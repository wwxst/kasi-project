package com.kasi.backend.promotion;

import com.kasi.backend.promotion.mapper.PromotionLinkMapper;
import com.kasi.backend.support.MySqlContractTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
        named = "MYSQL_CONTRACT_URL",
        matches = ".+",
        disabledReason = "SKIP: MYSQL_CONTRACT_URL is not configured")
@DisplayName("MySQL行锁契约")
class LockingMySqlContractIT extends MySqlContractTestSupport {

    @Autowired
    private PromotionLinkMapper linkMapper;

    @Test
    @DisplayName("FOR UPDATE持锁期间竞争更新会阻塞并在释放后提交")
    void findForUpdateBlocksCompetingUpdateUntilCommit() throws Exception {
        Long connectionId = insertConnection("row-lock");
        Long dramaId = insertDrama(connectionId, "row-lock");
        Long linkId = insertPromotionLink(connectionId, dramaId, "row-lock");
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch updateStarted = new CountDownLatch(1);
        CountDownLatch updateFinished = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Long> locker = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                Long lockedId = linkMapper.findByUserAndRequestKeyForUpdate(
                        primaryUserId(), CONTRACT_PREFIX + "row-lock", "TIKTOK", "LANDING").getId();
                lockAcquired.countDown();
                await(releaseLock, "Row lock was not released in time");
                return lockedId;
            }));

            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Integer> updater = executor.submit(() -> {
                updateStarted.countDown();
                try {
                    return new TransactionTemplate(transactionManager).execute(status ->
                            linkMapper.markFailed(linkId, "MYSQL_CONTRACT", "blocked update"));
                } finally {
                    updateFinished.countDown();
                }
            });

            try {
                assertThat(updateStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(updateFinished.await(500, TimeUnit.MILLISECONDS)).isFalse();
            } finally {
                releaseLock.countDown();
            }

            assertThat(locker.get(5, TimeUnit.SECONDS)).isEqualTo(linkId);
            assertThat(updater.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM promotion_link WHERE id = ?", String.class, linkId)).isEqualTo("FAILED");
    }

    private void await(CountDownLatch latch, String message) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, exception);
        }
    }
}

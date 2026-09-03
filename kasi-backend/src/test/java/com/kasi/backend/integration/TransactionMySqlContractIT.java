package com.kasi.backend.integration;

import com.kasi.backend.promotion.service.PromotionLinkPersistenceService;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCode;
import com.kasi.backend.scheduledtask.mapper.SystemScheduledTaskMapper;
import com.kasi.backend.support.MySqlContractTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
        named = "MYSQL_CONTRACT_URL",
        matches = ".+",
        disabledReason = "SKIP: MYSQL_CONTRACT_URL is not configured")
@DisplayName("MySQL事务与租约契约")
class TransactionMySqlContractIT extends MySqlContractTestSupport {

    @Autowired
    private PromotionLinkPersistenceService linkPersistenceService;

    @Autowired
    private SystemScheduledTaskMapper scheduledTaskMapper;

    @Test
    @DisplayName("外层回滚不撤销Spring代理提交的REQUIRES_NEW状态")
    void requiresNewCommitSurvivesOuterRollback() {
        Long userId = primaryUserId();
        Long connectionId = insertConnection("requires-new");
        Long dramaId = insertDrama(connectionId, "requires-new");
        Long linkId = insertPromotionLink(connectionId, dramaId, "requires-new");
        String originalNickname = jdbcTemplate.queryForObject(
                "SELECT nickname FROM promotion_user WHERE id = ?", String.class, userId);

        assertThat(AopUtils.isAopProxy(linkPersistenceService)).isTrue();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("UPDATE promotion_user SET nickname = ? WHERE id = ?",
                    CONTRACT_PREFIX + "rollback", userId);
            linkPersistenceService.markFailed(linkId, "MYSQL_CONTRACT", "requires new");
            status.setRollbackOnly();
        });

        assertThat(jdbcTemplate.queryForObject(
                "SELECT nickname FROM promotion_user WHERE id = ?", String.class, userId))
                .isEqualTo(originalNickname);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM promotion_link WHERE id = ?", String.class, linkId))
                .isEqualTo("FAILED");
    }

    @Test
    @DisplayName("两个事务竞争同一到期任务时只有一个租约领取成功")
    void concurrentLeaseClaimsHaveOneWinner() throws Exception {
        ScheduledTaskCode taskCode = ScheduledTaskCode.GOODSHORT_ORDER_SYNC;
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 10, 0);
        jdbcTemplate.update("""
                UPDATE system_scheduled_task
                SET enabled = 1, next_run_at = ?, lease_owner = NULL, lease_until = NULL
                WHERE task_code = ?
                """, now.minusSeconds(1), taskCode.name());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(
                    () -> claimAfterStart(taskCode, CONTRACT_PREFIX + "worker-a", now, ready, start));
            Future<Integer> second = executor.submit(
                    () -> claimAfterStart(taskCode, CONTRACT_PREFIX + "worker-b", now, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> results = List.of(
                    first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder(1, 0);
        }

        String owner = jdbcTemplate.queryForObject(
                "SELECT lease_owner FROM system_scheduled_task WHERE task_code = ?",
                String.class, taskCode.name());
        assertThat(owner).isIn(CONTRACT_PREFIX + "worker-a", CONTRACT_PREFIX + "worker-b");
    }

    private int claimAfterStart(ScheduledTaskCode taskCode,
                                String owner,
                                LocalDateTime now,
                                CountDownLatch ready,
                                CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent lease claim did not start in time");
        }
        Integer result = new TransactionTemplate(transactionManager).execute(status ->
                scheduledTaskMapper.claimLease(taskCode, owner, now, now.plusMinutes(2)));
        return result == null ? 0 : result;
    }
}

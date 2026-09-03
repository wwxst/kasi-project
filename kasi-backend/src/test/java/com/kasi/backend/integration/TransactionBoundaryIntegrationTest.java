package com.kasi.backend.integration;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.drama.service.DramaContentSyncService;
import com.kasi.backend.promotion.service.PromotionLinkPersistenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("生产Service事务边界")
class TransactionBoundaryIntegrationTest extends BaseAuthTest {

    @Autowired
    private PromotionLinkPersistenceService promotionLinkPersistenceService;

    @Autowired
    private DramaContentSyncService dramaContentSyncService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean(name = "dramaSyncTaskExecutor")
    private TaskExecutor dramaSyncTaskExecutor;

    @Test
    @DisplayName("外层事务回滚不回滚通过Spring代理独立提交的推广链接状态")
    void requiresNewStatusCommitSurvivesOuterRollback() {
        Long userId = primaryUserId();
        Long linkId = insertPromotionLink(userId);
        String originalNickname = jdbcTemplate.queryForObject(
                "SELECT nickname FROM promotion_user WHERE id = ?", String.class, userId);

        assertThat(AopUtils.isAopProxy(promotionLinkPersistenceService)).isTrue();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            jdbcTemplate.update("UPDATE promotion_user SET nickname = ? WHERE id = ?", "外层事务昵称", userId);

            promotionLinkPersistenceService.markFailed(linkId, "PROVIDER_REMOTE_UNAVAILABLE", "timeout");
            status.setRollbackOnly();
        });

        assertThat(jdbcTemplate.queryForObject(
                "SELECT nickname FROM promotion_user WHERE id = ?", String.class, userId))
                .isEqualTo(originalNickname);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM promotion_link WHERE id = ?", String.class, linkId))
                .isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_error_code FROM promotion_link WHERE id = ?", String.class, linkId))
                .isEqualTo("PROVIDER_REMOTE_UNAVAILABLE");
    }

    @Test
    @DisplayName("手动剧集同步在真实事务提交后才唤醒Worker")
    void manualContentSyncWakesWorkerOnlyAfterCommit() {
        Long dramaId = insertProviderDrama();
        clearInvocations(dramaSyncTaskExecutor);

        assertThat(AopUtils.isAopProxy(dramaContentSyncService)).isTrue();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            dramaContentSyncService.request(dramaId);
            verifyNoInteractions(dramaSyncTaskExecutor);
        });

        verify(dramaSyncTaskExecutor).execute(any(Runnable.class));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM provider_drama_content_sync_task WHERE drama_id = ?",
                String.class, dramaId)).isEqualTo("REQUESTED");
    }

    private Long insertPromotionLink(Long userId) {
        Long providerId = goodShortProviderId();
        Long connectionId = insertConnection(providerId);
        Long dramaId = insertDrama(connectionId, "transaction-link-drama");
        jdbcTemplate.update("""
                INSERT INTO promotion_link
                    (user_id, provider_id, connection_id, drama_id, batch_no, media_type,
                     link_variant, request_key, tracking_no, status)
                VALUES (?, ?, ?, ?, 'transaction-batch', 'TIKTOK', 'LANDING',
                        'transaction-request', 'transaction-tracking', 'PENDING')
                """, userId, providerId, connectionId, dramaId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_link WHERE tracking_no = 'transaction-tracking'", Long.class);
    }

    private Long insertProviderDrama() {
        Long connectionId = insertConnection(goodShortProviderId());
        return insertDrama(connectionId, "transaction-content-drama");
    }

    private Long insertConnection(Long providerId) {
        jdbcTemplate.update("""
                INSERT INTO short_drama_connection
                    (provider_id, connection_name, currency, filing_mode, status)
                VALUES (?, 'Transaction Test', 'USD', 'API', 1)
                """, providerId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id = ?", Long.class, providerId);
    }

    private Long insertDrama(Long connectionId, String externalDramaId) {
        jdbcTemplate.update("""
                INSERT INTO provider_drama
                    (connection_id, external_drama_id, title, language, remote_show_status, local_status)
                VALUES (?, ?, 'Transaction Test Drama', 'ENGLISH', '1', 'PUBLISHED')
                """, connectionId, externalDramaId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM provider_drama WHERE external_drama_id = ?", Long.class, externalDramaId);
    }

    private Long primaryUserId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_user WHERE user_no = ?", Long.class, PRIMARY_USER_NO);
    }

    private Long goodShortProviderId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
    }
}

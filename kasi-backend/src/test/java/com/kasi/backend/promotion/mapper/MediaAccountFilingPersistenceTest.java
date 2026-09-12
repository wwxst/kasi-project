package com.kasi.backend.promotion.mapper;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.promotion.entity.PromotionMediaAccount;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.dto.AdminMediaAccountPageQueryDTO;
import com.kasi.backend.promotion.enums.FilingAction;
import com.kasi.backend.promotion.enums.FilingMethod;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.MediaType;
import com.kasi.backend.promotion.service.MediaFilingTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("媒体账号与报备持久层")
class MediaAccountFilingPersistenceTest extends BaseAuthTest {

    @Autowired
    private PromotionMediaAccountMapper mediaAccountMapper;

    @Autowired
    private ProviderMediaFilingMapper filingMapper;

    @Autowired
    private MediaFilingTaskService filingTaskService;

    @Test
    @DisplayName("同一媒体平台账号只能绑定一次")
    void mediaIdentityIsGloballyUnique() {
        Long firstUserId = userId(PRIMARY_USER_NO);
        Long secondUserId = userId(MOBILE_USER_NO);
        PromotionMediaAccount first = mediaAccount(firstUserId, MediaType.TIKTOK, "creator-1");
        assertThat(mediaAccountMapper.insert(first)).isEqualTo(1);

        PromotionMediaAccount duplicate = mediaAccount(secondUserId, MediaType.TIKTOK, "creator-1");
        assertThatThrownBy(() -> mediaAccountMapper.insert(duplicate))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(mediaAccountMapper.findByIdentity(MediaType.TIKTOK, "creator-1").getUserId())
                .isEqualTo(firstUserId);
    }

    @Test
    @DisplayName("同一接入账号和媒体账号只保留一条报备")
    void filingIsUniquePerConnectionAndMediaAccount() {
        PromotionMediaAccount account = mediaAccount(userId(PRIMARY_USER_NO), MediaType.TIKTOK, "creator-2");
        mediaAccountMapper.insert(account);
        Long connectionId = insertConnection();

        ProviderMediaFiling filing = pendingFiling(connectionId, account.getId(), 1);
        assertThat(filingMapper.insert(filing)).isEqualTo(1);
        assertThatThrownBy(() -> filingMapper.insert(pendingFiling(connectionId, account.getId(), 1)))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(filingMapper.findByConnectionAndMedia(connectionId, account.getId()).getStatus())
                .isEqualTo(FilingStatus.NOT_SUBMITTED);
        assertThat(filingMapper.findByMediaAccountId(account.getId())).hasSize(1);
    }

    @Test
    @DisplayName("报备任务租约和资料版本条件可以保护写回")
    void filingTaskLeaseAndVersionGuard() {
        LocalDateTime now = LocalDateTime.now();
        PromotionMediaAccount account = mediaAccount(userId(PRIMARY_USER_NO), MediaType.TIKTOK, "creator-3");
        mediaAccountMapper.insert(account);
        Long connectionId = insertConnection();
        ProviderMediaFiling filing = pendingFiling(connectionId, account.getId(), 1);
        filing.setNextActionAt(now.minusSeconds(1));
        filingMapper.insert(filing);

        assertThat(filingMapper.findDueIds(now, 50)).containsExactly(filing.getId());
        assertThat(filingMapper.claimLease(filing.getId(), "worker-a", FilingMethod.API,
                FilingAction.SUBMIT, 2, now, now.plusMinutes(2))).isZero();
        assertThat(filingMapper.claimLease(filing.getId(), "worker-a", FilingMethod.API,
                FilingAction.SUBMIT, 1, now, now.plusMinutes(2))).isEqualTo(1);
        assertThat(filingMapper.markSubmitAttempt(filing.getId(), "worker-b", FilingMethod.API,
                FilingAction.SUBMIT, 1, now)).isZero();
        assertThat(filingMapper.markSubmitAttempt(filing.getId(), "worker-a", FilingMethod.API,
                FilingAction.SUBMIT, 1, now)).isEqualTo(1);
        assertThat(filingMapper.completeSubmit(filing.getId(), "worker-b", FilingMethod.API,
                FilingAction.SUBMIT, 1, now, now.plusMinutes(1))).isZero();
        assertThat(filingMapper.completeSubmit(filing.getId(), "worker-a", FilingMethod.MANUAL,
                FilingAction.SUBMIT, 1, now, now.plusMinutes(1))).isZero();
        assertThat(filingMapper.completeSubmit(filing.getId(), "worker-a", FilingMethod.API,
                FilingAction.QUERY, 1, now, now.plusMinutes(1))).isZero();
        assertThat(filingMapper.completeSubmit(filing.getId(), "worker-a", FilingMethod.API,
                FilingAction.SUBMIT, 2, now, now.plusMinutes(1))).isZero();
        assertThat(filingMapper.completeSubmit(filing.getId(), "worker-a", FilingMethod.API,
                FilingAction.SUBMIT, 1, now, now.plusMinutes(1))).isEqualTo(1);

        assertThat(filingMapper.claimLease(filing.getId(), "worker-query-early", FilingMethod.API,
                FilingAction.QUERY, 1, now, now.plusMinutes(2))).isZero();
        LocalDateTime firstQueryAt = now.plusMinutes(1).plusSeconds(1);
        assertThat(filingMapper.claimLease(filing.getId(), "worker-query", FilingMethod.API,
                FilingAction.QUERY, 1, firstQueryAt, firstQueryAt.plusMinutes(2))).isEqualTo(1);
        assertThat(filingMapper.completeQuery(filing.getId(), "worker-query", FilingMethod.API,
                FilingAction.SUBMIT, 1, FilingStatus.PENDING, "0", null, null, null,
                firstQueryAt, FilingAction.QUERY, now.plusMinutes(5))).isZero();
        assertThat(filingMapper.completeQuery(filing.getId(), "worker-query", FilingMethod.API,
                FilingAction.QUERY, 2, FilingStatus.PENDING, "0", null, null, null,
                firstQueryAt, FilingAction.QUERY, now.plusMinutes(5))).isZero();
        assertThat(filingMapper.completeQuery(filing.getId(), "worker-query", FilingMethod.API,
                FilingAction.QUERY, 1, FilingStatus.PENDING, "0", null, null, null,
                firstQueryAt, FilingAction.QUERY, now.plusMinutes(5))).isEqualTo(1);

        assertThat(filingMapper.claimLease(filing.getId(), "worker-retry-early", FilingMethod.API,
                FilingAction.QUERY, 1, firstQueryAt, firstQueryAt.plusMinutes(2))).isZero();
        LocalDateTime retryQueryAt = now.plusMinutes(5).plusSeconds(1);
        assertThat(filingMapper.claimLease(filing.getId(), "worker-retry", FilingMethod.API,
                FilingAction.QUERY, 1, retryQueryAt, retryQueryAt.plusMinutes(2))).isEqualTo(1);
        assertThat(filingMapper.recordRetry(filing.getId(), "worker-old", FilingMethod.API,
                FilingAction.QUERY, 1, FilingStatus.PENDING, FilingAction.NONE, null,
                1, "TASK_ERROR", "failed")).isZero();
        assertThat(filingMapper.recordRetry(filing.getId(), "worker-retry", FilingMethod.MANUAL,
                FilingAction.QUERY, 1, FilingStatus.PENDING, FilingAction.NONE, null,
                1, "TASK_ERROR", "failed")).isZero();
        assertThat(filingMapper.recordRetry(filing.getId(), "worker-retry", FilingMethod.API,
                FilingAction.SUBMIT, 1, FilingStatus.PENDING, FilingAction.NONE, null,
                1, "TASK_ERROR", "failed")).isZero();
        assertThat(filingMapper.recordRetry(filing.getId(), "worker-retry", FilingMethod.API,
                FilingAction.QUERY, 2, FilingStatus.PENDING, FilingAction.NONE, null,
                1, "TASK_ERROR", "failed")).isZero();
        assertThat(filingMapper.recordRetry(filing.getId(), "worker-retry", FilingMethod.API,
                FilingAction.QUERY, 1, FilingStatus.PENDING, FilingAction.NONE, null,
                1, "TASK_ERROR", "failed")).isEqualTo(1);
        assertThat(filingMapper.switchMethodAndSchedule(filing.getId(), FilingMethod.API,
                FilingStatus.NOT_SUBMITTED, FilingAction.SUBMIT, now.plusMinutes(1), 2, now)).isZero();
        assertThat(filingMapper.switchMethodAndSchedule(filing.getId(), FilingMethod.API,
                FilingStatus.NOT_SUBMITTED, FilingAction.SUBMIT, now.plusMinutes(1), 1, now)).isEqualTo(1);
    }

    @Test
    @DisplayName("到期扫描包含 API SUBMIT 和 QUERY 但排除 MANUAL")
    void dueScanIncludesOnlyApiSubmitAndQuery() {
        PromotionMediaAccount account = mediaAccount(userId(PRIMARY_USER_NO), MediaType.TIKTOK, "creator-due");
        mediaAccountMapper.insert(account);
        Long connectionId = insertConnection();
        ProviderMediaFiling submit = pendingFiling(connectionId, account.getId(), 1);
        filingMapper.insert(submit);

        PromotionMediaAccount queryAccount = mediaAccount(
                userId(PRIMARY_USER_NO), MediaType.TIKTOK, "creator-due-query");
        mediaAccountMapper.insert(queryAccount);
        ProviderMediaFiling query = pendingFiling(connectionId, queryAccount.getId(), 1);
        query.setStatus(FilingStatus.PENDING);
        query.setNextAction(FilingAction.QUERY);
        filingMapper.insert(query);

        jdbcTemplate.update("UPDATE provider_media_filing SET filing_method = 'MANUAL' WHERE id = ?", query.getId());
        assertThat(filingMapper.findDueIds(LocalDateTime.now().plusSeconds(1), 50))
                .containsExactly(submit.getId());
        jdbcTemplate.update("UPDATE provider_media_filing SET filing_method = 'API' WHERE id = ?", query.getId());
        assertThat(filingMapper.findDueIds(LocalDateTime.now().plusSeconds(1), 50))
                .containsExactly(submit.getId(), query.getId());
    }

    @Test
    @DisplayName("默认实例标识生成的租约 token 可以写入任务表")
    void defaultInstanceLeaseTokenFitsTaskTable() {
        PromotionMediaAccount account = mediaAccount(
                userId(PRIMARY_USER_NO), MediaType.TIKTOK, "creator-default-lease");
        account.setStatus(0);
        mediaAccountMapper.insert(account);
        ProviderMediaFiling filing = pendingFiling(insertConnection(), account.getId(), 1);
        filing.setNextActionAt(LocalDateTime.now().minusSeconds(1));
        filingMapper.insert(filing);

        assertThatCode(() -> filingTaskService.submitNow(filing.getId()))
                .doesNotThrowAnyException();

        ProviderMediaFiling stored = filingMapper.findById(filing.getId());
        assertThat(stored.getLastErrorCode()).isEqualTo("LOCAL_INVALID");
        assertThat(stored.getNextAction()).isEqualTo(FilingAction.NONE);
    }

    @Test
    @DisplayName("普通重试只接受已确认未收到并单调递增任务版本")
    void retryRequiresConfirmedNotReceivedAndIncrementsVersion() {
        PromotionMediaAccount account = mediaAccount(userId(PRIMARY_USER_NO), MediaType.TIKTOK, "creator-retry");
        mediaAccountMapper.insert(account);
        ProviderMediaFiling filing = pendingFiling(insertConnection(), account.getId(), 3);
        filing.setStatus(FilingStatus.SUBMIT_FAILED);
        filing.setNextAction(FilingAction.NONE);
        filing.setNextActionAt(null);
        filing.setLastSubmitAttemptAt(LocalDateTime.now().minusMinutes(1));
        filing.setLastErrorCode("SUBMIT_OUTCOME_UNKNOWN");
        filingMapper.insert(filing);

        LocalDateTime now = LocalDateTime.now();
        assertThat(filingMapper.retrySubmission(filing.getId(), 3, now)).isZero();
        jdbcTemplate.update("UPDATE provider_media_filing SET last_error_code = ? WHERE id = ?",
                "SUBMIT_CONFIRMED_NOT_RECEIVED", filing.getId());
        assertThat(filingMapper.retrySubmission(filing.getId(), 3, now)).isEqualTo(1);

        ProviderMediaFiling stored = filingMapper.findById(filing.getId());
        assertThat(stored.getStatus()).isEqualTo(FilingStatus.NOT_SUBMITTED);
        assertThat(stored.getNextAction()).isEqualTo(FilingAction.SUBMIT);
        assertThat(stored.getTaskDataVersion()).isEqualTo(4);
        assertThat(stored.getLastSubmitAttemptAt()).isNull();
        assertThat(stored.getLastErrorCode()).isNull();
    }

    @Test
    @DisplayName("报白方式切换递增任务版本并保留真实提交和远端证据")
    void switchMethodPreservesSubmissionAndRemoteEvidence() {
        PromotionMediaAccount account = mediaAccount(userId(PRIMARY_USER_NO), MediaType.TIKTOK, "creator-old");
        mediaAccountMapper.insert(account);
        ProviderMediaFiling filing = pendingFiling(insertConnection(), account.getId(), 4);
        filing.setFilingMethod(FilingMethod.API);
        filing.setSubmittedDataVersion(4);
        filing.setRemoteStatus("1");
        filing.setExternalFilingId("old-filing");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime attemptAt = now.minusMinutes(5);
        filing.setFilingTime(now.minusMinutes(4));
        filing.setOperateTime(now.minusMinutes(3));
        filing.setLastSubmitAttemptAt(attemptAt);
        filing.setLastSubmittedAt(now.minusMinutes(4));
        filing.setLastQueriedAt(now.minusMinutes(2));
        filing.setManualUpdatedBy(2L);
        filing.setManualUpdatedAt(now.minusMinutes(1));
        filingMapper.insert(filing);

        assertThat(filingMapper.switchMethodAndSchedule(
                filing.getId(), FilingMethod.MANUAL, FilingStatus.APPROVED,
                FilingAction.NONE, null, 3, now)).isZero();
        assertThat(filingMapper.switchMethodAndSchedule(
                filing.getId(), FilingMethod.MANUAL, FilingStatus.APPROVED,
                FilingAction.NONE, null, 4, now)).isEqualTo(1);

        ProviderMediaFiling stored = filingMapper.findById(filing.getId());
        assertThat(stored.getFilingMethod()).isEqualTo(FilingMethod.MANUAL);
        assertThat(stored.getStatus()).isEqualTo(FilingStatus.APPROVED);
        assertThat(stored.getTaskDataVersion()).isEqualTo(5);
        assertThat(stored.getSubmittedDataVersion()).isEqualTo(4);
        assertThat(stored.getRemoteStatus()).isEqualTo("1");
        assertThat(stored.getExternalFilingId()).isEqualTo("old-filing");
        assertThat(stored.getFilingTime()).isEqualTo(now.minusMinutes(4));
        assertThat(stored.getOperateTime()).isEqualTo(now.minusMinutes(3));
        assertThat(stored.getLastSubmitAttemptAt()).isEqualTo(attemptAt);
        assertThat(stored.getLastSubmittedAt()).isEqualTo(now.minusMinutes(4));
        assertThat(stored.getLastQueriedAt()).isEqualTo(now.minusMinutes(2));
        assertThat(stored.getManualUpdatedBy()).isEqualTo(2L);
        assertThat(stored.getManualUpdatedAt()).isEqualTo(now.minusMinutes(1));
    }

    @Test
    @DisplayName("人工状态条件更新只接受一次旧状态和旧版本")
    void manualStatusUpdateRejectsStaleStateAndVersion() {
        PromotionMediaAccount account = mediaAccount(userId(PRIMARY_USER_NO), MediaType.TIKTOK, "manual-cas");
        mediaAccountMapper.insert(account);
        ProviderMediaFiling filing = pendingFiling(insertConnection(), account.getId(), 1);
        filing.setFilingMethod(FilingMethod.MANUAL);
        filing.setStatus(FilingStatus.PENDING);
        filing.setNextAction(FilingAction.NONE);
        filing.setNextActionAt(null);
        filingMapper.insert(filing);
        LocalDateTime now = LocalDateTime.now().withNano(0);

        assertThat(filingMapper.updateManualStatus(filing.getId(), FilingStatus.PENDING,
                FilingStatus.APPROVED, 1, 2L, now, now)).isEqualTo(1);
        assertThat(filingMapper.updateManualStatus(filing.getId(), FilingStatus.PENDING,
                FilingStatus.REJECTED, 1, 2L, now, now)).isZero();

        ProviderMediaFiling stored = filingMapper.findById(filing.getId());
        assertThat(stored.getStatus()).isEqualTo(FilingStatus.APPROVED);
        assertThat(stored.getTaskDataVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("报备五态筛选在数据库查询和分页前生效")
    void adminFilingDisplayStatusFiltersBeforePagination() {
        PromotionMediaAccount failed = mediaAccount(userId(PRIMARY_USER_NO), MediaType.TIKTOK, "creator-filter-failed");
        mediaAccountMapper.insert(failed);
        PromotionMediaAccount pending = mediaAccount(userId(PRIMARY_USER_NO), MediaType.TIKTOK, "creator-filter-pending");
        mediaAccountMapper.insert(pending);
        Long connectionId = insertConnection();
        ProviderMediaFiling filing = pendingFiling(connectionId, failed.getId(), 1);
        filing.setStatus(FilingStatus.SUBMIT_FAILED);
        filing.setNextAction(FilingAction.NONE);
        filing.setNextActionAt(null);
        filingMapper.insert(filing);
        assertThat(jdbcTemplate.update(
                "UPDATE provider_media_filing SET last_error_message = ? WHERE id = ?",
                "report failed", filing.getId())).isEqualTo(1);

        AdminMediaAccountPageQueryDTO query = new AdminMediaAccountPageQueryDTO();
        query.setFilingStatus("SUBMIT_FAILED");
        query.setPage(1);
        query.setSize(1);

        assertThat(mediaAccountMapper.countAdminPage(query)).isEqualTo(1);
        assertThat(mediaAccountMapper.findAdminPage(query, 0, 1))
                .extracting(PromotionMediaAccount::getExternalAccountId)
                .containsExactly("creator-filter-failed");
    }

    @Test
    @DisplayName("删除后旧任务不能污染相同外部账号的新报白记录")
    void staleTaskCannotUpdateRecreatedAccountFiling() {
        LocalDateTime now = LocalDateTime.now();
        Long connectionId = insertConnection();
        PromotionMediaAccount oldAccount = mediaAccount(
                userId(PRIMARY_USER_NO), MediaType.TIKTOK, "creator-recreated");
        mediaAccountMapper.insert(oldAccount);
        ProviderMediaFiling oldFiling = pendingFiling(connectionId, oldAccount.getId(), 1);
        oldFiling.setNextActionAt(now.minusSeconds(1));
        filingMapper.insert(oldFiling);
        assertThat(filingMapper.claimLease(oldFiling.getId(), "worker:old", FilingMethod.API,
                FilingAction.SUBMIT, 1, now, now.plusMinutes(2))).isEqualTo(1);

        assertThat(filingMapper.deleteByMediaAccountId(oldAccount.getId())).isEqualTo(1);
        assertThat(mediaAccountMapper.deleteById(oldAccount.getId())).isEqualTo(1);
        PromotionMediaAccount newAccount = mediaAccount(
                userId(PRIMARY_USER_NO), MediaType.TIKTOK, "creator-recreated");
        mediaAccountMapper.insert(newAccount);
        ProviderMediaFiling newFiling = pendingFiling(connectionId, newAccount.getId(), 1);
        filingMapper.insert(newFiling);

        assertThat(newFiling.getId()).isNotEqualTo(oldFiling.getId());
        assertThat(filingMapper.completeSubmit(oldFiling.getId(), "worker:old", FilingMethod.API,
                FilingAction.SUBMIT, 1, now, now.plusMinutes(1))).isZero();
        ProviderMediaFiling stored = filingMapper.findById(newFiling.getId());
        assertThat(stored.getStatus()).isEqualTo(FilingStatus.NOT_SUBMITTED);
        assertThat(stored.getSubmittedDataVersion()).isNull();
        assertThat(stored.getNextAction()).isEqualTo(FilingAction.SUBMIT);
    }

    private Long insertConnection() {
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, base_url, partner_id, api_key_ciphertext, currency) "
                        + "VALUES (?, 'GoodShort', 'https://api.test/creek', 'partner-1', 'v1:cipher', 'USD')",
                providerId);
        return jdbcTemplate.queryForObject("SELECT id FROM short_drama_connection WHERE provider_id = ?",
                Long.class, providerId);
    }

    private Long userId(String userNo) {
        return jdbcTemplate.queryForObject("SELECT id FROM promotion_user WHERE user_no = ?", Long.class, userNo);
    }

    private PromotionMediaAccount mediaAccount(Long userId, MediaType type, String id) {
        PromotionMediaAccount account = new PromotionMediaAccount();
        account.setUserId(userId);
        account.setMediaType(type);
        account.setExternalAccountId(id);
        account.setAccountName("Creator");
        account.setAccountLink("https://www.tiktok.com/@" + id);
        account.setStatus(1);
        account.setDataVersion(1);
        return account;
    }

    private ProviderMediaFiling pendingFiling(Long connectionId, Long mediaAccountId, int version) {
        ProviderMediaFiling filing = new ProviderMediaFiling();
        filing.setConnectionId(connectionId);
        filing.setMediaAccountId(mediaAccountId);
        filing.setFilingMethod(FilingMethod.API);
        filing.setStatus(FilingStatus.NOT_SUBMITTED);
        filing.setNextAction(FilingAction.SUBMIT);
        filing.setNextActionAt(LocalDateTime.now());
        filing.setTaskDataVersion(version);
        return filing;
    }
}

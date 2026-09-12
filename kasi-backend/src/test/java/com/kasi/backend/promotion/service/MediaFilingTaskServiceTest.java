package com.kasi.backend.promotion.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.config.MediaFilingProperties;
import com.kasi.backend.promotion.entity.PromotionMediaAccount;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.enums.FilingAction;
import com.kasi.backend.promotion.enums.FilingMethod;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.MediaType;
import com.kasi.backend.promotion.mapper.PromotionMediaAccountMapper;
import com.kasi.backend.promotion.mapper.ProviderMediaFilingMapper;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.exception.ProviderRemoteRejectedException;
import com.kasi.backend.provider.exception.ProviderTransientException;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.AccountFilingProviderAdapter;
import com.kasi.backend.provider.spi.AccountFilingResult;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@DisplayName("媒体账号报白后台任务")
class MediaFilingTaskServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 8, 0);

    private ProviderMediaFilingMapper filingMapper;
    private PromotionMediaAccountMapper mediaMapper;
    private com.kasi.backend.provider.mapper.ShortDramaConnectionMapper connectionMapper;
    private ProviderRuntimeConnectionService runtimeService;
    private AccountFilingProviderAdapter adapter;
    private MediaFilingTaskService service;

    @BeforeEach
    void setUp() {
        filingMapper = mock(ProviderMediaFilingMapper.class);
        mediaMapper = mock(PromotionMediaAccountMapper.class);
        connectionMapper = mock(com.kasi.backend.provider.mapper.ShortDramaConnectionMapper.class);
        runtimeService = mock(ProviderRuntimeConnectionService.class);
        adapter = mock(AccountFilingProviderAdapter.class);
        MediaFilingProperties properties = new MediaFilingProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new com.kasi.backend.promotion.service.impl.MediaFilingTaskServiceImpl(
                filingMapper, mediaMapper, connectionMapper, runtimeService, properties, clock,
                "filing-worker-test");
    }

    @Test
    @DisplayName("到期 QUERY 任务继续查询待审核状态")
    void dueQueryKeepsPendingFilingScheduled() {
        ProviderMediaFiling filing = filing(1L, FilingMethod.API, FilingAction.QUERY);
        stubDueTask(filing, ProviderCapability.FILING_STATUS_QUERY);
        when(adapter.queryAccountFiling(any(), any())).thenReturn(
                new AccountFilingResult(FilingStatus.PENDING, "0", null, null, null));

        service.processDueBatch();

        verify(adapter).queryAccountFiling(any(), any());
        verify(filingMapper).completeQuery(eq(1L), any(), eq(FilingMethod.API), eq(FilingAction.QUERY),
                eq(1), eq(FilingStatus.PENDING), eq("0"), isNull(), isNull(), isNull(), eq(NOW),
                eq(FilingAction.QUERY), eq(NOW.plusMinutes(5)));
        verify(adapter, never()).submitAccountFiling(any(), any());
    }

    @Test
    @DisplayName("到期 QUERY 返回已加白后停止继续查询")
    void approvedQueryStopsFurtherQueries() {
        ProviderMediaFiling filing = filing(1L, FilingMethod.API, FilingAction.QUERY);
        stubDueTask(filing, ProviderCapability.FILING_STATUS_QUERY);
        when(adapter.queryAccountFiling(any(), any())).thenReturn(
                new AccountFilingResult(FilingStatus.APPROVED, "1", null, null, NOW));

        service.processDueBatch();

        verify(filingMapper).completeQuery(eq(1L), any(), eq(FilingMethod.API), eq(FilingAction.QUERY),
                eq(1), eq(FilingStatus.APPROVED), eq("1"), isNull(), isNull(), eq(NOW), eq(NOW),
                eq(FilingAction.NONE), isNull());
    }

    @Test
    @DisplayName("到期 SUBMIT 在 HTTP 前记录尝试并在成功后转查询")
    void dueSubmitRecordsAttemptBeforeHttpAndSchedulesQuery() {
        ProviderMediaFiling filing = filing(1L, FilingMethod.API, FilingAction.SUBMIT);
        stubDueTask(filing, ProviderCapability.ACCOUNT_FILING);
        when(filingMapper.markSubmitAttempt(eq(1L), any(), eq(FilingMethod.API),
                eq(FilingAction.SUBMIT), eq(1), eq(NOW))).thenReturn(1);

        service.processDueBatch();

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(filingMapper).claimLease(eq(1L), token.capture(), eq(FilingMethod.API),
                eq(FilingAction.SUBMIT), eq(1), eq(NOW), eq(NOW.plusMinutes(2)));
        InOrder order = inOrder(filingMapper, adapter);
        order.verify(filingMapper).markSubmitAttempt(1L, token.getValue(), FilingMethod.API,
                FilingAction.SUBMIT, 1, NOW);
        order.verify(adapter).submitAccountFiling(any(), any());
        order.verify(filingMapper).completeSubmit(1L, token.getValue(), FilingMethod.API,
                FilingAction.SUBMIT, 1, NOW, NOW.plusMinutes(1));
    }

    @Test
    @DisplayName("每次领取生成 instanceId 加 UUID 的唯一租约 token")
    void eachClaimUsesUniqueLeaseToken() {
        ProviderMediaFiling first = filing(1L, FilingMethod.API, FilingAction.QUERY);
        ProviderMediaFiling second = filing(2L, FilingMethod.API, FilingAction.QUERY);
        when(filingMapper.findDueIds(NOW, 50)).thenReturn(List.of(1L, 2L));
        when(filingMapper.findById(anyLong())).thenAnswer(invocation ->
                invocation.<Long>getArgument(0).equals(1L) ? first : second);
        when(filingMapper.claimLease(anyLong(), any(), eq(FilingMethod.API), eq(FilingAction.QUERY),
                eq(1), eq(NOW), eq(NOW.plusMinutes(2)))).thenReturn(1);
        stubLocalData();
        stubRuntime(ProviderCapability.FILING_STATUS_QUERY);
        when(adapter.queryAccountFiling(any(), any())).thenReturn(
                new AccountFilingResult(FilingStatus.PENDING, "0", null, null, null));

        service.processDueBatch();

        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        verify(filingMapper, times(2)).claimLease(anyLong(), tokens.capture(), eq(FilingMethod.API),
                eq(FilingAction.QUERY), eq(1), eq(NOW), eq(NOW.plusMinutes(2)));
        assertThat(tokens.getAllValues()).hasSize(2).doesNotHaveDuplicates()
                .allMatch(value -> value.matches("filing-worker-test:[0-9a-f-]{36}"));
    }

    @Test
    @DisplayName("立即提交未领取时仍由后续 Worker 接续")
    void workerContinuesWhenImmediateSubmitCannotClaim() {
        ProviderMediaFiling filing = filing(1L, FilingMethod.API, FilingAction.SUBMIT);
        when(filingMapper.findById(1L)).thenReturn(filing);
        when(filingMapper.claimLease(eq(1L), any(), eq(FilingMethod.API), eq(FilingAction.SUBMIT),
                eq(1), eq(NOW), eq(NOW.plusMinutes(2)))).thenReturn(0, 1);
        when(filingMapper.findDueIds(NOW, 50)).thenReturn(List.of(1L));
        stubLocalData();
        stubRuntime(ProviderCapability.ACCOUNT_FILING);
        when(filingMapper.markSubmitAttempt(eq(1L), any(), eq(FilingMethod.API),
                eq(FilingAction.SUBMIT), eq(1), eq(NOW))).thenReturn(1);

        service.submitNow(1L);
        service.processDueBatch();

        verify(adapter).submitAccountFiling(any(), any());
    }

    @Test
    @DisplayName("MANUAL SUBMIT 不领取且不调用平台")
    void manualSubmitNeverCallsProvider() {
        ProviderMediaFiling filing = filing(1L, FilingMethod.MANUAL, FilingAction.SUBMIT);
        when(filingMapper.findById(1L)).thenReturn(filing);

        service.submitNow(1L);

        verify(filingMapper, never()).claimLease(anyLong(), any(), any(), any(), anyInt(), any(), any());
        verify(adapter, never()).submitAccountFiling(any(), any());
        verify(adapter, never()).queryAccountFiling(any(), any());
    }

    @Test
    @DisplayName("第4次 QUERY 临时错误继续排队")
    void fourthTransientQueryFailureRetries() {
        ProviderMediaFiling filing = filing(1L, FilingMethod.API, FilingAction.QUERY);
        filing.setRetryCount(3);
        stubDueTask(filing, ProviderCapability.FILING_STATUS_QUERY);
        when(adapter.queryAccountFiling(any(), any())).thenThrow(new ProviderTransientException("temporary"));

        service.processDueBatch();

        verify(filingMapper).recordRetry(eq(1L), any(), eq(FilingMethod.API), eq(FilingAction.QUERY),
                eq(1), eq(FilingStatus.PENDING), eq(FilingAction.QUERY), eq(NOW.plusMinutes(30)), eq(4),
                eq("REMOTE_TRANSIENT"), eq("temporary"));
    }

    @Test
    @DisplayName("第5次 QUERY 临时错误停止")
    void fifthTransientQueryFailureStops() {
        ProviderMediaFiling filing = filing(1L, FilingMethod.API, FilingAction.QUERY);
        filing.setRetryCount(4);
        stubDueTask(filing, ProviderCapability.FILING_STATUS_QUERY);
        when(adapter.queryAccountFiling(any(), any())).thenThrow(new ProviderTransientException("temporary"));

        service.processDueBatch();

        verify(filingMapper).recordRetry(eq(1L), any(), eq(FilingMethod.API), eq(FilingAction.QUERY),
                eq(1), eq(FilingStatus.PENDING), eq(FilingAction.NONE), isNull(), eq(5),
                eq("REMOTE_TRANSIENT"), eq("temporary"));
    }

    @Test
    @DisplayName("QUERY 被平台拒绝时停止")
    void rejectedQueryFailureStopsImmediately() {
        ProviderMediaFiling filing = filing(1L, FilingMethod.API, FilingAction.QUERY);
        stubDueTask(filing, ProviderCapability.FILING_STATUS_QUERY);
        when(adapter.queryAccountFiling(any(), any())).thenThrow(new ProviderRemoteRejectedException("rejected"));

        service.processDueBatch();

        verify(filingMapper).recordRetry(eq(1L), any(), eq(FilingMethod.API), eq(FilingAction.QUERY),
                eq(1), eq(FilingStatus.PENDING), eq(FilingAction.NONE), isNull(), eq(1),
                eq("REMOTE_REJECTED"), eq("rejected"));
    }

    @Test
    @DisplayName("QUERY 不可恢复错误停止后继续抛出")
    void unexpectedQueryFailureStopsImmediately() {
        ProviderMediaFiling filing = filing(1L, FilingMethod.API, FilingAction.QUERY);
        stubDueTask(filing, ProviderCapability.FILING_STATUS_QUERY);
        when(adapter.queryAccountFiling(any(), any())).thenThrow(new IllegalStateException("broken"));

        assertThatThrownBy(service::processDueBatch).isInstanceOf(IllegalStateException.class);

        verify(filingMapper).recordRetry(eq(1L), any(), eq(FilingMethod.API), eq(FilingAction.QUERY),
                eq(1), eq(FilingStatus.PENDING), eq(FilingAction.NONE), isNull(), eq(1),
                eq("TASK_ERROR"), eq("broken"));
    }

    @Test
    @DisplayName("SUBMIT 被平台明确拒绝时记录提交失败")
    void rejectedSubmissionIsSubmitFailed() {
        ProviderMediaFiling filing = filing(1L, FilingMethod.API, FilingAction.SUBMIT);
        when(filingMapper.findById(1L)).thenReturn(filing);
        when(filingMapper.claimLease(eq(1L), any(), eq(FilingMethod.API), eq(FilingAction.SUBMIT),
                eq(1), eq(NOW), eq(NOW.plusMinutes(2)))).thenReturn(1);
        stubLocalData();
        stubRuntime(ProviderCapability.ACCOUNT_FILING);
        when(filingMapper.markSubmitAttempt(eq(1L), any(), eq(FilingMethod.API),
                eq(FilingAction.SUBMIT), eq(1), eq(NOW))).thenReturn(1);
        doThrow(new ProviderRemoteRejectedException("rejected"))
                .when(adapter).submitAccountFiling(any(), any());

        service.submitNow(1L);

        verify(filingMapper).recordRetry(eq(1L), any(), eq(FilingMethod.API), eq(FilingAction.SUBMIT),
                eq(1), eq(FilingStatus.SUBMIT_FAILED), eq(FilingAction.NONE), isNull(), eq(1),
                eq("REMOTE_REJECTED"), eq("rejected"));
    }

    @Test
    @DisplayName("SUBMIT 在平台运行配置解析失败时记录本地失败")
    void runtimeResolutionFailureIsLocalSubmitFailure() {
        ProviderMediaFiling filing = filing(1L, FilingMethod.API, FilingAction.SUBMIT);
        stubDueTask(filing, ProviderCapability.ACCOUNT_FILING);
        when(runtimeService.resolve(4L, ProviderCapability.ACCOUNT_FILING))
                .thenThrow(new BusinessException(
                        ErrorCode.PROVIDER_CONNECTION_INVALID, "平台接入配置无效"));

        service.processDueBatch();

        verify(filingMapper, never()).markSubmitAttempt(anyLong(), any(), any(), any(), anyInt(), any());
        verify(adapter, never()).submitAccountFiling(any(), any());
        verify(filingMapper, never()).markSubmissionUnknown(anyLong(), any(), any(), any(), anyInt(), any(), any());
        verify(filingMapper).recordRetry(eq(1L), any(), eq(FilingMethod.API), eq(FilingAction.SUBMIT),
                eq(1), eq(FilingStatus.SUBMIT_FAILED), eq(FilingAction.NONE), isNull(), eq(1),
                eq("LOCAL_INVALID"), eq("平台接入配置无效"));
    }

    @Test
    @DisplayName("SUBMIT 超时收敛为结果不确定并停止自动提交")
    void transientSubmissionBecomesOutcomeUnknown() {
        ProviderMediaFiling filing = filing(1L, FilingMethod.API, FilingAction.SUBMIT);
        stubDueTask(filing, ProviderCapability.ACCOUNT_FILING);
        when(filingMapper.markSubmitAttempt(eq(1L), any(), eq(FilingMethod.API),
                eq(FilingAction.SUBMIT), eq(1), eq(NOW))).thenReturn(1);
        doThrow(new ProviderTransientException("timeout"))
                .when(adapter).submitAccountFiling(any(), any());

        service.processDueBatch();

        verify(filingMapper).markSubmissionUnknown(eq(1L), any(), eq(FilingMethod.API),
                eq(FilingAction.SUBMIT), eq(1), eq("SUBMIT_OUTCOME_UNKNOWN"), eq("timeout"));
    }

    @Test
    @DisplayName("租约过期的已尝试 SUBMIT 不重复调用平台")
    void expiredAttemptIsMarkedUnknownWithoutSubmittingAgain() {
        ProviderMediaFiling filing = filing(1L, FilingMethod.API, FilingAction.SUBMIT);
        filing.setLastSubmitAttemptAt(NOW.minusMinutes(3));
        stubDueTask(filing, ProviderCapability.ACCOUNT_FILING);

        service.processDueBatch();

        verify(filingMapper).markSubmissionUnknown(eq(1L), any(), eq(FilingMethod.API),
                eq(FilingAction.SUBMIT), eq(1), eq("SUBMIT_OUTCOME_UNKNOWN"), any());
        verify(adapter, never()).submitAccountFiling(any(), any());
    }

    @Test
    @DisplayName("SUBMIT 未分类运行异常也保存结果不确定")
    void unexpectedSubmissionBecomesOutcomeUnknown() {
        ProviderMediaFiling filing = filing(1L, FilingMethod.API, FilingAction.SUBMIT);
        stubDueTask(filing, ProviderCapability.ACCOUNT_FILING);
        when(filingMapper.markSubmitAttempt(eq(1L), any(), eq(FilingMethod.API),
                eq(FilingAction.SUBMIT), eq(1), eq(NOW))).thenReturn(1);
        doThrow(new IllegalStateException("connection reset"))
                .when(adapter).submitAccountFiling(any(), any());

        assertThatThrownBy(service::processDueBatch).isInstanceOf(IllegalStateException.class);

        verify(filingMapper).markSubmissionUnknown(eq(1L), any(), eq(FilingMethod.API),
                eq(FilingAction.SUBMIT), eq(1), eq("SUBMIT_OUTCOME_UNKNOWN"), eq("connection reset"));
    }

    private void stubDueTask(ProviderMediaFiling filing, ProviderCapability capability) {
        when(filingMapper.findDueIds(NOW, 50)).thenReturn(List.of(filing.getId()));
        when(filingMapper.findById(filing.getId())).thenReturn(filing);
        when(filingMapper.claimLease(eq(filing.getId()), any(), eq(FilingMethod.API),
                eq(filing.getNextAction()), eq(filing.getTaskDataVersion()), eq(NOW), eq(NOW.plusMinutes(2))))
                .thenReturn(1);
        stubLocalData();
        stubRuntime(capability);
    }

    private void stubLocalData() {
        when(mediaMapper.findById(2L)).thenReturn(account());
        ShortDramaConnection connection = new ShortDramaConnection();
        connection.setId(3L);
        connection.setProviderId(4L);
        when(connectionMapper.findById(3L)).thenReturn(connection);
    }

    private void stubRuntime(ProviderCapability capability) {
        when(runtimeService.resolve(4L, capability)).thenReturn(new ProviderRuntimeConnection(
                3L, 4L, "GOODSHORT", "GoodShort",
                new ProviderConnectionSecret("https://test", "pid", "key", "USD"), adapter));
    }

    private ProviderMediaFiling filing(Long id, FilingMethod method, FilingAction action) {
        ProviderMediaFiling filing = new ProviderMediaFiling();
        filing.setId(id);
        filing.setConnectionId(3L);
        filing.setMediaAccountId(2L);
        filing.setFilingMethod(method);
        filing.setStatus(action == FilingAction.SUBMIT ? FilingStatus.NOT_SUBMITTED : FilingStatus.PENDING);
        filing.setTaskDataVersion(1);
        filing.setNextAction(action);
        filing.setRetryCount(0);
        return filing;
    }

    private PromotionMediaAccount account() {
        PromotionMediaAccount account = new PromotionMediaAccount();
        account.setId(2L);
        account.setMediaType(MediaType.TIKTOK);
        account.setExternalAccountId("creator-1");
        account.setAccountName("Creator");
        account.setAccountLink("https://tiktok.com/@creator-1");
        account.setStatus(1);
        account.setDataVersion(1);
        return account;
    }
}

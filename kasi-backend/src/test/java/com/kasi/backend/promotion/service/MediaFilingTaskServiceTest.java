package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.config.MediaFilingProperties;
import com.kasi.backend.promotion.entity.PromotionMediaAccount;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.enums.FilingAction;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.MediaType;
import com.kasi.backend.promotion.mapper.PromotionMediaAccountMapper;
import com.kasi.backend.promotion.mapper.ProviderMediaFilingMapper;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.AccountFilingProviderAdapter;
import com.kasi.backend.provider.spi.AccountFilingResult;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@DisplayName("媒体账号报备后台任务")
class MediaFilingTaskServiceTest {
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
                filingMapper, mediaMapper, connectionMapper, runtimeService, properties, clock);
    }

    @Test
    @DisplayName("到期提交任务成功后转为状态查询")
    void dueTaskQueriesFilingStatus() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 18, 8, 0);
        ProviderMediaFiling filing = filing();
        filing.setNextAction(FilingAction.QUERY);
        when(filingMapper.findDueIds(now, 50)).thenReturn(List.of(1L));
        when(filingMapper.claimLease(eq(1L), any(), eq(now), any())).thenReturn(1);
        when(filingMapper.findById(1L)).thenReturn(filing);
        when(mediaMapper.findById(2L)).thenReturn(account());
        ShortDramaConnection connection = new ShortDramaConnection();
        connection.setId(3L);
        connection.setProviderId(4L);
        when(connectionMapper.findById(3L)).thenReturn(connection);
        when(runtimeService.resolve(4L, com.kasi.backend.provider.enums.ProviderCapability.FILING_STATUS_QUERY))
                .thenReturn(new ProviderRuntimeConnection(3L, 4L, "GOODSHORT", "GoodShort",
                        new ProviderConnectionSecret("https://test", "pid", "key", "USD"), adapter));
        when(adapter.queryAccountFiling(any(), any())).thenReturn(
                new AccountFilingResult(FilingStatus.PENDING, "0", null, null, null));

        service.processDueBatch();

        verify(adapter).queryAccountFiling(any(), any());
        verify(filingMapper).completeQuery(eq(1L), any(), eq(1), eq(FilingStatus.PENDING), eq("0"),
                isNull(), isNull(), isNull(), eq(now), eq(FilingAction.QUERY), eq(now.plusMinutes(5)));
        verify(adapter, never()).submitAccountFiling(any(), any());
    }

    @Test
    @DisplayName("查询返回已加白后停止继续查询")
    void approvedQueryStopsFurtherQueries() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 18, 8, 0);
        ProviderMediaFiling filing = filing();
        filing.setNextAction(FilingAction.QUERY);
        when(filingMapper.findDueIds(now, 50)).thenReturn(List.of(1L));
        when(filingMapper.claimLease(eq(1L), any(), eq(now), any())).thenReturn(1);
        when(filingMapper.findById(1L)).thenReturn(filing);
        when(mediaMapper.findById(2L)).thenReturn(account());
        ShortDramaConnection connection = new ShortDramaConnection();
        connection.setId(3L);
        connection.setProviderId(4L);
        when(connectionMapper.findById(3L)).thenReturn(connection);
        when(runtimeService.resolve(4L, com.kasi.backend.provider.enums.ProviderCapability.FILING_STATUS_QUERY))
                .thenReturn(new ProviderRuntimeConnection(3L, 4L, "GOODSHORT", "GoodShort",
                        new ProviderConnectionSecret("https://test", "pid", "key", "USD"), adapter));
        when(adapter.queryAccountFiling(any(), any())).thenReturn(
                new AccountFilingResult(FilingStatus.APPROVED, "1", null, null, now));

        service.processDueBatch();

        verify(filingMapper).completeQuery(eq(1L), any(), eq(1), eq(FilingStatus.APPROVED), eq("1"),
                isNull(), isNull(), eq(now), eq(now), eq(FilingAction.NONE), isNull());
    }

    @Test
    @DisplayName("浜嬪姟鎻愪氦鍚庣珛鍗虫彁浜ゆ姤澶囦笖鎴愬姛鍚庡畾鏃舵煡璇")
    void submitNowSubmitsOnlyQueuedFiling() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 18, 8, 0);
        ProviderMediaFiling filing = filing();
        when(filingMapper.findById(1L)).thenReturn(filing);
        when(filingMapper.claimLease(eq(1L), any(), eq(now), any())).thenReturn(1);
        when(mediaMapper.findById(2L)).thenReturn(account());
        ShortDramaConnection connection = new ShortDramaConnection();
        connection.setId(3L);
        connection.setProviderId(4L);
        when(connectionMapper.findById(3L)).thenReturn(connection);
        when(runtimeService.resolve(4L, com.kasi.backend.provider.enums.ProviderCapability.ACCOUNT_FILING))
                .thenReturn(new ProviderRuntimeConnection(3L, 4L, "GOODSHORT", "GoodShort",
                        new ProviderConnectionSecret("https://test", "pid", "key", "USD"), adapter));

        service.submitNow(1L);

        verify(adapter).submitAccountFiling(any(), any());
        verify(filingMapper).completeSubmit(eq(1L), any(), eq(1), eq(now), eq(now.plusMinutes(1)));
    }

    private ProviderMediaFiling filing() {
        ProviderMediaFiling filing = new ProviderMediaFiling();
        filing.setId(1L);
        filing.setConnectionId(3L);
        filing.setMediaAccountId(2L);
        filing.setStatus(FilingStatus.PENDING);
        filing.setTaskDataVersion(1);
        filing.setNextAction(FilingAction.SUBMIT);
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

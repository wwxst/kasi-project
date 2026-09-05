package com.kasi.backend.promotion.service;

import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.AnalyticalReportProviderAdapter;
import com.kasi.backend.provider.spi.AnalyticalReportRequest;
import com.kasi.backend.provider.spi.ProviderAnalyticalReportPage;
import com.kasi.backend.provider.spi.ProviderAnalyticalReportRecord;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import com.kasi.backend.promotion.service.impl.PromotionAnalyticalReportSyncServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromotionAnalyticalReportSyncServiceTest {
    @Test
    @DisplayName("日报同步按500条分页并对每条记录执行upsert")
    void syncTraversesPagesAndUpsertsRecords() {
        ProviderRuntimeConnectionService runtimeService = mock(ProviderRuntimeConnectionService.class);
        PromotionAnalyticalReportService reportService = mock(PromotionAnalyticalReportService.class);
        AnalyticalReportProviderAdapter adapter = mock(AnalyticalReportProviderAdapter.class);
        ProviderRuntimeConnection runtime = new ProviderRuntimeConnection(3L, 7L, "GOODSHORT", "GoodShort",
                new ProviderConnectionSecret("url", "pid", "key", "USD"), adapter);
        LocalDate date = LocalDate.of(2026, 8, 19);
        when(runtimeService.resolve(7L, ProviderCapability.ANALYTICS_SYNC)).thenReturn(runtime);
        ProviderAnalyticalReportRecord first = new ProviderAnalyticalReportRecord(date, "pid", "u1", "b1", "c1",
                1L, 2L, 3L, 4L, 5L, 6L, 7L, new BigDecimal("8.90"));
        ProviderAnalyticalReportRecord second = new ProviderAnalyticalReportRecord(date, "pid", "u2", "b2", "c2",
                9L, 10L, 11L, 12L, 13L, 14L, 15L, new BigDecimal("16.70"));
        when(adapter.fetchAnalyticalReports(any(), eq(new AnalyticalReportRequest(date, date, 1, 500, null, null, null))))
                .thenReturn(new ProviderAnalyticalReportPage(List.of(first), 1, 500, 2, 2, true));
        when(adapter.fetchAnalyticalReports(any(), eq(new AnalyticalReportRequest(date, date, 2, 500, null, null, null))))
                .thenReturn(new ProviderAnalyticalReportPage(List.of(second), 2, 500, 2, 2, false));

        PromotionAnalyticalReportSyncService service =
                new PromotionAnalyticalReportSyncServiceImpl(runtimeService, reportService);

        var result = service.sync(7L, date, date, null, null, null);

        assertThat(result.getFetchedCount()).isEqualTo(2);
        verify(reportService).upsert(runtime, first);
        verify(reportService).upsert(runtime, second);
    }
}

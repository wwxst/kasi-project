package com.kasi.backend.scheduledtask.service;

import com.kasi.backend.drama.config.DramaSyncProperties;
import com.kasi.backend.drama.service.DramaCatalogSyncService;
import com.kasi.backend.drama.service.DramaContentSyncService;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import com.kasi.backend.promotion.service.PromotionAnalyticalReportSyncService;
import com.kasi.backend.promotion.service.PromotionOrderSyncService;
import com.kasi.backend.scheduledtask.config.ScheduledTaskProperties;
import com.kasi.backend.scheduledtask.entity.SystemScheduledTask;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCode;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCycleType;
import com.kasi.backend.scheduledtask.mapper.SystemScheduledTaskMapper;
import com.kasi.backend.scheduledtask.service.impl.ScheduledTaskDispatchServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnalyticalReportScheduledTaskTest {
    @Test
    @DisplayName("每日转化日报任务同步Asia/Shanghai昨日")
    void dispatchesYesterdayReport() {
        SystemScheduledTaskMapper taskMapper = mock(SystemScheduledTaskMapper.class);
        ShortDramaProviderMapper providerMapper = mock(ShortDramaProviderMapper.class);
        PromotionAnalyticalReportSyncService reportSync = mock(PromotionAnalyticalReportSyncService.class);
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 8, 0);
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T08:00:00Z"), ZoneOffset.UTC);
        SystemScheduledTask task = new SystemScheduledTask();
        task.setTaskCode(ScheduledTaskCode.GOODSHORT_ANALYTICAL_REPORT_SYNC);
        task.setCycleType(ScheduledTaskCycleType.DAILY);
        task.setTimeOfDay(java.time.LocalTime.of(8, 0));
        task.setEnabled(true);
        when(taskMapper.findDue(now, 10)).thenReturn(List.of(task));
        when(taskMapper.claimLease(eq(task.getTaskCode()), eq("worker"), eq(now), any())).thenReturn(1);
        ShortDramaProvider provider = new ShortDramaProvider();
        provider.setId(7L);
        provider.setStatus(1);
        when(providerMapper.findByCode("GOODSHORT")).thenReturn(provider);

        var service = new ScheduledTaskDispatchServiceImpl(taskMapper, providerMapper,
                mock(DramaCatalogSyncService.class), mock(DramaContentSyncService.class), tx,
                new ScheduledTaskProperties(), new DramaSyncProperties(), clock, "worker",
                mock(PromotionOrderSyncService.class), reportSync, new ScheduledTaskScheduleCalculator());

        service.processDueBatch();

        verify(reportSync).sync(7L, now.toLocalDate().minusDays(1), now.toLocalDate().minusDays(1), null, null, null);
    }
}

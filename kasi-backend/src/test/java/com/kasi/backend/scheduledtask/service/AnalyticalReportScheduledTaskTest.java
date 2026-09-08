package com.kasi.backend.scheduledtask.service;

import com.kasi.backend.drama.config.DramaSyncProperties;
import com.kasi.backend.drama.service.DramaCatalogSyncService;
import com.kasi.backend.drama.service.DramaContentSyncService;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import com.kasi.backend.promotion.service.PromotionAnalyticalReportSyncService;
import com.kasi.backend.promotion.service.PromotionOrderSyncService;
import com.kasi.backend.provider.exception.ProviderTransientException;
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
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnalyticalReportScheduledTaskTest {
    @Test
    @DisplayName("每日转化日报任务按Asia/Shanghai同步最近三个完整自然日")
    void dispatchesLastThreeCompletedDates() {
        SystemScheduledTaskMapper taskMapper = mock(SystemScheduledTaskMapper.class);
        ShortDramaProviderMapper providerMapper = mock(ShortDramaProviderMapper.class);
        PromotionAnalyticalReportSyncService reportSync = mock(PromotionAnalyticalReportSyncService.class);
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 8, 0);
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
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

        verify(reportSync).sync(7L, now.toLocalDate().minusDays(3), now.toLocalDate().minusDays(1),
                null, null, null);
    }

    @Test
    @DisplayName("转化日报同步失败时保留当前到期时间等待租约重试")
    void failedSyncDoesNotAdvanceSchedule() {
        SystemScheduledTaskMapper taskMapper = mock(SystemScheduledTaskMapper.class);
        ShortDramaProviderMapper providerMapper = mock(ShortDramaProviderMapper.class);
        PromotionAnalyticalReportSyncService reportSync = mock(PromotionAnalyticalReportSyncService.class);
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 8, 0);
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
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
        doThrow(new ProviderTransientException("temporary failure")).when(reportSync)
                .sync(7L, now.toLocalDate().minusDays(3), now.toLocalDate().minusDays(1), null, null, null);

        var service = new ScheduledTaskDispatchServiceImpl(taskMapper, providerMapper,
                mock(DramaCatalogSyncService.class), mock(DramaContentSyncService.class), tx,
                new ScheduledTaskProperties(), new DramaSyncProperties(), clock, "worker",
                mock(PromotionOrderSyncService.class), reportSync, new ScheduledTaskScheduleCalculator());

        service.processDueBatch();

        verify(taskMapper, never()).completeRun(any(ScheduledTaskCode.class), any(), any());
    }
    @Test
    @DisplayName("订单同步任务分别覆盖今日、昨日加今日和最近七日")
    void dispatchesOrderSyncWindows() {
        SystemScheduledTaskMapper taskMapper = mock(SystemScheduledTaskMapper.class);
        ShortDramaProviderMapper providerMapper = mock(ShortDramaProviderMapper.class);
        PromotionOrderSyncService orderSync = mock(PromotionOrderSyncService.class);
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 12, 0);
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        List<SystemScheduledTask> tasks = List.of(
                orderTask(ScheduledTaskCode.GOODSHORT_ORDER_TODAY_SYNC, 5),
                orderTask(ScheduledTaskCode.GOODSHORT_ORDER_SYNC, 60),
                orderTask(ScheduledTaskCode.GOODSHORT_ORDER_RECENT_SYNC, 3));
        when(taskMapper.findDue(now, 10)).thenReturn(tasks);
        for (SystemScheduledTask task : tasks) {
            when(taskMapper.claimLease(eq(task.getTaskCode()), eq("worker"), eq(now), any())).thenReturn(1);
        }
        ShortDramaProvider provider = new ShortDramaProvider();
        provider.setId(7L);
        when(providerMapper.findByCode("GOODSHORT")).thenReturn(provider);

        var service = new ScheduledTaskDispatchServiceImpl(taskMapper, providerMapper,
                mock(DramaCatalogSyncService.class), mock(DramaContentSyncService.class), tx,
                new ScheduledTaskProperties(), new DramaSyncProperties(), clock, "worker", orderSync);

        service.processDueBatch();

        verify(orderSync).sync(7L, now.toLocalDate().atStartOfDay(), now);
        verify(orderSync).sync(7L, now.toLocalDate().minusDays(1).atStartOfDay(), now);
        verify(orderSync).sync(7L, now.toLocalDate().minusDays(6).atStartOfDay(), now);
    }

    private static SystemScheduledTask orderTask(ScheduledTaskCode code, int interval) {
        SystemScheduledTask task = new SystemScheduledTask();
        task.setTaskCode(code);
        task.setCycleType(ScheduledTaskCycleType.INTERVAL_MINUTES);
        task.setIntervalValue(interval);
        task.setEnabled(true);
        return task;
    }
}

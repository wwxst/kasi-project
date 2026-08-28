package com.kasi.backend.scheduledtask.service;

import com.kasi.backend.drama.config.DramaSyncProperties;
import com.kasi.backend.drama.service.DramaCatalogSyncService;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import com.kasi.backend.promotion.service.PromotionOrderSyncService;
import com.kasi.backend.scheduledtask.config.ScheduledTaskProperties;
import com.kasi.backend.scheduledtask.entity.SystemScheduledTask;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCode;
import com.kasi.backend.scheduledtask.mapper.SystemScheduledTaskMapper;
import com.kasi.backend.scheduledtask.service.impl.ScheduledTaskDispatchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("系统定时任务分发服务")
class ScheduledTaskDispatchServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 8, 0);

    private SystemScheduledTaskMapper taskMapper;
    private ShortDramaProviderMapper providerMapper;
    private DramaCatalogSyncService syncService;
    private PromotionOrderSyncService orderSyncService;
    private ScheduledTaskDispatchService service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(SystemScheduledTaskMapper.class);
        providerMapper = mock(ShortDramaProviderMapper.class);
        syncService = mock(DramaCatalogSyncService.class);
        orderSyncService = mock(PromotionOrderSyncService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        ScheduledTaskProperties properties = new ScheduledTaskProperties();
        DramaSyncProperties dramaProperties = new DramaSyncProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T08:00:00Z"), ZoneOffset.UTC);
        service = new ScheduledTaskDispatchServiceImpl(
                taskMapper, providerMapper, syncService, transactionManager,
                properties, dramaProperties, clock, "scheduled-worker-test", orderSyncService);
    }

    @Test
    @DisplayName("到期GoodShort任务领取后创建增量同步并推进周期")
    void dueGoodShortTaskIsDispatchedAndAdvanced() {
        SystemScheduledTask task = scheduledTask();
        when(taskMapper.findDue(NOW, 10)).thenReturn(List.of(task));
        when(taskMapper.claimLease(ScheduledTaskCode.GOODSHORT_DRAMA_INCREMENTAL_SYNC, "scheduled-worker-test", NOW,
                NOW.plusMinutes(2))).thenReturn(1);
        ShortDramaProvider provider = new ShortDramaProvider();
        provider.setId(7L);
        provider.setProviderCode("GOODSHORT");
        provider.setStatus(1);
        when(providerMapper.findByCode("GOODSHORT")).thenReturn(provider);

        service.processDueBatch();

        verify(syncService).requestScheduledIncremental(7L, List.of("ENGLISH"));
        verify(taskMapper).completeRun(ScheduledTaskCode.GOODSHORT_DRAMA_INCREMENTAL_SYNC, "scheduled-worker-test", NOW.plusMinutes(60));
    }

    @Test
    @DisplayName("租约领取失败时不分发也不推进任务")
    void lostLeaseSkipsDispatch() {
        SystemScheduledTask task = scheduledTask();
        when(taskMapper.findDue(NOW, 10)).thenReturn(List.of(task));
        when(taskMapper.claimLease(ScheduledTaskCode.GOODSHORT_DRAMA_INCREMENTAL_SYNC, "scheduled-worker-test", NOW,
                NOW.plusMinutes(2))).thenReturn(0);

        service.processDueBatch();

        verify(providerMapper, never()).findByCode(any());
        verify(syncService, never()).requestScheduledIncremental(any(), any());
        verify(taskMapper, never()).completeRun(any(ScheduledTaskCode.class), any(), any());
    }

    @Test
    @DisplayName("平台不存在时不创建同步但仍推进任务周期")
    void missingProviderStillAdvancesSchedule() {
        SystemScheduledTask task = scheduledTask();
        when(taskMapper.findDue(NOW, 10)).thenReturn(List.of(task));
        when(taskMapper.claimLease(ScheduledTaskCode.GOODSHORT_DRAMA_INCREMENTAL_SYNC, "scheduled-worker-test", NOW,
                NOW.plusMinutes(2))).thenReturn(1);
        when(providerMapper.findByCode("GOODSHORT")).thenReturn(null);

        service.processDueBatch();

        verify(syncService, never()).requestScheduledIncremental(any(), any());
        verify(taskMapper).completeRun(ScheduledTaskCode.GOODSHORT_DRAMA_INCREMENTAL_SYNC, "scheduled-worker-test", NOW.plusMinutes(60));
    }

    @Test
    @DisplayName("到期GoodShort订单任务领取后同步最近三天并推进一分钟周期")
    void dueGoodShortOrderTaskIsDispatchedAndAdvanced() {
        SystemScheduledTask task = scheduledOrderTask();
        when(taskMapper.findDue(NOW, 10)).thenReturn(List.of(task));
        when(taskMapper.claimLease(ScheduledTaskCode.GOODSHORT_ORDER_SYNC, "scheduled-worker-test", NOW,
                NOW.plusMinutes(2))).thenReturn(1);
        ShortDramaProvider provider = new ShortDramaProvider();
        provider.setId(7L);
        provider.setProviderCode("GOODSHORT");
        provider.setStatus(1);
        when(providerMapper.findByCode("GOODSHORT")).thenReturn(provider);

        service.processDueBatch();

        verify(orderSyncService).sync(7L, NOW.minusDays(3), NOW);
        verify(taskMapper).completeRun(ScheduledTaskCode.GOODSHORT_ORDER_SYNC, "scheduled-worker-test", NOW.plusMinutes(1));
    }

    @Test
    @DisplayName("GoodShort订单任务丢失租约时不执行同步也不推进任务")
    void lostLeaseSkipsGoodShortOrderDispatch() {
        SystemScheduledTask task = scheduledOrderTask();
        when(taskMapper.findDue(NOW, 10)).thenReturn(List.of(task));
        when(taskMapper.claimLease(ScheduledTaskCode.GOODSHORT_DRAMA_INCREMENTAL_SYNC, "scheduled-worker-test", NOW,
                NOW.plusMinutes(2))).thenReturn(0);

        service.processDueBatch();

        verify(providerMapper, never()).findByCode(any());
        verify(orderSyncService, never()).sync(any(), any(), any());
        verify(taskMapper, never()).completeRun(any(ScheduledTaskCode.class), any(), any());
    }

    @Test
    @DisplayName("GoodShort订单任务找不到平台时不调用同步但推进一分钟周期")
    void missingProviderStillAdvancesGoodShortOrderSchedule() {
        SystemScheduledTask task = scheduledOrderTask();
        when(taskMapper.findDue(NOW, 10)).thenReturn(List.of(task));
        when(taskMapper.claimLease(ScheduledTaskCode.GOODSHORT_ORDER_SYNC, "scheduled-worker-test", NOW,
                NOW.plusMinutes(2))).thenReturn(1);
        when(providerMapper.findByCode("GOODSHORT")).thenReturn(null);

        service.processDueBatch();

        verify(orderSyncService, never()).sync(any(), any(), any());
        verify(taskMapper).completeRun(ScheduledTaskCode.GOODSHORT_ORDER_SYNC, "scheduled-worker-test", NOW.plusMinutes(1));
    }

    private SystemScheduledTask scheduledTask() {
        SystemScheduledTask task = new SystemScheduledTask();
        task.setTaskCode(ScheduledTaskCode.GOODSHORT_DRAMA_INCREMENTAL_SYNC);
        task.setIntervalValue(60);
        task.setEnabled(true);
        task.setNextRunAt(NOW.minusMinutes(1));
        return task;
    }

    private SystemScheduledTask scheduledOrderTask() {
        SystemScheduledTask task = scheduledTask();
        task.setTaskCode(ScheduledTaskCode.GOODSHORT_ORDER_SYNC);
        
        task.setIntervalValue(1);
        task.setCycleType(com.kasi.backend.scheduledtask.enums.ScheduledTaskCycleType.INTERVAL_MINUTES);
        return task;
    }
}

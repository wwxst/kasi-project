package com.kasi.backend.drama.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.drama.config.DramaSyncProperties;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.entity.ProviderSyncCheckpoint;
import com.kasi.backend.drama.enums.DramaSyncStatus;
import com.kasi.backend.drama.enums.DramaSyncType;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.drama.mapper.ProviderSyncCheckpointMapper;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.DramaCatalogPage;
import com.kasi.backend.provider.spi.DramaCatalogProviderAdapter;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.spi.ProviderDramaRecord;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.core.task.TaskExecutor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

@DisplayName("短剧目录同步服务")
class DramaCatalogSyncServiceTest {
    private ProviderSyncCheckpointMapper checkpointMapper;
    private ProviderDramaMapper dramaMapper;
    private ShortDramaConnectionMapper connectionMapper;
    private ProviderRuntimeConnectionService runtimeService;
    private DramaCatalogProviderAdapter adapter;
    private TaskExecutor taskExecutor;
    private DramaCatalogSyncService service;

    @BeforeEach
    void setUp() {
        checkpointMapper = mock(ProviderSyncCheckpointMapper.class);
        dramaMapper = mock(ProviderDramaMapper.class);
        connectionMapper = mock(ShortDramaConnectionMapper.class);
        runtimeService = mock(ProviderRuntimeConnectionService.class);
        adapter = mock(DramaCatalogProviderAdapter.class);
        taskExecutor = mock(TaskExecutor.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(checkpointMapper.requestRun(anyLong(), any(), anyBoolean())).thenReturn(1);
        DramaSyncProperties properties = new DramaSyncProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T08:00:00Z"), ZoneOffset.UTC);
        service = new com.kasi.backend.drama.service.impl.DramaCatalogSyncServiceImpl(
                checkpointMapper, dramaMapper, connectionMapper, runtimeService,
                transactionManager, properties, clock, "worker-test", taskExecutor);
    }

    @Test
    @DisplayName("手动同步事务提交后才异步唤醒，回滚不触发")
    void requestSyncTriggersOnlyAfterCommit() {
        when(runtimeService.resolve(7L, ProviderCapability.FULL_DRAMA_SYNC)).thenReturn(runtime());
        when(connectionMapper.lockById(3L)).thenReturn(mock(com.kasi.backend.provider.entity.ShortDramaConnection.class));
        when(checkpointMapper.find(3L, DramaSyncType.FULL, "ENGLISH")).thenReturn(null, checkpoint(11L, DramaSyncType.FULL));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.requestSync(7L, DramaSyncType.FULL, List.of("ENGLISH"));

            verifyNoInteractions(taskExecutor);
            var synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);
            synchronization.afterCompletion(2);
            verifyNoInteractions(taskExecutor);

            synchronization.afterCommit();
            verify(taskExecutor).execute(any(Runnable.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("没有成功全量基线时增量请求升级为全量且只标记待执行")
    void requestIncrementalWithoutBaselineRequestsFull() {
        when(runtimeService.resolve(7L, ProviderCapability.INCREMENTAL_DRAMA_SYNC)).thenReturn(runtime());
        when(connectionMapper.lockById(3L)).thenReturn(mock(com.kasi.backend.provider.entity.ShortDramaConnection.class));
        when(checkpointMapper.find(3L, DramaSyncType.FULL, "ENGLISH"))
                .thenReturn(null, null, checkpoint(11L, DramaSyncType.FULL));

        var tasks = service.requestSync(7L, DramaSyncType.INCREMENTAL, List.of("ENGLISH"));

        assertThat(tasks).singleElement().extracting(task -> task.syncType()).isEqualTo(DramaSyncType.FULL);
        verify(checkpointMapper).insert(argThat(value -> value.getSyncType() == DramaSyncType.FULL));
        verify(checkpointMapper).requestRun(11L, LocalDateTime.of(2026, 8, 20, 8, 0), true);
        verifyNoInteractions(adapter);
    }

    @Test
    @DisplayName("同类型已有运行任务时拒绝重复排队")
    void sameTypeRunningTaskIsRejected() {
        when(runtimeService.resolve(7L, ProviderCapability.FULL_DRAMA_SYNC)).thenReturn(runtime());
        when(connectionMapper.lockById(3L)).thenReturn(mock(com.kasi.backend.provider.entity.ShortDramaConnection.class));
        ProviderSyncCheckpoint running = checkpoint(11L, DramaSyncType.FULL);
        running.setStatus(DramaSyncStatus.RUNNING);
        when(checkpointMapper.findActive(3L, "ENGLISH")).thenReturn(List.of(running));

        assertThatThrownBy(() -> service.requestSync(7L, DramaSyncType.FULL, List.of("ENGLISH")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.DRAMA_SYNC_TASK_RUNNING.getCode()));
        verify(checkpointMapper, never()).requestRun(anyLong(), any(), anyBoolean());
    }

    @Test
    @DisplayName("跨类型已有排队任务时拒绝新的同步请求")
    void crossTypeQueuedTaskIsRejected() {
        when(runtimeService.resolve(7L, ProviderCapability.FULL_DRAMA_SYNC)).thenReturn(runtime());
        when(connectionMapper.lockById(3L)).thenReturn(mock(com.kasi.backend.provider.entity.ShortDramaConnection.class));
        ProviderSyncCheckpoint queued = checkpoint(12L, DramaSyncType.INCREMENTAL);
        queued.setStatus(DramaSyncStatus.REQUESTED);
        when(checkpointMapper.findActive(3L, "ENGLISH")).thenReturn(List.of(queued));

        assertThatThrownBy(() -> service.requestSync(7L, DramaSyncType.FULL, List.of("ENGLISH")))
                .isInstanceOf(BusinessException.class);
        verify(checkpointMapper, never()).requestRun(anyLong(), any(), anyBoolean());
    }

    @Test
    @DisplayName("定时增量在没有成功全量基线时不创建任务")
    void scheduledIncrementalWithoutBaselineIsSkipped() {
        when(runtimeService.resolve(7L, ProviderCapability.INCREMENTAL_DRAMA_SYNC)).thenReturn(runtime());
        when(connectionMapper.lockById(3L))
                .thenReturn(mock(com.kasi.backend.provider.entity.ShortDramaConnection.class));
        when(checkpointMapper.find(3L, DramaSyncType.FULL, "ENGLISH")).thenReturn(null);

        assertThat(service.requestScheduledIncremental(7L, List.of("ENGLISH"))).isEmpty();

        verify(checkpointMapper, never()).insert(any());
        verify(checkpointMapper, never()).requestRun(anyLong(), any(), anyBoolean());
    }

    @Test
    @DisplayName("定时增量在全量基线成功后创建增量任务")
    void scheduledIncrementalWithBaselineIsRequested() {
        when(runtimeService.resolve(7L, ProviderCapability.INCREMENTAL_DRAMA_SYNC)).thenReturn(runtime());
        when(connectionMapper.lockById(3L))
                .thenReturn(mock(com.kasi.backend.provider.entity.ShortDramaConnection.class));
        ProviderSyncCheckpoint full = checkpoint(11L, DramaSyncType.FULL);
        full.setStatus(DramaSyncStatus.SUCCESS);
        full.setLastSuccessAt(LocalDateTime.of(2026, 8, 19, 8, 0));
        ProviderSyncCheckpoint incremental = checkpoint(12L, DramaSyncType.INCREMENTAL);
        incremental.setStatus(DramaSyncStatus.SUCCESS);
        incremental.setLastSuccessAt(LocalDateTime.of(2026, 8, 20, 7, 0));
        when(checkpointMapper.find(3L, DramaSyncType.FULL, "ENGLISH")).thenReturn(full);
        when(checkpointMapper.find(3L, DramaSyncType.INCREMENTAL, "ENGLISH"))
                .thenReturn(incremental);
        when(checkpointMapper.findById(12L)).thenReturn(incremental);

        var tasks = service.requestScheduledIncremental(7L, List.of("ENGLISH"));

        assertThat(tasks).singleElement()
                .extracting(task -> task.syncType()).isEqualTo(DramaSyncType.INCREMENTAL);
        verify(checkpointMapper).requestRun(12L, LocalDateTime.of(2026, 8, 20, 8, 0), true);
    }

    @Test
    @DisplayName("定时增量发现活动任务时不重复排队")
    void scheduledIncrementalWithActiveTaskIsSkipped() {
        when(runtimeService.resolve(7L, ProviderCapability.INCREMENTAL_DRAMA_SYNC)).thenReturn(runtime());
        when(connectionMapper.lockById(3L))
                .thenReturn(mock(com.kasi.backend.provider.entity.ShortDramaConnection.class));
        when(checkpointMapper.findActive(3L, "ENGLISH"))
                .thenReturn(List.of(checkpoint(11L, DramaSyncType.FULL)));

        assertThat(service.requestScheduledIncremental(7L, List.of("ENGLISH"))).isEmpty();

        verify(checkpointMapper, never()).requestRun(anyLong(), any(), anyBoolean());
    }

    @Test
    @DisplayName("每页持久化完成后才推进检查点并在末页成功")
    void processPageThenAdvancesCheckpoint() {
        ProviderSyncCheckpoint checkpoint = checkpoint(11L, DramaSyncType.FULL);
        when(checkpointMapper.findDue(any(), eq(10))).thenReturn(List.of(checkpoint));
        when(checkpointMapper.claimLease(eq(11L), eq("worker-test"), any(), any())).thenReturn(1);
        when(checkpointMapper.findById(11L)).thenReturn(checkpoint);
        var connection = new com.kasi.backend.provider.entity.ShortDramaConnection();
        connection.setProviderId(7L);
        when(connectionMapper.findById(3L)).thenReturn(connection);
        when(runtimeService.resolve(7L, ProviderCapability.FULL_DRAMA_SYNC)).thenReturn(runtime());
        ProviderDramaRecord record = new ProviderDramaRecord("book-1", "Title", null, "中文标题", "Intro",
                "https://img/1", List.of("霸总", "爱情"), "爱情", "ENGLISH", 3, "SERIES",
                "ORIGINAL", 1, "ONLINE", LocalDateTime.of(2025, 8, 27, 11, 26, 18),
                LocalDateTime.of(2025, 8, 28, 11, 26, 18), List.of());
        when(adapter.fetchFullDramas(any(), any())).thenReturn(
                new DramaCatalogPage(List.of(record), 1, 100, 1, false, null));
        when(dramaMapper.findByConnectionAndExternalId(3L, "book-1")).thenReturn(null, storedDrama());
        when(checkpointMapper.updateProgress(anyLong(), anyString(), anyInt(), any(), anyInt(), anyInt(),
                anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(1);
        when(checkpointMapper.markSuccess(anyLong(), anyString(), any(), anyInt(), any())).thenReturn(1);

        service.processDueBatch();

        var order = inOrder(adapter, dramaMapper, checkpointMapper);
        order.verify(adapter).fetchFullDramas(any(), any());
        ArgumentCaptor<ProviderDrama> dramaCaptor = ArgumentCaptor.forClass(ProviderDrama.class);
        order.verify(dramaMapper).upsert(dramaCaptor.capture());
        assertThat(dramaCaptor.getValue().getTitleZh()).isEqualTo("中文标题");
        assertThat(dramaCaptor.getValue().getLabelNames()).isEqualTo("[\"霸总\",\"爱情\"]");
        assertThat(dramaCaptor.getValue().getCategoryName()).isEqualTo("爱情");
        assertThat(dramaCaptor.getValue().getRemoteRank()).isEqualTo(3);
        assertThat(dramaCaptor.getValue().getNovelType()).isEqualTo("ORIGINAL");
        assertThat(dramaCaptor.getValue().getNovelSubType()).isEqualTo(1);
        assertThat(dramaCaptor.getValue().getRemoteCreatedAt()).isEqualTo(LocalDateTime.of(2025, 8, 27, 11, 26, 18));
        order.verify(checkpointMapper).updateProgress(11L, "worker-test", 2, null, 1, 1, 1, 0, 0, 0);
        order.verify(checkpointMapper).markSuccess(11L, "worker-test",
                LocalDateTime.of(2026, 8, 20, 8, 0), 2, null);
    }

    @Test
    @DisplayName("第二页失败保留第一页进度并按租约所有者记录失败")
    void secondPageFailureKeepsFirstPageProgress() {
        ProviderSyncCheckpoint checkpoint = checkpoint(11L, DramaSyncType.FULL);
        when(checkpointMapper.findDue(any(), anyInt())).thenReturn(List.of(checkpoint));
        when(checkpointMapper.claimLease(anyLong(), anyString(), any(), any())).thenReturn(1);
        when(checkpointMapper.findById(11L)).thenReturn(checkpoint);
        var connection = new com.kasi.backend.provider.entity.ShortDramaConnection();
        connection.setProviderId(7L);
        when(connectionMapper.findById(3L)).thenReturn(connection);
        when(runtimeService.resolve(7L, ProviderCapability.FULL_DRAMA_SYNC)).thenReturn(runtime());
        when(adapter.fetchFullDramas(any(), any()))
                .thenReturn(new DramaCatalogPage(List.of(), 1, 100, 101, true, null))
                .thenThrow(new IllegalStateException("page two failed"));
        when(checkpointMapper.updateProgress(anyLong(), anyString(), anyInt(), any(), anyInt(), anyInt(),
                anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(1);
        when(checkpointMapper.markFailure(anyLong(), anyString(), any(), anyString(), anyString())).thenReturn(1);

        service.processDueBatch();

        verify(checkpointMapper, times(1)).updateProgress(11L, "worker-test", 2, null, 0, 0, 0, 0, 0, 0);
        verify(checkpointMapper).markFailure(eq(11L), eq("worker-test"), any(), eq("IllegalStateException"),
                eq("page two failed"));
        verify(checkpointMapper, never()).markSuccess(anyLong(), anyString(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("页数据写入后租约丢失会回滚事务并且不标记成功")
    void lostLeaseWhileAdvancingPageFailsTask() {
        ProviderSyncCheckpoint checkpoint = checkpoint(11L, DramaSyncType.FULL);
        when(checkpointMapper.findDue(any(), anyInt())).thenReturn(List.of(checkpoint));
        when(checkpointMapper.claimLease(anyLong(), anyString(), any(), any())).thenReturn(1);
        when(checkpointMapper.findById(11L)).thenReturn(checkpoint);
        var connection = new com.kasi.backend.provider.entity.ShortDramaConnection();
        connection.setProviderId(7L);
        when(connectionMapper.findById(3L)).thenReturn(connection);
        when(runtimeService.resolve(7L, ProviderCapability.FULL_DRAMA_SYNC)).thenReturn(runtime());
        ProviderDramaRecord record = new ProviderDramaRecord("book-lease", "Title", null, null, null,
                "ENGLISH", null, "ONLINE", null, List.of());
        when(adapter.fetchFullDramas(any(), any())).thenReturn(
                new DramaCatalogPage(List.of(record), 1, 100, 1, false, null));
        when(dramaMapper.findByConnectionAndExternalId(3L, "book-lease")).thenReturn(null, storedDrama());
        when(checkpointMapper.updateProgress(anyLong(), anyString(), anyInt(), any(), anyInt(), anyInt(),
                anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(0);

        service.processDueBatch();

        verify(dramaMapper).upsert(any(ProviderDrama.class));
        verify(checkpointMapper, never()).markSuccess(anyLong(), anyString(), any(), anyInt(), any());
        verify(checkpointMapper, never()).markFailure(anyLong(), anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("租约领取失败时不执行远端同步")
    void lostLeaseSkipsTask() {
        when(checkpointMapper.findDue(any(), anyInt())).thenReturn(List.of(checkpoint(11L, DramaSyncType.FULL)));
        when(checkpointMapper.claimLease(anyLong(), anyString(), any(), any())).thenReturn(0);

        service.processDueBatch();

        verifyNoInteractions(runtimeService, adapter);
    }

    @Test
    @DisplayName("人工报白或凭据不完整连接记录失败且不调用远端")
    void incompleteConnectionFailsWithoutRemoteCall() {
        ProviderSyncCheckpoint checkpoint = checkpoint(11L, DramaSyncType.FULL);
        when(checkpointMapper.findDue(any(), anyInt())).thenReturn(List.of(checkpoint));
        when(checkpointMapper.claimLease(anyLong(), anyString(), any(), any())).thenReturn(1);
        when(checkpointMapper.findById(11L)).thenReturn(checkpoint);
        var connection = new com.kasi.backend.provider.entity.ShortDramaConnection();
        connection.setProviderId(7L);
        when(connectionMapper.findById(3L)).thenReturn(connection);
        when(runtimeService.resolve(7L, ProviderCapability.FULL_DRAMA_SYNC))
                .thenThrow(mock(BusinessException.class));
        when(checkpointMapper.markFailure(anyLong(), anyString(), any(), anyString(), anyString())).thenReturn(1);

        service.processDueBatch();

        verify(checkpointMapper).markFailure(11L, "worker-test",
                LocalDateTime.of(2026, 8, 20, 8, 0), "CONNECTION_NOT_READY",
                "Provider connection is not ready for catalog synchronization");
        verify(checkpointMapper, never()).markSuccess(anyLong(), anyString(), any(), anyInt(), any());
        verifyNoInteractions(adapter);
    }

    @Test
    @DisplayName("增量检查点缺少全量基线时只请求全量任务")
    void staleIncrementalCheckpointRequestsFullTask() {
        ProviderSyncCheckpoint incremental = checkpoint(12L, DramaSyncType.INCREMENTAL);
        when(checkpointMapper.findDue(any(), anyInt())).thenReturn(List.of(incremental));
        when(checkpointMapper.claimLease(anyLong(), anyString(), any(), any())).thenReturn(1);
        when(checkpointMapper.findById(12L)).thenReturn(incremental);
        when(checkpointMapper.find(3L, DramaSyncType.FULL, "ENGLISH"))
                .thenReturn(null, checkpoint(11L, DramaSyncType.FULL));
        when(checkpointMapper.markFailure(anyLong(), anyString(), any(), anyString(), anyString())).thenReturn(1);

        service.processDueBatch();

        verify(checkpointMapper).requestRun(eq(11L), any(), eq(true));
        verify(checkpointMapper).markFailure(eq(12L), eq("worker-test"), any(),
                eq("FULL_BASELINE_REQUIRED"), anyString());
        verifyNoInteractions(adapter, runtimeService);
    }

    @Test
    @DisplayName("成功任务手动重跑从头开始而失败任务保留断点")
    void requestRestartDependsOnPreviousStatus() {
        when(runtimeService.resolve(7L, ProviderCapability.FULL_DRAMA_SYNC)).thenReturn(runtime());
        ProviderSyncCheckpoint success = checkpoint(11L, DramaSyncType.FULL);
        success.setStatus(DramaSyncStatus.SUCCESS);
        success.setLastSuccessAt(LocalDateTime.of(2026, 8, 19, 8, 0));
        when(checkpointMapper.find(3L, DramaSyncType.FULL, "ENGLISH")).thenReturn(success);
        when(checkpointMapper.requestRun(11L, LocalDateTime.of(2026, 8, 20, 8, 0), true)).thenReturn(1);
        when(checkpointMapper.findById(11L)).thenReturn(success);

        service.requestSync(7L, DramaSyncType.FULL, List.of("ENGLISH"));

        verify(checkpointMapper).requestRun(11L, LocalDateTime.of(2026, 8, 20, 8, 0), true);

        reset(checkpointMapper);
        ProviderSyncCheckpoint failed = checkpoint(11L, DramaSyncType.FULL);
        failed.setStatus(DramaSyncStatus.FAILED);
        failed.setPageNo(3);
        when(checkpointMapper.find(3L, DramaSyncType.FULL, "ENGLISH")).thenReturn(failed);
        when(checkpointMapper.requestRun(11L, LocalDateTime.of(2026, 8, 20, 8, 0), false)).thenReturn(1);
        when(checkpointMapper.findById(11L)).thenReturn(failed);

        service.requestSync(7L, DramaSyncType.FULL, List.of("ENGLISH"));

        verify(checkpointMapper).requestRun(11L, LocalDateTime.of(2026, 8, 20, 8, 0), false);
    }

    private ProviderRuntimeConnection runtime() {
        return new ProviderRuntimeConnection(3L, 7L, "GOODSHORT", "GoodShort",
                new ProviderConnectionSecret("https://test", "pid", "key", "USD"), adapter);
    }

    private ProviderSyncCheckpoint checkpoint(Long id, DramaSyncType type) {
        ProviderSyncCheckpoint checkpoint = new ProviderSyncCheckpoint();
        checkpoint.setId(id); checkpoint.setConnectionId(3L); checkpoint.setSyncType(type);
        checkpoint.setLanguage("ENGLISH"); checkpoint.setStatus(DramaSyncStatus.REQUESTED);
        checkpoint.setPageNo(1); checkpoint.setPageSize(100);
        return checkpoint;
    }

    private ProviderDrama storedDrama() {
        ProviderDrama drama = new ProviderDrama();
        drama.setId(21L);
        return drama;
    }
}

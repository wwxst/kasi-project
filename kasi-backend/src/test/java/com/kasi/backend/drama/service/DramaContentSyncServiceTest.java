package com.kasi.backend.drama.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.drama.config.DramaContentSyncProperties;
import com.kasi.backend.drama.entity.DramaContentSyncTask;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.entity.ProviderDramaContent;
import com.kasi.backend.drama.enums.DramaContentSyncStatus;
import com.kasi.backend.drama.mapper.DramaContentSyncTaskMapper;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.drama.service.impl.DramaContentSyncServiceImpl;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.exception.ProviderTransientException;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.FreeContentProviderAdapter;
import com.kasi.backend.provider.spi.FreeContentResult;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("免费剧集同步服务")
class DramaContentSyncServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 28, 12, 0);

    private DramaContentSyncTaskMapper taskMapper;
    private ProviderDramaMapper dramaMapper;
    private ShortDramaConnectionMapper connectionMapper;
    private ProviderRuntimeConnectionService runtimeService;
    private FreeContentProviderAdapter adapter;
    private TaskExecutor taskExecutor;
    private DramaSyncDisplayRunService displayRunService;
    private DramaContentSyncService service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(DramaContentSyncTaskMapper.class);
        dramaMapper = mock(ProviderDramaMapper.class);
        connectionMapper = mock(ShortDramaConnectionMapper.class);
        runtimeService = mock(ProviderRuntimeConnectionService.class);
        adapter = mock(FreeContentProviderAdapter.class);
        taskExecutor = mock(TaskExecutor.class);
        displayRunService = mock(DramaSyncDisplayRunService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        DramaContentSyncProperties properties = new DramaContentSyncProperties();
        properties.setBatchSize(50);
        properties.setCandidatePageSize(500);
        properties.setLeaseDuration(Duration.ofMinutes(2));
        properties.setMaxRetries(5);
        properties.setRetryDelays(List.of(Duration.ofMinutes(1), Duration.ofMinutes(5)));
        Clock clock = Clock.fixed(Instant.parse("2026-08-28T12:00:00Z"), ZoneOffset.UTC);
        service = new DramaContentSyncServiceImpl(taskMapper, dramaMapper, connectionMapper,
                runtimeService, new DramaMediaUrlValidator(), transactionManager,
                properties, clock, "content-worker-test", taskExecutor, displayRunService);
    }

    @Test
    @DisplayName("到期任务获取免费内容后按章节号保存视频地址并完成统计")
    void dueTaskPersistsFreeContentAndCompletes() {
        prepareClaimedTask(0);
        ProviderDramaContent existing = new ProviderDramaContent();
        existing.setDramaId(3L);
        existing.setSequenceNo(1);
        existing.setTitle("Chapter 1");
        when(dramaMapper.findContents(3L)).thenReturn(List.of(existing));
        when(adapter.fetchFreeContent(any(), eq("book-1"))).thenReturn(List.of(
                new FreeContentResult("Chapter 1", "https://v-koc.novelopen.com/1.m3u8"),
                new FreeContentResult("Bonus", "https://v-koc.novelopen.com/bonus.mp4")));
        when(taskMapper.markSuccess(11L, "content-worker-test", 2, 1, 1)).thenReturn(1);

        service.processDueBatch();

        ArgumentCaptor<ProviderDramaContent> captor = ArgumentCaptor.forClass(ProviderDramaContent.class);
        verify(dramaMapper, times(2)).upsertContent(captor.capture());
        assertThat(captor.getAllValues()).extracting(ProviderDramaContent::getSequenceNo)
                .containsExactly(1, 2);
        assertThat(captor.getAllValues()).extracting(ProviderDramaContent::getContentUrl)
                .containsExactly("https://v-koc.novelopen.com/1.m3u8", "https://v-koc.novelopen.com/bonus.mp4");
        assertThat(captor.getAllValues()).allSatisfy(content -> assertThat(content.getFree()).isTrue());
        verify(taskMapper).markSuccess(11L, "content-worker-test", 2, 1, 1);
    }

    @Test
    @DisplayName("平台返回非法视频地址时任务失败且不写入剧集")
    void invalidMediaUrlFailsWithoutPersistence() {
        prepareClaimedTask(0);
        when(adapter.fetchFreeContent(any(), eq("book-1"))).thenReturn(List.of(
                new FreeContentResult("Chapter 1", "http://127.0.0.1/private.m3u8")));
        when(taskMapper.markFailed(11L, "content-worker-test", 1,
                "INVALID_MEDIA_URL", "GoodShort returned an invalid media URL")).thenReturn(1);

        service.processDueBatch();

        verify(dramaMapper, never()).upsertContent(any());
        verify(taskMapper).markFailed(11L, "content-worker-test", 1,
                "INVALID_MEDIA_URL", "GoodShort returned an invalid media URL");
    }

    @Test
    @DisplayName("平台暂时不可用时按配置延迟重新排队")
    void transientFailureIsRetried() {
        prepareClaimedTask(0);
        when(adapter.fetchFreeContent(any(), eq("book-1")))
                .thenThrow(new ProviderTransientException("temporary"));
        when(taskMapper.recordRetry(11L, "content-worker-test", NOW.plusMinutes(1), 1,
                "REMOTE_TRANSIENT", "temporary")).thenReturn(1);

        service.processDueBatch();

        verify(taskMapper).recordRetry(11L, "content-worker-test", NOW.plusMinutes(1), 1,
                "REMOTE_TRANSIENT", "temporary");
        verify(taskMapper, never()).markFailed(anyLong(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("暂时失败达到最大次数后停止自动重试")
    void transientFailureStopsAtRetryLimit() {
        prepareClaimedTask(4);
        when(adapter.fetchFreeContent(any(), eq("book-1")))
                .thenThrow(new ProviderTransientException("temporary"));
        when(taskMapper.markFailed(11L, "content-worker-test", 5,
                "REMOTE_TRANSIENT", "temporary")).thenReturn(1);

        service.processDueBatch();

        verify(taskMapper).markFailed(11L, "content-worker-test", 5,
                "REMOTE_TRANSIENT", "temporary");
        verify(taskMapper, never()).recordRetry(anyLong(), any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("平台不支持免费内容能力时直接失败且不重试")
    void unsupportedCapabilityFailsWithoutRetry() {
        prepareClaimedTask(0);
        when(runtimeService.resolve(7L, ProviderCapability.FREE_CONTENT_PREVIEW))
                .thenThrow(new BusinessException(ErrorCode.PROVIDER_CAPABILITY_UNSUPPORTED));
        when(taskMapper.markFailed(11L, "content-worker-test", 1,
                "CAPABILITY_UNSUPPORTED", "短剧平台不支持该能力")).thenReturn(1);

        service.processDueBatch();

        verify(taskMapper).markFailed(11L, "content-worker-test", 1,
                "CAPABILITY_UNSUPPORTED", "短剧平台不支持该能力");
        verify(taskMapper, never()).recordRetry(anyLong(), any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("勾选批量同步会去重并跳过正在运行的任务")
    void batchRequestDeduplicatesAndSkipsRunningTask() {
        ProviderDrama first = drama(1L);
        ProviderDrama second = drama(2L);
        when(dramaMapper.findById(1L)).thenReturn(first);
        when(dramaMapper.findById(2L)).thenReturn(second);
        when(dramaMapper.findById(99L)).thenReturn(null);

        DramaContentSyncTask requested = task(21L, 1L, DramaContentSyncStatus.REQUESTED);
        DramaContentSyncTask running = task(22L, 2L, DramaContentSyncStatus.RUNNING);
        when(taskMapper.findByDramaId(1L)).thenReturn(null, requested);
        when(taskMapper.findByDramaId(2L)).thenReturn(running);

        var result = service.requestBatch(List.of(1L, 1L, 2L, 99L));

        assertThat(result.requestedCount()).isEqualTo(3);
        assertThat(result.queuedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.invalidCount()).isEqualTo(1);
        verify(taskMapper).request(1L, NOW);
        verify(taskMapper, never()).request(eq(2L), any());
        verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    @DisplayName("单部手动同步不存在短剧时返回短剧不存在错误")
    void singleRequestRejectsMissingDrama() {
        when(dramaMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.request(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.DRAMA_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("单部手动同步遇到运行中任务时返回任务运行中错误")
    void singleRequestRejectsRunningTask() {
        when(dramaMapper.findById(3L)).thenReturn(drama(3L));
        when(taskMapper.findByDramaId(3L)).thenReturn(task(21L, 3L, DramaContentSyncStatus.RUNNING));

        assertThatThrownBy(() -> service.request(3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(6016));
        verify(taskMapper, never()).request(anyLong(), any());
    }

    @Test
    @DisplayName("查询不存在的剧集同步任务时返回任务不存在错误")
    void getStatusRejectsMissingTask() {
        when(taskMapper.findByDramaId(3L)).thenReturn(null);

        assertThatThrownBy(() -> service.getStatus(3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(6017));
    }

    @Test
    @DisplayName("全部同步按候选短剧排队并唤醒任务处理器")
    void requestAllQueuesCandidatesAndTriggersWorker() {
        when(dramaMapper.findContentSyncCandidateIds(7L, "ENGLISH", true, 0L, 500))
                .thenReturn(List.of(1L, 2L));
        when(dramaMapper.findById(1L)).thenReturn(drama(1L));
        when(dramaMapper.findById(2L)).thenReturn(drama(2L));
        when(taskMapper.findByDramaId(1L)).thenReturn(null, task(21L, 1L, DramaContentSyncStatus.REQUESTED));
        when(taskMapper.findByDramaId(2L)).thenReturn(null, task(22L, 2L, DramaContentSyncStatus.REQUESTED));

        var result = service.requestAll(7L, "english", true);

        assertThat(result.requestedCount()).isEqualTo(2);
        assertThat(result.queuedCount()).isEqualTo(2);
        verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    @DisplayName("自动触发只创建任务而不调用GoodShort")
    void automaticRequestOnlyEnqueuesTask() {
        service.requestAutomatic(3L);

        verify(taskMapper).request(3L, NOW);
        verify(adapter, never()).fetchFreeContent(any(), any());
    }

    @Test
    @DisplayName("手动触发处理完一批后继续异步消费剩余待执行任务")
    void fullBatchSchedulesAnotherWorkerPass() {
        when(taskMapper.findDueIds(NOW, 50)).thenReturn(
                java.util.stream.LongStream.rangeClosed(1, 50).boxed().toList());
        when(taskMapper.claimLease(anyLong(), eq("content-worker-test"), eq(NOW), eq(NOW.plusMinutes(2))))
                .thenReturn(0);

        service.processDueBatch();

        verify(taskExecutor).execute(any(Runnable.class));
    }

    private void prepareClaimedTask(int retryCount) {
        DramaContentSyncTask task = new DramaContentSyncTask();
        task.setId(11L);
        task.setDramaId(3L);
        task.setStatus(DramaContentSyncStatus.RUNNING);
        task.setRetryCount(retryCount);
        when(taskMapper.findDueIds(NOW, 50)).thenReturn(List.of(11L));
        when(taskMapper.claimLease(11L, "content-worker-test", NOW, NOW.plusMinutes(2))).thenReturn(1);
        when(taskMapper.findById(11L)).thenReturn(task);

        ProviderDrama drama = new ProviderDrama();
        drama.setId(3L);
        drama.setConnectionId(4L);
        drama.setExternalDramaId("book-1");
        when(dramaMapper.findById(3L)).thenReturn(drama);

        ShortDramaConnection connection = new ShortDramaConnection();
        connection.setId(4L);
        connection.setProviderId(7L);
        connection.setMediaRootDomain("novelopen.com");
        when(connectionMapper.findById(4L)).thenReturn(connection);

        ProviderConnectionSecret secret = new ProviderConnectionSecret(
                "https://goodshort.test", "partner-1", "key", "USD");
        when(runtimeService.resolve(7L, ProviderCapability.FREE_CONTENT_PREVIEW))
                .thenReturn(new ProviderRuntimeConnection(4L, 7L, "GOODSHORT", "GoodShort", secret, adapter));
    }

    private ProviderDrama drama(Long id) {
        ProviderDrama drama = new ProviderDrama();
        drama.setId(id);
        return drama;
    }

    private DramaContentSyncTask task(Long id, Long dramaId, DramaContentSyncStatus status) {
        DramaContentSyncTask task = new DramaContentSyncTask();
        task.setId(id);
        task.setDramaId(dramaId);
        task.setStatus(status);
        return task;
    }
}

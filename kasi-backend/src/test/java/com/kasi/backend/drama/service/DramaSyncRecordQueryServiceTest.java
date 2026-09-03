package com.kasi.backend.drama.service;

import com.kasi.backend.drama.entity.DramaSyncDisplayRun;
import com.kasi.backend.drama.entity.DramaSyncDisplayRunItem;
import com.kasi.backend.drama.entity.ProviderSyncCheckpoint;
import com.kasi.backend.drama.enums.DramaSyncDomain;
import com.kasi.backend.drama.enums.DramaSyncStatus;
import com.kasi.backend.drama.enums.DramaSyncTaskType;
import com.kasi.backend.drama.enums.DramaSyncType;
import com.kasi.backend.drama.enums.SyncTriggerSource;
import com.kasi.backend.drama.enums.SyncRecordStatus;
import com.kasi.backend.drama.mapper.DramaContentSyncTaskMapper;
import com.kasi.backend.drama.mapper.DramaSyncDisplayRunMapper;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.drama.mapper.ProviderSyncCheckpointMapper;
import com.kasi.backend.drama.service.impl.DramaSyncRecordQueryServiceImpl;
import com.kasi.backend.drama.vo.DramaSyncRecordVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("同步记录聚合查询服务")
class DramaSyncRecordQueryServiceTest {
    private final DramaSyncDisplayRunMapper runMapper = mock(DramaSyncDisplayRunMapper.class);
    private final ProviderSyncCheckpointMapper checkpointMapper = mock(ProviderSyncCheckpointMapper.class);
    private final DramaContentSyncTaskMapper contentTaskMapper = mock(DramaContentSyncTaskMapper.class);
    private final ProviderDramaMapper dramaMapper = mock(ProviderDramaMapper.class);
    private final DramaSyncRecordQueryService service = new DramaSyncRecordQueryServiceImpl(
            runMapper, checkpointMapper, contentTaskMapper, dramaMapper);

    @Test
    @DisplayName("目录同一运行的语言任务统计求和并派生部分失败")
    void aggregatesCatalogTasksAndDerivesPartialFailure() {
        DramaSyncDisplayRun run = run("run-1", DramaSyncDomain.CATALOG,
                DramaSyncTaskType.FULL, SyncTriggerSource.MANUAL);
        when(runMapper.findRuns(7L, DramaSyncDomain.CATALOG)).thenReturn(List.of(run));
        when(runMapper.findItems("run-1", DramaSyncDomain.CATALOG)).thenReturn(
                List.of(item("run-1", DramaSyncDomain.CATALOG, 11L),
                        item("run-1", DramaSyncDomain.CATALOG, 12L)));
        when(checkpointMapper.findByIds(anyList())).thenReturn(List.of(
                checkpoint(11L, DramaSyncStatus.SUCCESS, 3, 2, 5),
                checkpoint(12L, DramaSyncStatus.FAILED, 4, 1, 6)));

        List<DramaSyncRecordVO> records = service.listCatalog(7L);

        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.id()).isEqualTo("run-1");
            assertThat(record.status()).isEqualTo(com.kasi.backend.drama.enums.SyncRecordStatus.PARTIAL_FAILED);
            assertThat(record.insertedCount()).isEqualTo(7);
            assertThat(record.updatedCount()).isEqualTo(3);
            assertThat(record.totalProcessed()).isEqualTo(11);
        });
    }

    @Test
    @DisplayName("短剧聚合记录按子任务组合派生五种展示状态")
    void listCatalog_derivesAllDisplayStatuses() {
        assertCatalogStatus(List.of(DramaSyncStatus.REQUESTED), SyncRecordStatus.WAITING);
        assertCatalogStatus(List.of(DramaSyncStatus.SUCCESS, DramaSyncStatus.REQUESTED), SyncRecordStatus.RUNNING);
        assertCatalogStatus(List.of(DramaSyncStatus.SUCCESS), SyncRecordStatus.SUCCESS);
        assertCatalogStatus(List.of(DramaSyncStatus.SUCCESS, DramaSyncStatus.FAILED), SyncRecordStatus.PARTIAL_FAILED);
        assertCatalogStatus(List.of(DramaSyncStatus.FAILED), SyncRecordStatus.FAILED);
    }

    private void assertCatalogStatus(List<DramaSyncStatus> statuses, SyncRecordStatus expected) {
        DramaSyncDisplayRun run = run("run-status", DramaSyncDomain.CATALOG,
                DramaSyncTaskType.FULL, SyncTriggerSource.MANUAL);
        List<DramaSyncDisplayRunItem> items = java.util.stream.LongStream.range(0, statuses.size())
                .mapToObj(index -> item("run-status", DramaSyncDomain.CATALOG, index + 1))
                .toList();
        List<ProviderSyncCheckpoint> checkpoints = java.util.stream.IntStream.range(0, statuses.size())
                .mapToObj(index -> checkpoint((long) index + 1, statuses.get(index), 0, 0, 0))
                .toList();
        when(runMapper.findRuns(7L, DramaSyncDomain.CATALOG)).thenReturn(List.of(run));
        when(runMapper.findItems("run-status", DramaSyncDomain.CATALOG)).thenReturn(items);
        when(checkpointMapper.findByIds(anyList())).thenReturn(checkpoints);

        assertThat(service.listCatalog(7L)).singleElement()
                .extracting(DramaSyncRecordVO::status)
                .isEqualTo(expected);
    }

    private DramaSyncDisplayRun run(String id, DramaSyncDomain domain,
                                    DramaSyncTaskType taskType, SyncTriggerSource trigger) {
        DramaSyncDisplayRun run = new DramaSyncDisplayRun();
        run.setId(id);
        run.setProviderId(7L);
        run.setDomain(domain);
        run.setTaskType(taskType);
        run.setTriggerSource(trigger);
        run.setRequestedAt(LocalDateTime.of(2026, 8, 29, 8, 25));
        return run;
    }

    private DramaSyncDisplayRunItem item(String runId, DramaSyncDomain domain, Long taskId) {
        DramaSyncDisplayRunItem item = new DramaSyncDisplayRunItem();
        item.setRunId(runId);
        item.setTaskDomain(domain);
        item.setTaskId(taskId);
        return item;
    }

    private ProviderSyncCheckpoint checkpoint(Long id, DramaSyncStatus status,
                                              int inserted, int updated, int fetched) {
        ProviderSyncCheckpoint checkpoint = new ProviderSyncCheckpoint();
        checkpoint.setId(id);
        checkpoint.setSyncType(DramaSyncType.FULL);
        checkpoint.setLanguage("ENGLISH");
        checkpoint.setStatus(status);
        checkpoint.setInsertedCount(inserted);
        checkpoint.setUpdatedCount(updated);
        checkpoint.setTotalFetched(fetched);
        return checkpoint;
    }
}

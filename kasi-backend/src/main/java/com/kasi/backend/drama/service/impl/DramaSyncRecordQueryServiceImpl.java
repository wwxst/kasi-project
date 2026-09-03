package com.kasi.backend.drama.service.impl;

import com.kasi.backend.drama.entity.DramaSyncDisplayRun;
import com.kasi.backend.drama.entity.DramaSyncDisplayRunItem;
import com.kasi.backend.drama.entity.DramaContentSyncTask;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.entity.ProviderSyncCheckpoint;
import com.kasi.backend.drama.enums.DramaContentSyncStatus;
import com.kasi.backend.drama.enums.DramaSyncDomain;
import com.kasi.backend.drama.enums.DramaSyncStatus;
import com.kasi.backend.drama.enums.SyncRecordStatus;
import com.kasi.backend.drama.mapper.DramaContentSyncTaskMapper;
import com.kasi.backend.drama.mapper.DramaSyncDisplayRunMapper;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.drama.mapper.ProviderSyncCheckpointMapper;
import com.kasi.backend.drama.service.DramaSyncRecordQueryService;
import com.kasi.backend.drama.vo.DramaContentSyncRecordDetailVO;
import com.kasi.backend.drama.vo.DramaSyncRecordDetailVO;
import com.kasi.backend.drama.vo.DramaSyncRecordVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class DramaSyncRecordQueryServiceImpl implements DramaSyncRecordQueryService {
    private final DramaSyncDisplayRunMapper runMapper;
    private final ProviderSyncCheckpointMapper checkpointMapper;
    private final DramaContentSyncTaskMapper contentTaskMapper;
    private final ProviderDramaMapper dramaMapper;

    public DramaSyncRecordQueryServiceImpl(DramaSyncDisplayRunMapper runMapper,
                                           ProviderSyncCheckpointMapper checkpointMapper,
                                           DramaContentSyncTaskMapper contentTaskMapper,
                                           ProviderDramaMapper dramaMapper) {
        this.runMapper = runMapper;
        this.checkpointMapper = checkpointMapper;
        this.contentTaskMapper = contentTaskMapper;
        this.dramaMapper = dramaMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DramaSyncRecordVO> listCatalog(Long providerId) {
        return runMapper.findRuns(providerId, DramaSyncDomain.CATALOG).stream()
                .map(run -> aggregateCatalog(run, runMapper.findItems(run.getId(), DramaSyncDomain.CATALOG)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DramaSyncRecordVO> listContent(Long providerId) {
        return runMapper.findRuns(providerId, DramaSyncDomain.CONTENT).stream()
                .map(run -> aggregateContent(run, runMapper.findItems(run.getId(), DramaSyncDomain.CONTENT)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DramaSyncRecordDetailVO> catalogDetails(Long providerId, String runId) {
        DramaSyncDisplayRun run = requireRun(providerId, runId, DramaSyncDomain.CATALOG);
        List<Long> ids = runMapper.findItems(run.getId(), DramaSyncDomain.CATALOG).stream()
                .map(DramaSyncDisplayRunItem::getTaskId).toList();
        return checkpointMapper.findByIds(ids).stream().map(this::catalogDetail).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DramaContentSyncRecordDetailVO> contentDetails(Long providerId, String runId) {
        DramaSyncDisplayRun run = requireRun(providerId, runId, DramaSyncDomain.CONTENT);
        List<DramaContentSyncRecordDetailVO> details = new ArrayList<>();
        for (DramaSyncDisplayRunItem item : runMapper.findItems(run.getId(), DramaSyncDomain.CONTENT)) {
            DramaContentSyncTask task = contentTaskMapper.findById(item.getTaskId());
            if (task == null) continue;
            ProviderDrama drama = dramaMapper.findById(task.getDramaId());
            details.add(contentDetail(task, drama));
        }
        return details;
    }

    private DramaSyncDisplayRun requireRun(Long providerId, String runId, DramaSyncDomain domain) {
        DramaSyncDisplayRun run = runMapper.findById(runId, providerId, domain);
        if (run == null) throw new IllegalArgumentException("同步记录不存在");
        return run;
    }

    private DramaSyncRecordVO aggregateCatalog(DramaSyncDisplayRun run, List<DramaSyncDisplayRunItem> items) {
        List<ProviderSyncCheckpoint> tasks = checkpointMapper.findByIds(items.stream()
                .map(DramaSyncDisplayRunItem::getTaskId).toList());
        int inserted = tasks.stream().mapToInt(task -> value(task.getInsertedCount())).sum();
        int updated = tasks.stream().mapToInt(task -> value(task.getUpdatedCount())).sum();
        int total = tasks.stream().mapToInt(task -> value(task.getTotalFetched())).sum();
        return new DramaSyncRecordVO(run.getId(), run.getRequestedAt(), run.getTriggerSource(),
                run.getTaskType(), catalogStatus(tasks), inserted, updated, total);
    }

    private DramaSyncRecordVO aggregateContent(DramaSyncDisplayRun run, List<DramaSyncDisplayRunItem> items) {
        List<DramaContentSyncTask> tasks = items.stream().map(DramaSyncDisplayRunItem::getTaskId)
                .map(contentTaskMapper::findById).filter(java.util.Objects::nonNull).toList();
        int inserted = tasks.stream().mapToInt(task -> value(task.getInsertedCount())).sum();
        int updated = tasks.stream().mapToInt(task -> value(task.getUpdatedCount())).sum();
        int total = tasks.stream().mapToInt(task -> value(task.getTotalFetched())).sum();
        return new DramaSyncRecordVO(run.getId(), run.getRequestedAt(), run.getTriggerSource(),
                run.getTaskType(), contentStatus(tasks), inserted, updated, total);
    }

    private SyncRecordStatus catalogStatus(List<ProviderSyncCheckpoint> tasks) {
        List<DramaSyncStatus> statuses = tasks.stream().map(ProviderSyncCheckpoint::getStatus).toList();
        return aggregate(statuses, DramaSyncStatus.SUCCESS, DramaSyncStatus.FAILED);
    }

    private SyncRecordStatus contentStatus(List<DramaContentSyncTask> tasks) {
        List<DramaContentSyncStatus> statuses = tasks.stream().map(DramaContentSyncTask::getStatus).toList();
        boolean waiting = statuses.stream().anyMatch(status -> status == DramaContentSyncStatus.REQUESTED);
        boolean running = statuses.stream().anyMatch(status -> status == DramaContentSyncStatus.RUNNING);
        boolean success = statuses.stream().anyMatch(status -> status == DramaContentSyncStatus.SUCCESS);
        boolean failed = statuses.stream().anyMatch(status -> status == DramaContentSyncStatus.FAILED);
        if (running || (waiting && (success || failed))) return SyncRecordStatus.RUNNING;
        if (success && failed) return SyncRecordStatus.PARTIAL_FAILED;
        if (success) return SyncRecordStatus.SUCCESS;
        if (failed) return SyncRecordStatus.FAILED;
        return SyncRecordStatus.WAITING;
    }

    private <T> SyncRecordStatus aggregate(List<T> statuses, T successValue, T failedValue) {
        boolean waiting = statuses.stream().anyMatch(status -> status == DramaSyncStatus.IDLE
                || status == DramaSyncStatus.REQUESTED);
        boolean running = statuses.stream().anyMatch(status -> status == DramaSyncStatus.RUNNING);
        boolean success = statuses.stream().anyMatch(status -> status == successValue);
        boolean failed = statuses.stream().anyMatch(status -> status == failedValue);
        if (running || (waiting && (success || failed))) return SyncRecordStatus.RUNNING;
        if (success && failed) return SyncRecordStatus.PARTIAL_FAILED;
        if (success) return SyncRecordStatus.SUCCESS;
        if (failed) return SyncRecordStatus.FAILED;
        return SyncRecordStatus.WAITING;
    }

    private DramaSyncRecordDetailVO catalogDetail(ProviderSyncCheckpoint task) {
        return new DramaSyncRecordDetailVO(task.getId(), task.getLanguage(), task.getSyncType(), task.getStatus(),
                value(task.getPageNo()), value(task.getInsertedCount()), value(task.getUpdatedCount()),
                value(task.getTotalFetched()), task.getLastErrorCode(), task.getLastErrorMessage());
    }

    private DramaContentSyncRecordDetailVO contentDetail(DramaContentSyncTask task, ProviderDrama drama) {
        return new DramaContentSyncRecordDetailVO(task.getId(), task.getDramaId(),
                drama == null ? null : firstNonBlank(drama.getTitleZh(), drama.getTitle()),
                drama == null ? null : drama.getLanguage(), task.getStatus(), value(task.getRetryCount()),
                value(task.getInsertedCount()), value(task.getUpdatedCount()), value(task.getTotalFetched()),
                task.getLastErrorCode(), task.getLastErrorMessage());
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}

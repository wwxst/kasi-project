package com.kasi.backend.drama.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.drama.config.DramaSyncProperties;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.entity.ProviderDramaContent;
import com.kasi.backend.drama.entity.ProviderSyncCheckpoint;
import com.kasi.backend.drama.enums.DramaSyncStatus;
import com.kasi.backend.drama.enums.DramaSyncType;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.drama.mapper.ProviderSyncCheckpointMapper;
import com.kasi.backend.drama.service.DramaCatalogSyncService;
import com.kasi.backend.drama.vo.DramaSyncTaskVO;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.DramaCatalogFetchRequest;
import com.kasi.backend.provider.spi.DramaCatalogPage;
import com.kasi.backend.provider.spi.DramaCatalogProviderAdapter;
import com.kasi.backend.provider.spi.ProviderDramaRecord;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class DramaCatalogSyncServiceImpl implements DramaCatalogSyncService {
    private static final Logger log = LoggerFactory.getLogger(DramaCatalogSyncServiceImpl.class);
    private final ProviderSyncCheckpointMapper checkpointMapper;
    private final ProviderDramaMapper dramaMapper;
    private final ShortDramaConnectionMapper connectionMapper;
    private final ProviderRuntimeConnectionService runtimeService;
    private final TransactionTemplate transactionTemplate;
    private final DramaSyncProperties properties;
    private final Clock clock;
    private final String workerId;
    private final TaskExecutor taskExecutor;
    private final tools.jackson.databind.ObjectMapper objectMapper = JsonMapper.builder().build();

    public DramaCatalogSyncServiceImpl(ProviderSyncCheckpointMapper checkpointMapper,
                                       ProviderDramaMapper dramaMapper,
                                       ShortDramaConnectionMapper connectionMapper,
                                       ProviderRuntimeConnectionService runtimeService,
                                       PlatformTransactionManager transactionManager,
                                       DramaSyncProperties properties,
                                       Clock clock,
                                       @Value("${app.promotion.drama.sync.worker-id:${random.uuid}}") String workerId,
                                       @Qualifier("dramaSyncTaskExecutor") TaskExecutor taskExecutor) {
        this.checkpointMapper = checkpointMapper;
        this.dramaMapper = dramaMapper;
        this.connectionMapper = connectionMapper;
        this.runtimeService = runtimeService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.properties = properties;
        this.clock = clock;
        this.workerId = workerId;
        this.taskExecutor = taskExecutor;
    }

    @Override
    @Transactional
    public List<DramaSyncTaskVO> requestSync(Long providerId, DramaSyncType requestedType, List<String> languages) {
        ProviderCapability requestedCapability = capability(requestedType);
        ProviderRuntimeConnection runtime;
        try {
            runtime = runtimeService.resolve(providerId, requestedCapability);
        } catch (BusinessException exception) {
            return List.of();
        }
        if (!usable(runtime) || !(runtime.adapter() instanceof DramaCatalogProviderAdapter)) {
            return List.of();
        }
        connectionMapper.lockById(runtime.connectionId());
        List<DramaSyncTaskVO> tasks = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);
        for (String language : normalizeLanguages(languages)) {
            List<ProviderSyncCheckpoint> active = checkpointMapper.findActive(runtime.connectionId(), language);
            if (active != null && !active.isEmpty()) {
                throw new BusinessException(ErrorCode.DRAMA_SYNC_TASK_RUNNING);
            }
            DramaSyncType effectiveType = effectiveType(runtime.connectionId(), requestedType, language);
            ProviderSyncCheckpoint checkpoint = ensureCheckpoint(runtime.connectionId(), effectiveType, language);
            boolean restart = checkpoint.getStatus() != DramaSyncStatus.FAILED;
            if (checkpointMapper.requestRun(checkpoint.getId(), now, restart) == 1) {
                ProviderSyncCheckpoint requested = checkpointMapper.findById(checkpoint.getId());
                tasks.add(DramaSyncTaskVO.from(requested == null ? checkpoint : requested));
            } else {
                throw new BusinessException(ErrorCode.DRAMA_SYNC_TASK_RUNNING);
            }
        }
        triggerAfterCommit();
        return tasks;
    }

    private void triggerAfterCommit() {
        Runnable trigger = () -> {
            try {
                taskExecutor.execute(this::processDueBatch);
            } catch (RuntimeException exception) {
                log.warn("Failed to submit immediate drama catalog synchronization", exception);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            trigger.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                trigger.run();
            }
        });
    }

    @Override
    @Transactional
    public List<DramaSyncTaskVO> requestScheduledIncremental(Long providerId, List<String> languages) {
        ProviderRuntimeConnection runtime;
        try {
            runtime = runtimeService.resolve(providerId, ProviderCapability.INCREMENTAL_DRAMA_SYNC);
        } catch (BusinessException exception) {
            return List.of();
        }
        if (!usable(runtime) || !(runtime.adapter() instanceof DramaCatalogProviderAdapter)) {
            return List.of();
        }
        connectionMapper.lockById(runtime.connectionId());
        List<DramaSyncTaskVO> tasks = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);
        for (String language : normalizeLanguages(languages)) {
            List<ProviderSyncCheckpoint> active = checkpointMapper.findActive(runtime.connectionId(), language);
            if (active != null && !active.isEmpty()) {
                continue;
            }
            ProviderSyncCheckpoint full = checkpointMapper.find(
                    runtime.connectionId(), DramaSyncType.FULL, language);
            if (full == null || full.getStatus() != DramaSyncStatus.SUCCESS
                    || full.getLastSuccessAt() == null) {
                continue;
            }
            ProviderSyncCheckpoint incremental = ensureCheckpoint(
                    runtime.connectionId(), DramaSyncType.INCREMENTAL, language);
            boolean restart = incremental.getStatus() != DramaSyncStatus.FAILED;
            if (checkpointMapper.requestRun(incremental.getId(), now, restart) == 1) {
                ProviderSyncCheckpoint requested = checkpointMapper.findById(incremental.getId());
                tasks.add(DramaSyncTaskVO.from(requested == null ? incremental : requested));
            }
        }
        return tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DramaSyncTaskVO> getStatuses(Long providerId) {
        ShortDramaConnection connection = connectionMapper.findByProviderId(providerId);
        if (connection == null) return List.of();
        return checkpointMapper.findByConnectionId(connection.getId()).stream()
                .map(DramaSyncTaskVO::from)
                .toList();
    }

    @Override
    public void processDueBatch() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<ProviderSyncCheckpoint> due = checkpointMapper.findDue(now, properties.getBatchSize());
        if (due == null) return;
        for (ProviderSyncCheckpoint candidate : due) {
            if (!claim(candidate, now)) {
                continue;
            }
            ProviderSyncCheckpoint checkpoint = checkpointMapper.findById(candidate.getId());
            if (checkpoint != null) processClaimed(checkpoint);
        }
    }

    private boolean claim(ProviderSyncCheckpoint candidate, LocalDateTime now) {
        Boolean claimed = transactionTemplate.execute(status -> {
            connectionMapper.lockById(candidate.getConnectionId());
            return checkpointMapper.claimLease(candidate.getId(), workerId, now,
                    now.plus(properties.getLeaseDuration())) == 1;
        });
        return Boolean.TRUE.equals(claimed);
    }

    private void processClaimed(ProviderSyncCheckpoint checkpoint) {
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            if (checkpoint.getSyncType() == DramaSyncType.INCREMENTAL
                    && effectiveType(checkpoint.getConnectionId(), DramaSyncType.INCREMENTAL,
                    checkpoint.getLanguage()) == DramaSyncType.FULL) {
                ProviderSyncCheckpoint full = ensureCheckpoint(checkpoint.getConnectionId(), DramaSyncType.FULL,
                        checkpoint.getLanguage());
                checkpointMapper.requestRun(full.getId(), now, full.getStatus() != DramaSyncStatus.FAILED);
                markFailure(checkpoint, now, "FULL_BASELINE_REQUIRED",
                        "Full catalog synchronization is required before incremental synchronization");
                return;
            }
            ShortDramaConnection connection = connectionMapper.findById(checkpoint.getConnectionId());
            if (connection == null) {
                markConnectionNotReady(checkpoint, now);
                return;
            }
            DramaSyncType effectiveType = effectiveType(checkpoint.getConnectionId(), checkpoint.getSyncType(),
                    checkpoint.getLanguage());
            ProviderRuntimeConnection runtime;
            try {
                runtime = runtimeService.resolve(connection.getProviderId(), capability(effectiveType));
            } catch (BusinessException exception) {
                markConnectionNotReady(checkpoint, now);
                return;
            }
            if (!usable(runtime) || !(runtime.adapter() instanceof DramaCatalogProviderAdapter adapter)) {
                markConnectionNotReady(checkpoint, now);
                return;
            }
            fetchPages(checkpoint, effectiveType, runtime, adapter);
        } catch (LeaseLostException ignored) {
            // Another worker owns the task now; this worker must not write any terminal state.
        } catch (RuntimeException exception) {
            try {
                markFailure(checkpoint, LocalDateTime.now(clock), exception.getClass().getSimpleName(),
                        safeMessage(exception));
            } catch (LeaseLostException ignored) {
                // Lease takeover won the race with failure reporting.
            }
        }
    }

    private void fetchPages(ProviderSyncCheckpoint checkpoint,
                            DramaSyncType effectiveType,
                            ProviderRuntimeConnection runtime,
                            DramaCatalogProviderAdapter adapter) {
        int pageNo = value(checkpoint.getPageNo(), 1);
        int pageSize = value(checkpoint.getPageSize(), properties.getPageSize());
        Long updateTime = checkpoint.getUpdateTime();
        boolean hasNext;
        do {
            DramaCatalogFetchRequest request = new DramaCatalogFetchRequest(
                    pageNo, pageSize, checkpoint.getLanguage(), effectiveType == DramaSyncType.INCREMENTAL ? updateTime : null);
            DramaCatalogPage page = effectiveType == DramaSyncType.FULL
                    ? adapter.fetchFullDramas(runtime.secret(), request)
                    : adapter.fetchIncrementalDramas(runtime.secret(), request);
            if (page == null) throw new IllegalStateException("Provider returned no catalog page");
            Long nextUpdateTime = page.nextUpdateTime() == null ? updateTime : page.nextUpdateTime();
            int nextPageNo = pageNo + 1;
            Long progressUpdateTime = nextUpdateTime;
            transactionTemplate.executeWithoutResult(status -> {
                PageStats stats = persistPage(runtime.connectionId(), page.items());
                if (checkpointMapper.updateProgress(checkpoint.getId(), workerId, nextPageNo, progressUpdateTime,
                        stats.fetched(), stats.upserted(), stats.inserted(), stats.updated(), stats.skipped(), 0) != 1) {
                    throw new LeaseLostException();
                }
            });
            pageNo = nextPageNo;
            updateTime = nextUpdateTime;
            hasNext = page.hasNext();
        } while (hasNext);
        if (checkpointMapper.markSuccess(checkpoint.getId(), workerId,
                LocalDateTime.now(clock), pageNo, updateTime) != 1) {
            throw new LeaseLostException();
        }
    }

    private PageStats persistPage(Long connectionId, List<ProviderDramaRecord> records) {
        List<ProviderDramaRecord> safeRecords = records == null ? List.of() : records;
        int inserted = 0;
        int updated = 0;
        for (ProviderDramaRecord record : safeRecords) {
                ProviderDrama existing = dramaMapper.findByConnectionAndExternalId(connectionId,
                        record.externalDramaId());
                ProviderDrama drama = toEntity(connectionId, record);
                dramaMapper.upsert(drama);
                ProviderDrama stored = existing == null
                        ? dramaMapper.findByConnectionAndExternalId(connectionId, record.externalDramaId()) : existing;
                if (stored == null || stored.getId() == null) {
                    throw new IllegalStateException("Persisted drama cannot be reloaded");
                }
                record.contents().forEach(content -> {
                    ProviderDramaContent entity = new ProviderDramaContent();
                    entity.setDramaId(stored.getId());
                    entity.setExternalContentId(content.externalContentId());
                    entity.setSequenceNo(content.sequenceNo());
                    entity.setTitle(content.title());
                    entity.setFree(content.free());
                    entity.setDurationSeconds(content.durationSeconds());
                    entity.setRemoteUpdatedAt(content.remoteUpdatedAt());
                    dramaMapper.upsertContent(entity);
                });
            if (existing == null) inserted++; else updated++;
        }
        return new PageStats(safeRecords.size(), safeRecords.size(), inserted, updated, 0);
    }

    private ProviderDrama toEntity(Long connectionId, ProviderDramaRecord record) {
        ProviderDrama drama = new ProviderDrama();
        drama.setConnectionId(connectionId);
        drama.setExternalDramaId(record.externalDramaId());
        drama.setTitle(record.title());
        drama.setOriginalTitle(record.originalTitle());
        drama.setTitleZh(record.titleZh());
        drama.setDescription(record.description());
        drama.setCoverUrl(record.coverUrl());
        drama.setLabelNames(writeLabels(record.labelNames()));
        drama.setCategoryName(record.categoryName());
        drama.setLanguage(record.language());
        drama.setRemoteRank(record.remoteRank());
        drama.setDramaType(record.dramaType());
        drama.setNovelType(record.novelType());
        drama.setNovelSubType(record.novelSubType());
        drama.setRemoteShowStatus(record.remoteShowStatus());
        drama.setRemoteCreatedAt(record.remoteCreatedAt());
        drama.setRemoteUpdatedAt(record.remoteUpdatedAt());
        drama.setLastSeenAt(LocalDateTime.now(clock));
        return drama;
    }

    private String writeLabels(List<String> labels) {
        try {
            return objectMapper.writeValueAsString(labels == null ? List.of() : labels);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("Drama labels cannot be serialized", exception);
        }
    }

    private DramaSyncType effectiveType(Long connectionId, DramaSyncType requestedType, String language) {
        if (requestedType == DramaSyncType.FULL) return DramaSyncType.FULL;
        ProviderSyncCheckpoint full = checkpointMapper.find(connectionId, DramaSyncType.FULL, language);
        return full != null && full.getStatus() == DramaSyncStatus.SUCCESS && full.getLastSuccessAt() != null
                ? DramaSyncType.INCREMENTAL : DramaSyncType.FULL;
    }

    private ProviderSyncCheckpoint ensureCheckpoint(Long connectionId, DramaSyncType syncType, String language) {
        ProviderSyncCheckpoint checkpoint = checkpointMapper.find(connectionId, syncType, language);
        if (checkpoint != null) return checkpoint;
        ProviderSyncCheckpoint created = new ProviderSyncCheckpoint();
        created.setConnectionId(connectionId);
        created.setSyncType(syncType);
        created.setLanguage(language);
        created.setStatus(DramaSyncStatus.IDLE);
        created.setPageNo(1);
        created.setPageSize(properties.getPageSize());
        checkpointMapper.insert(created);
        ProviderSyncCheckpoint stored = checkpointMapper.find(connectionId, syncType, language);
        return stored == null ? created : stored;
    }

    private List<String> normalizeLanguages(List<String> requested) {
        List<String> source = requested == null || requested.isEmpty() ? properties.getLanguages() : requested;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (source != null) source.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT)).forEach(normalized::add);
        if (normalized.isEmpty()) normalized.add("ENGLISH");
        return List.copyOf(normalized);
    }

    private boolean usable(ProviderRuntimeConnection runtime) {
        return runtime != null && runtime.secret() != null
                && !blank(runtime.secret().getBaseUrl())
                && !blank(runtime.secret().getPartnerId())
                && !blank(runtime.secret().getApiKey());
    }

    private void markConnectionNotReady(ProviderSyncCheckpoint checkpoint, LocalDateTime now) {
        markFailure(checkpoint, now, "CONNECTION_NOT_READY",
                "Provider connection is not ready for catalog synchronization");
    }

    private void markFailure(ProviderSyncCheckpoint checkpoint, LocalDateTime now,
                             String errorCode, String errorMessage) {
        if (checkpointMapper.markFailure(checkpoint.getId(), workerId, now, errorCode, errorMessage) != 1) {
            throw new LeaseLostException();
        }
    }

    private ProviderCapability capability(DramaSyncType type) {
        return type == DramaSyncType.FULL
                ? ProviderCapability.FULL_DRAMA_SYNC : ProviderCapability.INCREMENTAL_DRAMA_SYNC;
    }

    private int value(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    private record PageStats(int fetched, int upserted, int inserted, int updated, int skipped) {}

    private static final class LeaseLostException extends RuntimeException {
        private LeaseLostException() {
            super("Drama sync lease is no longer owned by this worker");
        }
    }
}

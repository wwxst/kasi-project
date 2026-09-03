package com.kasi.backend.drama.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.drama.config.DramaContentSyncProperties;
import com.kasi.backend.drama.entity.DramaContentSyncTask;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.entity.ProviderDramaContent;
import com.kasi.backend.drama.enums.DramaContentSyncStatus;
import com.kasi.backend.drama.mapper.DramaContentSyncTaskMapper;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.drama.enums.DramaSyncDomain;
import com.kasi.backend.drama.enums.DramaSyncTaskType;
import com.kasi.backend.drama.enums.SyncTriggerSource;
import com.kasi.backend.drama.entity.DramaSyncDisplayRun;
import com.kasi.backend.drama.service.DramaSyncDisplayRunService;
import com.kasi.backend.drama.service.DramaContentSyncService;
import com.kasi.backend.drama.service.DramaMediaUrlValidator;
import com.kasi.backend.drama.vo.DramaContentSyncBatchVO;
import com.kasi.backend.drama.vo.DramaContentSyncTaskVO;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.exception.ProviderRemoteRejectedException;
import com.kasi.backend.provider.exception.ProviderTransientException;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.FreeContentProviderAdapter;
import com.kasi.backend.provider.spi.FreeContentResult;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DramaContentSyncServiceImpl implements DramaContentSyncService {
    private static final Logger log = LoggerFactory.getLogger(DramaContentSyncServiceImpl.class);
    private static final Pattern TRAILING_NUMBER = Pattern.compile("(\\d+)\\s*$");

    private final DramaContentSyncTaskMapper taskMapper;
    private final ProviderDramaMapper dramaMapper;
    private final ShortDramaConnectionMapper connectionMapper;
    private final ProviderRuntimeConnectionService runtimeService;
    private final DramaMediaUrlValidator urlValidator;
    private final TransactionTemplate transactionTemplate;
    private final DramaContentSyncProperties properties;
    private final Clock clock;
    private final String workerId;
    private final TaskExecutor taskExecutor;
    private final DramaSyncDisplayRunService displayRunService;

    public DramaContentSyncServiceImpl(DramaContentSyncTaskMapper taskMapper,
                                       ProviderDramaMapper dramaMapper,
                                       ShortDramaConnectionMapper connectionMapper,
                                       ProviderRuntimeConnectionService runtimeService,
                                       DramaMediaUrlValidator urlValidator,
                                       PlatformTransactionManager transactionManager,
                                       DramaContentSyncProperties properties,
                                       Clock clock,
                                       @Value("${app.promotion.drama.content-sync.worker-id:${random.uuid}}")
                                       String workerId,
                                       @Qualifier("dramaSyncTaskExecutor") TaskExecutor taskExecutor,
                                       DramaSyncDisplayRunService displayRunService) {
        this.taskMapper = taskMapper;
        this.dramaMapper = dramaMapper;
        this.connectionMapper = connectionMapper;
        this.runtimeService = runtimeService;
        this.urlValidator = urlValidator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.properties = properties;
        this.clock = clock;
        this.workerId = workerId;
        this.taskExecutor = taskExecutor;
        this.displayRunService = displayRunService;
    }

    @Override
    @Transactional
    public DramaContentSyncTaskVO request(Long dramaId) {
        ProviderDrama drama = dramaMapper.findById(dramaId);
        if (drama == null) throw new BusinessException(ErrorCode.DRAMA_NOT_FOUND);
        DramaContentSyncTask existing = taskMapper.findByDramaId(dramaId);
        if (existing != null && existing.getStatus() == DramaContentSyncStatus.RUNNING) {
            throw new BusinessException(ErrorCode.DRAMA_CONTENT_SYNC_TASK_RUNNING);
        }
        LocalDateTime requestedAt = now();
        taskMapper.request(dramaId, requestedAt);
        DramaContentSyncTask stored = taskMapper.findByDramaId(dramaId);
        if (stored == null) throw new BusinessException(ErrorCode.DRAMA_CONTENT_SYNC_TASK_NOT_FOUND);
        if (stored.getStatus() == DramaContentSyncStatus.RUNNING) {
            throw new BusinessException(ErrorCode.DRAMA_CONTENT_SYNC_TASK_RUNNING);
        }
        Long providerId = providerId(drama);
        if (providerId != null) {
            DramaSyncDisplayRun run = displayRunService.createRun(providerId, null, DramaSyncDomain.CONTENT,
                    DramaSyncTaskType.SINGLE, SyncTriggerSource.MANUAL, requestedAt);
            displayRunService.attachTask(run.getId(), DramaSyncDomain.CONTENT, stored.getId());
        }
        triggerAfterCommit();
        return DramaContentSyncTaskVO.from(stored);
    }

    @Override
    @Transactional
    public DramaContentSyncBatchVO requestBatch(List<Long> dramaIds) {
        List<Long> ids = dramaIds == null ? List.of() : List.copyOf(new LinkedHashSet<>(dramaIds));
        BatchAccumulator result = new BatchAccumulator(ids.size());
        Map<Long, DramaSyncDisplayRun> runs = new HashMap<>();
        LocalDateTime requestedAt = now();
        ids.forEach(id -> enqueueOne(id, result, runs, requestedAt));
        if (result.queuedCount > 0) triggerAfterCommit();
        return result.toVO();
    }

    @Override
    public DramaContentSyncBatchVO requestAll(Long providerId, String language, boolean missingOnly) {
        String normalizedLanguage = language == null || language.isBlank()
                ? null : language.trim().toUpperCase(Locale.ROOT);
        BatchAccumulator result = new BatchAccumulator(0);
        DramaSyncDisplayRun run = null;
        LocalDateTime requestedAt = now();
        long afterId = 0L;
        while (true) {
            List<Long> ids = dramaMapper.findContentSyncCandidateIds(providerId, normalizedLanguage,
                    missingOnly, afterId, properties.getCandidatePageSize());
            if (ids == null || ids.isEmpty()) break;
            result.requestedCount += ids.size();
            DramaSyncDisplayRun currentRun = run;
            run = transactionTemplate.execute(status -> {
                DramaSyncDisplayRun runInTransaction = currentRun;
                if (runInTransaction == null) {
                    runInTransaction = displayRunService.createRun(providerId, null, DramaSyncDomain.CONTENT,
                            missingOnly ? DramaSyncTaskType.MISSING : DramaSyncTaskType.ALL,
                            SyncTriggerSource.MANUAL, requestedAt);
                }
                final DramaSyncDisplayRun finalRun = runInTransaction;
                Map<Long, DramaSyncDisplayRun> runs = new HashMap<>();
                if (finalRun != null) runs.put(providerId, finalRun);
                ids.forEach(id -> enqueueOne(id, result, runs, requestedAt));
                return runInTransaction;
            });
            afterId = ids.getLast();
            if (ids.size() < properties.getCandidatePageSize()) break;
        }
        if (result.queuedCount > 0) triggerAfterCommit();
        return result.toVOWithoutTasks();
    }

    @Override
    @Transactional(readOnly = true)
    public DramaContentSyncTaskVO getStatus(Long dramaId) {
        DramaContentSyncTask task = taskMapper.findByDramaId(dramaId);
        if (task == null) throw new BusinessException(ErrorCode.DRAMA_CONTENT_SYNC_TASK_NOT_FOUND);
        return DramaContentSyncTaskVO.from(task);
    }

    @Override
    @Transactional
    public void requestAutomatic(Long dramaId) {
        requestAutomatic(dramaId, null);
    }

    @Override
    @Transactional
    public void requestAutomatic(Long dramaId, String displayRunId) {
        taskMapper.request(dramaId, now());
        if (displayRunId != null) {
            DramaContentSyncTask stored = taskMapper.findByDramaId(dramaId);
            if (stored != null) {
                displayRunService.attachTask(displayRunId, DramaSyncDomain.CONTENT, stored.getId());
            }
        }
    }

    @Override
    public void processDueBatch() {
        LocalDateTime now = now();
        List<Long> dueIds = taskMapper.findDueIds(now, properties.getBatchSize());
        if (dueIds == null) return;
        for (Long id : dueIds) {
            if (taskMapper.claimLease(id, workerId, now, now.plus(properties.getLeaseDuration())) == 1) {
                processClaimed(id);
            }
        }
        if (dueIds.size() >= properties.getBatchSize()) {
            try {
                taskExecutor.execute(this::processDueBatch);
            } catch (RuntimeException exception) {
                log.warn("Failed to submit next free content synchronization batch", exception);
            }
        }
    }

    private void processClaimed(Long taskId) {
        DramaContentSyncTask task = taskMapper.findById(taskId);
        if (task == null) return;
        try {
            ProviderDrama drama = dramaMapper.findById(task.getDramaId());
            if (drama == null) {
                finalFailure(task, "DRAMA_NOT_FOUND", "Drama does not exist");
                return;
            }
            ShortDramaConnection connection = connectionMapper.findById(drama.getConnectionId());
            if (connection == null) {
                finalFailure(task, "CONNECTION_NOT_READY", "Provider connection is not ready");
                return;
            }
            ProviderRuntimeConnection runtime = runtimeService.resolve(
                    connection.getProviderId(), ProviderCapability.FREE_CONTENT_PREVIEW);
            if (!runtime.connectionId().equals(connection.getId())
                    || !(runtime.adapter() instanceof FreeContentProviderAdapter adapter)) {
                finalFailure(task, "CAPABILITY_UNSUPPORTED", "Provider does not support free content");
                return;
            }
            List<FreeContentResult> remote = adapter.fetchFreeContent(
                    runtime.secret(), drama.getExternalDramaId());
            if (remote == null) {
                finalFailure(task, "REMOTE_REJECTED", "Provider returned no free content");
                return;
            }
            if (remote.stream().anyMatch(item -> item == null
                    || !urlValidator.isAllowed(item.contentUrl(), connection.getMediaRootDomain()))) {
                finalFailure(task, "INVALID_MEDIA_URL", "GoodShort returned an invalid media URL");
                return;
            }
            persistAndComplete(task, remote);
        } catch (ProviderTransientException exception) {
            retry(task, "REMOTE_TRANSIENT", safeMessage(exception));
        } catch (ProviderRemoteRejectedException exception) {
            finalFailure(task, "REMOTE_REJECTED", safeMessage(exception));
        } catch (BusinessException exception) {
            finalFailure(task, businessFailureCode(exception.getCode()), safeMessage(exception));
        } catch (LeaseLostException ignored) {
            // A newer worker owns the task.
        } catch (RuntimeException exception) {
            try {
                finalFailure(task, "TASK_ERROR", safeMessage(exception));
            } catch (LeaseLostException ignored) {
                // A newer worker owns the task.
            }
            throw exception;
        }
    }

    private void persistAndComplete(DramaContentSyncTask task, List<FreeContentResult> remote) {
        transactionTemplate.executeWithoutResult(status -> {
            Map<Integer, ProviderDramaContent> existing = new HashMap<>();
            dramaMapper.findContents(task.getDramaId()).forEach(content ->
                    existing.put(content.getSequenceNo(), content));
            Set<Integer> used = new HashSet<>();
            int inserted = 0;
            int updated = 0;
            for (int index = 0; index < remote.size(); index++) {
                FreeContentResult item = remote.get(index);
                int sequence = sequence(item.chapterName(), index + 1, used);
                used.add(sequence);
                ProviderDramaContent old = existing.get(sequence);
                ProviderDramaContent content = new ProviderDramaContent();
                content.setDramaId(task.getDramaId());
                content.setSequenceNo(sequence);
                content.setTitle(item.chapterName());
                content.setFree(true);
                content.setContentUrl(item.contentUrl());
                if (old != null) {
                    content.setExternalContentId(old.getExternalContentId());
                    content.setDurationSeconds(old.getDurationSeconds());
                    content.setRemoteUpdatedAt(old.getRemoteUpdatedAt());
                    updated++;
                } else {
                    inserted++;
                }
                dramaMapper.upsertContent(content);
            }
            if (taskMapper.markSuccess(task.getId(), workerId, remote.size(), inserted, updated) != 1) {
                throw new LeaseLostException();
            }
        });
    }

    private int sequence(String title, int fallback, Set<Integer> used) {
        if (title != null) {
            Matcher matcher = TRAILING_NUMBER.matcher(title.trim());
            if (matcher.find()) {
                try {
                    int parsed = Integer.parseInt(matcher.group(1));
                    if (parsed > 0 && !used.contains(parsed)) return parsed;
                } catch (NumberFormatException ignored) {
                    // Use the deterministic fallback below.
                }
            }
        }
        int candidate = Math.max(1, fallback);
        while (used.contains(candidate)) candidate++;
        return candidate;
    }

    private void enqueueOne(Long dramaId, BatchAccumulator result,
                            Map<Long, DramaSyncDisplayRun> runs,
                            LocalDateTime requestedAt) {
        ProviderDrama drama = dramaMapper.findById(dramaId);
        if (drama == null) {
            result.invalidCount++;
            return;
        }
        DramaContentSyncTask existing = taskMapper.findByDramaId(dramaId);
        if (existing != null && existing.getStatus() == DramaContentSyncStatus.RUNNING) {
            result.skippedCount++;
            result.tasks.add(DramaContentSyncTaskVO.from(existing));
            return;
        }
        taskMapper.request(dramaId, now());
        DramaContentSyncTask stored = taskMapper.findByDramaId(dramaId);
        if (stored != null && stored.getStatus() == DramaContentSyncStatus.RUNNING) {
            result.skippedCount++;
            result.tasks.add(DramaContentSyncTaskVO.from(stored));
            return;
        }
        if (stored == null) {
            result.invalidCount++;
            return;
        }
        result.queuedCount++;
        result.tasks.add(DramaContentSyncTaskVO.from(stored));
        Long providerId = providerId(drama);
        if (providerId != null) {
            DramaSyncDisplayRun run = runs.get(providerId);
            if (run == null) {
                run = displayRunService.createRun(providerId, null, DramaSyncDomain.CONTENT,
                        DramaSyncTaskType.BATCH, SyncTriggerSource.MANUAL, requestedAt);
                runs.put(providerId, run);
            }
            displayRunService.attachTask(run.getId(), DramaSyncDomain.CONTENT, stored.getId());
        }
    }

    private Long providerId(ProviderDrama drama) {
        if (drama == null) return null;
        if (drama.getProviderId() != null) return drama.getProviderId();
        ShortDramaConnection connection = connectionMapper.findById(drama.getConnectionId());
        return connection == null ? null : connection.getProviderId();
    }

    private void retry(DramaContentSyncTask task, String code, String message) {
        int retryCount = value(task.getRetryCount()) + 1;
        if (retryCount >= properties.getMaxRetries()) {
            markFailed(task, retryCount, code, message);
            return;
        }
        Duration delay = retryDelay(retryCount);
        if (taskMapper.recordRetry(task.getId(), workerId, now().plus(delay), retryCount, code, message) != 1) {
            throw new LeaseLostException();
        }
    }

    private void finalFailure(DramaContentSyncTask task, String code, String message) {
        markFailed(task, value(task.getRetryCount()) + 1, code, message);
    }

    private void markFailed(DramaContentSyncTask task, int retryCount, String code, String message) {
        if (taskMapper.markFailed(task.getId(), workerId, retryCount, code, message) != 1) {
            throw new LeaseLostException();
        }
    }

    private Duration retryDelay(int retryCount) {
        List<Duration> delays = properties.getRetryDelays();
        return delays.get(Math.min(retryCount - 1, delays.size() - 1));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    private String businessFailureCode(int code) {
        if (code == ErrorCode.PROVIDER_CAPABILITY_UNSUPPORTED.getCode()) {
            return "CAPABILITY_UNSUPPORTED";
        }
        return "CONNECTION_NOT_READY";
    }

    private void triggerAfterCommit() {
        Runnable trigger = () -> {
            try {
                taskExecutor.execute(this::processDueBatch);
            } catch (RuntimeException exception) {
                log.warn("Failed to submit immediate drama content synchronization", exception);
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

    private static final class BatchAccumulator {
        private int requestedCount;
        private int queuedCount;
        private int skippedCount;
        private int invalidCount;
        private final List<DramaContentSyncTaskVO> tasks = new ArrayList<>();

        private BatchAccumulator(int requestedCount) {
            this.requestedCount = requestedCount;
        }

        private DramaContentSyncBatchVO toVO() {
            return new DramaContentSyncBatchVO(requestedCount, queuedCount, skippedCount, invalidCount, tasks);
        }

        private DramaContentSyncBatchVO toVOWithoutTasks() {
            return new DramaContentSyncBatchVO(requestedCount, queuedCount, skippedCount, invalidCount, List.of());
        }
    }

    private static final class LeaseLostException extends RuntimeException {
    }
}

package com.kasi.backend.drama.service;

import com.kasi.backend.drama.entity.DramaSyncDisplayRun;
import com.kasi.backend.drama.enums.DramaSyncDomain;
import com.kasi.backend.drama.enums.DramaSyncTaskType;
import com.kasi.backend.drama.enums.SyncTriggerSource;

import java.time.LocalDateTime;

public interface DramaSyncDisplayRunService {
    DramaSyncDisplayRun createRun(Long providerId, String parentRunId, DramaSyncDomain domain,
                                  DramaSyncTaskType taskType, SyncTriggerSource triggerSource,
                                  LocalDateTime requestedAt);

    DramaSyncDisplayRun createChildRun(DramaSyncDisplayRun parent, Long providerId,
                                       DramaSyncDomain domain, DramaSyncTaskType taskType,
                                       LocalDateTime requestedAt);

    void attachTask(String runId, DramaSyncDomain domain, Long taskId);

    void updateTaskType(String runId, DramaSyncTaskType taskType);
}

package com.kasi.backend.drama.entity;

import com.kasi.backend.drama.enums.DramaSyncDomain;
import com.kasi.backend.drama.enums.DramaSyncTaskType;
import com.kasi.backend.drama.enums.SyncTriggerSource;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DramaSyncDisplayRun {
    private String id;
    private Long providerId;
    private String parentRunId;
    private DramaSyncDomain domain;
    private DramaSyncTaskType taskType;
    private SyncTriggerSource triggerSource;
    private LocalDateTime requestedAt;
    private LocalDateTime createdAt;
}

package com.kasi.backend.drama.vo;

import com.kasi.backend.drama.enums.DramaSyncTaskType;
import com.kasi.backend.drama.enums.SyncRecordStatus;
import com.kasi.backend.drama.enums.SyncTriggerSource;

import java.time.LocalDateTime;

public record DramaSyncRecordVO(
        String id,
        LocalDateTime createdAt,
        SyncTriggerSource triggerSource,
        DramaSyncTaskType taskType,
        SyncRecordStatus status,
        int insertedCount,
        int updatedCount,
        int totalProcessed) {
}

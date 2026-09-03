package com.kasi.backend.drama.vo;

import com.kasi.backend.drama.enums.DramaContentSyncStatus;

public record DramaContentSyncRecordDetailVO(
        Long taskId,
        Long dramaId,
        String dramaTitle,
        String language,
        DramaContentSyncStatus status,
        int retryCount,
        int insertedCount,
        int updatedCount,
        int totalProcessed,
        String lastErrorCode,
        String lastErrorMessage) {
}

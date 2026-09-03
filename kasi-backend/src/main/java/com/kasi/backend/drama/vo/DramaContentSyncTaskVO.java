package com.kasi.backend.drama.vo;

import com.kasi.backend.drama.entity.DramaContentSyncTask;
import com.kasi.backend.drama.enums.DramaContentSyncStatus;

import java.time.LocalDateTime;

public record DramaContentSyncTaskVO(
        Long id,
        Long dramaId,
        DramaContentSyncStatus status,
        LocalDateTime requestedAt,
        LocalDateTime nextRunAt,
        int retryCount,
        int totalFetched,
        int insertedCount,
        int updatedCount,
        String lastErrorCode,
        String lastErrorMessage) {

    public static DramaContentSyncTaskVO from(DramaContentSyncTask task) {
        return new DramaContentSyncTaskVO(task.getId(), task.getDramaId(), task.getStatus(),
                task.getRequestedAt(), task.getNextRunAt(), value(task.getRetryCount()),
                value(task.getTotalFetched()), value(task.getInsertedCount()), value(task.getUpdatedCount()),
                task.getLastErrorCode(), task.getLastErrorMessage());
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}

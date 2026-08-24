package com.kasi.backend.drama.vo;

import com.kasi.backend.drama.enums.DramaSyncStatus;
import com.kasi.backend.drama.enums.DramaSyncType;

import java.time.LocalDateTime;

public record DramaSyncStatusVO(
        Long id,
        DramaSyncType syncType,
        String language,
        DramaSyncStatus status,
        int pageNo,
        Long updateTime,
        int totalFetched,
        int totalUpserted,
        int insertedCount,
        int updatedCount,
        int skippedCount,
        int errorCount,
        LocalDateTime lastSuccessAt,
        String lastErrorCode,
        String lastErrorMessage) {
    public static DramaSyncStatusVO from(DramaSyncTaskVO task) {
        return new DramaSyncStatusVO(task.id(), task.syncType(), task.language(), task.status(), task.pageNo(),
                task.updateTime(), task.totalFetched(), task.totalUpserted(),
                task.insertedCount(), task.updatedCount(), task.skippedCount(), task.errorCount(), task.lastSuccessAt(),
                task.lastErrorCode(), task.lastErrorMessage());
    }
}

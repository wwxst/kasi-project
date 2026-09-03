package com.kasi.backend.drama.vo;

import com.kasi.backend.drama.enums.DramaSyncStatus;
import com.kasi.backend.drama.enums.DramaSyncType;

public record DramaSyncRecordDetailVO(
        Long taskId,
        String language,
        DramaSyncType syncType,
        DramaSyncStatus status,
        int pageNo,
        int insertedCount,
        int updatedCount,
        int totalProcessed,
        String lastErrorCode,
        String lastErrorMessage) {
}

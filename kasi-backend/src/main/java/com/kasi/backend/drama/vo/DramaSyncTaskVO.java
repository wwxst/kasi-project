package com.kasi.backend.drama.vo;

import com.kasi.backend.drama.entity.ProviderSyncCheckpoint;
import com.kasi.backend.drama.enums.DramaSyncStatus;
import com.kasi.backend.drama.enums.DramaSyncType;

import java.time.LocalDateTime;

public record DramaSyncTaskVO(
        Long id,
        DramaSyncType syncType,
        String language,
        DramaSyncStatus status,
        int pageNo,
        Long updateTime,
        int totalFetched,
        int totalUpserted,
        LocalDateTime lastSuccessAt,
        String lastErrorCode,
        String lastErrorMessage) {

    public static DramaSyncTaskVO from(ProviderSyncCheckpoint checkpoint) {
        return new DramaSyncTaskVO(checkpoint.getId(), checkpoint.getSyncType(), checkpoint.getLanguage(),
                checkpoint.getStatus(), value(checkpoint.getPageNo()), checkpoint.getUpdateTime(),
                value(checkpoint.getTotalFetched()), value(checkpoint.getTotalUpserted()),
                checkpoint.getLastSuccessAt(), checkpoint.getLastErrorCode(), checkpoint.getLastErrorMessage());
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}

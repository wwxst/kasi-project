package com.kasi.backend.drama.vo;

import java.util.List;

public record DramaContentSyncBatchVO(
        int requestedCount,
        int queuedCount,
        int skippedCount,
        int invalidCount,
        List<DramaContentSyncTaskVO> tasks) {

    public DramaContentSyncBatchVO {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}

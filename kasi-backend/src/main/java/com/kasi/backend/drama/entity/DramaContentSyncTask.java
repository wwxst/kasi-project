package com.kasi.backend.drama.entity;

import com.kasi.backend.drama.enums.DramaContentSyncStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DramaContentSyncTask {
    private Long id;
    private Long dramaId;
    private DramaContentSyncStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime nextRunAt;
    private Integer retryCount;
    private Integer totalFetched;
    private Integer insertedCount;
    private Integer updatedCount;
    private String lastErrorCode;
    private String lastErrorMessage;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

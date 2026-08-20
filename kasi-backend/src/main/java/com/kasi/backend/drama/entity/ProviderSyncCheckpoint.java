package com.kasi.backend.drama.entity;

import com.kasi.backend.drama.enums.DramaSyncStatus;
import com.kasi.backend.drama.enums.DramaSyncType;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProviderSyncCheckpoint {
    private Long id;
    private Long connectionId;
    private DramaSyncType syncType;
    private String language;
    private DramaSyncStatus status;
    private Integer pageNo;
    private Integer pageSize;
    private LocalDateTime updateTime;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime requestedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer totalFetched;
    private Integer totalUpserted;
    private String lastErrorCode;
    private String lastErrorMessage;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

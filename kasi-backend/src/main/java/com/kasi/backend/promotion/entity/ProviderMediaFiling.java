package com.kasi.backend.promotion.entity;

import com.kasi.backend.promotion.enums.FilingAction;
import com.kasi.backend.promotion.enums.FilingStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProviderMediaFiling {
    private Long id;
    private Long connectionId;
    private Long mediaAccountId;
    private FilingStatus status;
    private Integer submittedDataVersion;
    private Integer taskDataVersion;
    private String remoteStatus;
    private String externalFilingId;
    private LocalDateTime filingTime;
    private LocalDateTime operateTime;
    private Long operateBy;
    private FilingAction nextAction;
    private LocalDateTime nextActionAt;
    private Integer retryCount;
    private LocalDateTime lastSubmittedAt;
    private LocalDateTime lastQueriedAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
}

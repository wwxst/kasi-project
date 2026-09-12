package com.kasi.backend.promotion.vo;

import com.kasi.backend.promotion.enums.FilingMethod;
import com.kasi.backend.promotion.enums.FilingStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminMediaFilingVO {
    private Long providerId;
    private String providerName;
    private FilingMethod filingMethod;
    private FilingStatus status;
    private String remoteStatus;
    private String externalFilingId;
    private LocalDateTime filingTime;
    private LocalDateTime operateTime;
    private LocalDateTime lastSubmittedAt;
    private LocalDateTime lastQueriedAt;
    private LocalDateTime nextActionAt;
    private Long manualUpdatedBy;
    private LocalDateTime manualUpdatedAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private boolean retryAllowed;
    private boolean submissionResolutionAllowed;
}

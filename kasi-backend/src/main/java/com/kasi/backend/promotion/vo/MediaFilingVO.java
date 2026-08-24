package com.kasi.backend.promotion.vo;

import com.kasi.backend.promotion.enums.FilingStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MediaFilingVO {
    private Long providerId;
    private String providerName;
    private FilingStatus status;
    private String remoteStatus;
    private String externalFilingId;
    private LocalDateTime filingTime;
    private LocalDateTime operateTime;
    private Long operateBy;
    private LocalDateTime lastSubmittedAt;
    private LocalDateTime lastQueriedAt;
    private LocalDateTime nextActionAt;
    private String lastErrorCode;
    private String lastErrorMessage;
}

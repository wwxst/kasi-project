package com.kasi.backend.provider.spi;

import com.kasi.backend.promotion.enums.FilingStatus;

import java.time.LocalDateTime;

public record AccountFilingResult(
        FilingStatus status,
        String remoteStatus,
        String externalFilingId,
        LocalDateTime filingTime,
        LocalDateTime operateTime) {
}

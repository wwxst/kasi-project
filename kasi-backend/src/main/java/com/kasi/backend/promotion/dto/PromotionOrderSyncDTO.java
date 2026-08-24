package com.kasi.backend.promotion.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;

@Data
public class PromotionOrderSyncDTO {
    @NotNull
    private Long providerId;
    @NotNull
    private LocalDateTime startDate;
    @NotNull
    private LocalDateTime endDate;

    @AssertTrue(message = "同步时间窗口必须正序且不超过31天")
    public boolean isDateRangeValid() {
        if (startDate == null || endDate == null) {
            return true;
        }
        Duration duration = Duration.between(startDate, endDate);
        return !duration.isNegative() && duration.compareTo(Duration.ofDays(31)) <= 0;
    }
}

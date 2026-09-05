package com.kasi.backend.promotion.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PromotionAnalyticalReportSyncDTO {
    @NotNull
    private Long providerId;
    @NotNull
    private LocalDate startDate;
    @NotNull
    private LocalDate endDate;
    private String code;
    private String bookId;
    private String customParams;

    @AssertTrue(message = "同步日期范围必须正序且不超过30天")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null
                || (!endDate.isBefore(startDate) && endDate.toEpochDay() - startDate.toEpochDay() <= 29);
    }
}

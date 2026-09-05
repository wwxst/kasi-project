package com.kasi.backend.promotion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PromotionAnalyticalReportPageQueryDTO {
    @Min(1)
    private int page = 1;
    @Min(1)
    @Max(100)
    private int size = 20;
    private LocalDate startDate;
    private LocalDate endDate;
    private String customParams;
    private String userNo;
    private String bookId;
    private String code;
}

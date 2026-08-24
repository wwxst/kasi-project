package com.kasi.backend.promotion.dto;

import com.kasi.backend.promotion.enums.PromotionAttributionStatus;
import com.kasi.backend.promotion.enums.PromotionOrderStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PromotionOrderPageQueryDTO {
    @Min(1)
    private int page = 1;
    @Min(1)
    @Max(100)
    private int size = 20;
    private Long providerId;
    private Long userId;
    private PromotionOrderStatus status;
    private PromotionAttributionStatus attributionStatus;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}

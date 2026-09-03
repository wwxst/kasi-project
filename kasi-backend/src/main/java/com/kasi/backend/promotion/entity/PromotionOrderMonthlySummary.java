package com.kasi.backend.promotion.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PromotionOrderMonthlySummary {
    private Long paidOrderCount;
    private BigDecimal calculatedCommission;
    private BigDecimal reversedCommission;
}

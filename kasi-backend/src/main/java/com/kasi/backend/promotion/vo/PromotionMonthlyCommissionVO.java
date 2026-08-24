package com.kasi.backend.promotion.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PromotionMonthlyCommissionVO {
    private String month;
    private long paidOrderCount;
    private BigDecimal grossOrderAmount;
    private BigDecimal calculatedCommission;
    private BigDecimal reversedCommission;
    private BigDecimal netCommission;
}

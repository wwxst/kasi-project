package com.kasi.backend.promotion.vo;

import com.kasi.backend.promotion.enums.PromotionOrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class UserPromotionOrderVO {
    private String externalOrderId;
    private String currency;
    private PromotionOrderStatus status;
    private LocalDateTime paidAt;
    private BigDecimal commissionAmount;
}

package com.kasi.backend.promotion.vo;

import com.kasi.backend.promotion.enums.PromotionCommissionStatus;
import com.kasi.backend.promotion.enums.PromotionOrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class UserPromotionOrderVO {
    private Long id;
    private String externalOrderId;
    private BigDecimal orderAmount;
    private String currency;
    private PromotionOrderStatus status;
    private LocalDateTime paidAt;
    private String trackingNo;
    private BigDecimal commissionAmount;
    private PromotionCommissionStatus commissionStatus;
}

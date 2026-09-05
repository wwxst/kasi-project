package com.kasi.backend.promotion.vo;

import com.kasi.backend.promotion.enums.PromotionAttributionStatus;
import com.kasi.backend.promotion.enums.PromotionCommissionStatus;
import com.kasi.backend.promotion.enums.PromotionOrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PromotionOrderVO {
    private Long id;
    private Long providerId;
    private String externalOrderId;
    private String externalDramaId;
    private String searchCode;
    private String channelCode;
    private BigDecimal orderAmount;
    private String currency;
    private PromotionOrderStatus status;
    private LocalDateTime paidAt;
    private String customParams;
    private Long userId;
    private Long dramaId;
    private PromotionAttributionStatus attributionStatus;
    private BigDecimal channelFeeRate;
    private BigDecimal principalFeeRate;
    private BigDecimal principalCommissionRate;
    private BigDecimal downstreamFeeRate;
    private BigDecimal downstreamCommissionRate;
    private BigDecimal commissionAmount;
    private PromotionCommissionStatus commissionStatus;
    private LocalDateTime lastSyncedAt;
}

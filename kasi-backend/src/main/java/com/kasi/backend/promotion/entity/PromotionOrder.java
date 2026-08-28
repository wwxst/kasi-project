package com.kasi.backend.promotion.entity;

import com.kasi.backend.promotion.enums.PromotionAttributionStatus;
import com.kasi.backend.promotion.enums.PromotionCommissionStatus;
import com.kasi.backend.promotion.enums.PromotionOrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PromotionOrder {
    private Long id;
    private Long connectionId;
    private Long providerId;
    private String externalOrderId;
    private String externalUserId;
    private String externalDramaId;
    private String searchCode;
    private String channelCode;
    private String partnerId;
    private Long orderAmountMinor;
    private BigDecimal orderAmount;
    private String currency;
    private String rawStatus;
    private PromotionOrderStatus status;
    private LocalDateTime paidAt;
    private LocalDateTime providerUpdatedAt;
    private String customParams;
    private String trackingNo;
    private Long promotionLinkId;
    private Long userId;
    private Long dramaId;
    private PromotionAttributionStatus attributionStatus;
    private Long ruleHistoryId;
    private BigDecimal channelFeeRate;
    private BigDecimal principalFeeRate;
    private BigDecimal principalCommissionRate;
    private BigDecimal downstreamFeeRate;
    private BigDecimal downstreamCommissionRate;
    private BigDecimal commissionAmount;
    private PromotionCommissionStatus commissionStatus;
    private String rawPayloadJson;
    private LocalDateTime syncStartDate;
    private LocalDateTime syncEndDate;
    private LocalDateTime lastSyncedAt;
    private String lastErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

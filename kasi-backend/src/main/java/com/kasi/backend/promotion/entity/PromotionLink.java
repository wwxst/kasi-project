package com.kasi.backend.promotion.entity;

import com.kasi.backend.promotion.enums.PromotionLinkStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PromotionLink {
    private Long id;
    private Long userId;
    private Long providerId;
    private Long connectionId;
    private Long dramaId;
    private String batchNo;
    private String providerName;
    private String dramaTitle;
    private String mediaType;
    private String linkVariant;
    private String requestKey;
    private String trackingNo;
    private String campaignName;
    private String externalCode;
    private String shareUrl;
    private PromotionLinkStatus status;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.kasi.backend.promotion.vo;

import com.kasi.backend.promotion.enums.PromotionLinkStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PromotionLinkVO {
    private Long id;
    private Long providerId;
    private String providerName;
    private Long dramaId;
    private String dramaTitle;
    private String batchNo;
    private String requestKey;
    private String mediaType;
    private String linkVariant;
    private String campaignName;
    private String trackingNo;
    private String externalCode;
    private String shareUrl;
    private PromotionLinkStatus status;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

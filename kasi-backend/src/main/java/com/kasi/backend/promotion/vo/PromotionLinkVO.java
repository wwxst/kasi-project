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
    private Long mediaAccountId;
    private String mediaType;
    private String mediaAccountName;
    private String campaignName;
    private String trackingNo;
    private String externalCode;
    private String shareUrl;
    private String customParams;
    private String landingType;
    private PromotionLinkStatus status;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

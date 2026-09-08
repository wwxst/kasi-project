package com.kasi.backend.promotion.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminPromotionLinkVO {
    private Long id;
    private String userNo;
    private String nickname;
    private String realName;
    private Long providerId;
    private String providerName;
    private Long dramaId;
    private String dramaTitle;
    private String mediaType;
    private String linkVariant;
    private String campaignName;
    private String trackingNo;
    private String externalCode;
    private String shareUrl;
    private Long clickCount;
    private Long attributedUserCount;
    private Long newRegisteredUserCount;
    private Long newPaidUserCount;
    private Long newMemberUserCount;
    private Long paidUserCount;
    private Long orderCount;
    private LocalDateTime createdAt;
}

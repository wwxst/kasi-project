package com.kasi.backend.promotion.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户端推广任务按 GoodShort 口令（externalCode）聚合后的一行投影。
 * 同一口令的 LANDING/ONELINK 两个变体共用一份转化日报；
 * 跨媒体共用同一口令时 analyticsConflict 为 true，此时不返回媒体和转化指标。
 */
@Data
public class UserPromotionLinkVO {
    private Long id;
    private Long providerId;
    private String providerName;
    private Long dramaId;
    private String dramaTitle;
    private String campaignName;
    private String mediaType;
    private String externalCode;
    private String landingUrl;
    private String oneLinkUrl;
    private boolean analyticsConflict;
    private Long clickCount;
    private Long attributedUserCount;
    private Long newRegisteredUserCount;
    private Long newPaidUserCount;
    private Long newMemberUserCount;
    private Long paidUserCount;
    private Long orderCount;
    private LocalDateTime createdAt;
}

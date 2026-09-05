package com.kasi.backend.promotion.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PromotionAnalyticalReport {
    private Long id;
    private LocalDate reportDate;
    private String pid;
    private String customParams;
    private String bookId;
    private String code;
    private Long clickCount;
    private Long attributedUserCount;
    private Long newRegisteredUserCount;
    private Long newPaidUserCount;
    private Long newMemberUserCount;
    private Long paidUserCount;
    private Long orderCount;
    private BigDecimal orderAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

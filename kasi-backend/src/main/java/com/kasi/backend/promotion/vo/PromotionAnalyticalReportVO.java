package com.kasi.backend.promotion.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PromotionAnalyticalReportVO {
    private Long id;
    private LocalDate reportDate;
    @JsonProperty("pId")
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
}

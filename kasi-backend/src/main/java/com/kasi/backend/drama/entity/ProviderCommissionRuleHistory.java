package com.kasi.backend.drama.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProviderCommissionRuleHistory {
    private Long id;
    private Long providerId;
    private Long ruleId;
    private BigDecimal channelFeeRate;
    private BigDecimal principalFeeRate;
    private BigDecimal principalCommissionRate;
    private BigDecimal downstreamFeeRate;
    private BigDecimal downstreamCommissionRate;
    private Long createdBy;
    private LocalDateTime createdAt;
}

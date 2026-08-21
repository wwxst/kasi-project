package com.kasi.backend.drama.vo;

import com.kasi.backend.drama.enums.CommissionRuleStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ProviderCommissionRuleVO {
    private Long id;
    private Long providerId;
    private BigDecimal channelFeeRate;
    private BigDecimal principalFeeRate;
    private BigDecimal principalCommissionRate;
    private BigDecimal downstreamFeeRate;
    private BigDecimal downstreamCommissionRate;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private CommissionRuleStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

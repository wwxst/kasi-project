package com.kasi.backend.drama.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class ProviderCommissionCalculator {
    public BigDecimal calculate(BigDecimal amount,
                                BigDecimal channelFeeRate,
                                BigDecimal principalFeeRate,
                                BigDecimal principalCommissionRate,
                                BigDecimal downstreamFeeRate,
                                BigDecimal downstreamCommissionRate) {
        BigDecimal result = Objects.requireNonNull(amount)
                .multiply(BigDecimal.ONE.subtract(Objects.requireNonNull(channelFeeRate)))
                .multiply(BigDecimal.ONE.subtract(Objects.requireNonNull(principalFeeRate)))
                .multiply(Objects.requireNonNull(principalCommissionRate))
                .multiply(BigDecimal.ONE.subtract(Objects.requireNonNull(downstreamFeeRate)))
                .multiply(Objects.requireNonNull(downstreamCommissionRate));
        return result.setScale(2, RoundingMode.HALF_UP);
    }
}

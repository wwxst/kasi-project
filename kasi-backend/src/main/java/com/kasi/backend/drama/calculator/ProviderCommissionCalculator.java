package com.kasi.backend.drama.calculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

public class ProviderCommissionCalculator {
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    public BigDecimal calculate(BigDecimal amount,
                                BigDecimal channelFeeRate,
                                BigDecimal principalFeeRate,
                                BigDecimal principalCommissionRate,
                                BigDecimal downstreamFeeRate,
                                BigDecimal downstreamCommissionRate) {
        BigDecimal result = Objects.requireNonNull(amount)
                .multiply(BigDecimal.ONE.subtract(Objects.requireNonNull(channelFeeRate)), MATH_CONTEXT)
                .multiply(BigDecimal.ONE.subtract(Objects.requireNonNull(principalFeeRate)), MATH_CONTEXT)
                .multiply(Objects.requireNonNull(principalCommissionRate), MATH_CONTEXT)
                .multiply(BigDecimal.ONE.subtract(Objects.requireNonNull(downstreamFeeRate)), MATH_CONTEXT)
                .multiply(Objects.requireNonNull(downstreamCommissionRate), MATH_CONTEXT);
        return result.setScale(2, RoundingMode.HALF_UP);
    }
}

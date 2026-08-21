package com.kasi.backend.drama.calculator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("平台分佣计算器")
class ProviderCommissionCalculatorTest {
    private final ProviderCommissionCalculator calculator = new ProviderCommissionCalculator();

    @Test
    @DisplayName("五项费率按顺序计算并最终四舍五入两位")
    void calculateAppliesFiveRatesAndRoundsOnce() {
        assertThat(calculator.calculate(new BigDecimal("100"),
                new BigDecimal("0.30"), BigDecimal.ZERO, new BigDecimal("0.80"),
                BigDecimal.ZERO, new BigDecimal("0.70")))
                .isEqualByComparingTo("39.20");
        assertThat(calculator.calculate(new BigDecimal("10.01"),
                new BigDecimal("0.003"), new BigDecimal("0.001"), BigDecimal.ONE,
                BigDecimal.ZERO, new BigDecimal("0.3333")))
                .isEqualByComparingTo("3.32");
    }

    @Test
    @DisplayName("百分百扣费返回零且零扣费保留完整金额")
    void calculateHandlesZeroAndFullFees() {
        assertThat(calculator.calculate(new BigDecimal("12.34"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE))
                .isEqualByComparingTo("12.34");
        assertThat(calculator.calculate(new BigDecimal("12.34"),
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE))
                .isEqualByComparingTo("0.00");
    }
}

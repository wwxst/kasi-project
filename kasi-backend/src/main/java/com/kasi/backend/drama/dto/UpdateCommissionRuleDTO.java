package com.kasi.backend.drama.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateCommissionRuleDTO {
    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Digits(integer = 3, fraction = 4)
    private BigDecimal channelFeeRate;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Digits(integer = 3, fraction = 4)
    private BigDecimal principalFeeRate;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Digits(integer = 3, fraction = 4)
    private BigDecimal principalCommissionRate;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Digits(integer = 3, fraction = 4)
    private BigDecimal downstreamFeeRate;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Digits(integer = 3, fraction = 4)
    private BigDecimal downstreamCommissionRate;

}

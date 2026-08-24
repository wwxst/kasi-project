package com.kasi.backend.promotion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PromotionOrderMonthQueryDTO {
    @NotBlank
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$")
    private String month;
    @Min(1)
    private int page = 1;
    @Min(1)
    @Max(100)
    private int size = 20;
}

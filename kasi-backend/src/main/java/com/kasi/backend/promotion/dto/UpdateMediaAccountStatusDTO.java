package com.kasi.backend.promotion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMediaAccountStatusDTO {
    @NotNull @Min(0) @Max(1) private Integer status;
}

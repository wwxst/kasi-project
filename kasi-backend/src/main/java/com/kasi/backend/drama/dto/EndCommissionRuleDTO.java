package com.kasi.backend.drama.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EndCommissionRuleDTO {
    @NotNull
    private LocalDateTime effectiveTo;
}

package com.kasi.backend.promotion.dto;

import com.kasi.backend.promotion.enums.FilingStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMediaFilingStatusDTO {
    @NotNull
    private FilingStatus status;
}

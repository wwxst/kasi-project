package com.kasi.backend.provider.dto;

import com.kasi.backend.provider.enums.FilingMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateProviderFilingModeDTO {

    @NotNull
    private FilingMode filingMode;
}

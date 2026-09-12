package com.kasi.backend.promotion.dto;

import com.kasi.backend.promotion.enums.SubmissionResolution;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResolveFilingSubmissionDTO {
    @NotNull
    private SubmissionResolution resolution;
}

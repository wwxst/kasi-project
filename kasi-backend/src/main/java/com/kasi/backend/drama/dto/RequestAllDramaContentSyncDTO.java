package com.kasi.backend.drama.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RequestAllDramaContentSyncDTO {
    @NotNull
    @Positive
    private Long providerId;
    @Size(max = 32)
    private String language;
    private boolean missingOnly;
}

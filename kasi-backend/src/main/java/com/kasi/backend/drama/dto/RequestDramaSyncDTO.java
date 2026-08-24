package com.kasi.backend.drama.dto;

import com.kasi.backend.drama.enums.DramaSyncType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RequestDramaSyncDTO {
    @NotNull
    @Positive
    private Long providerId;
    @NotNull
    private DramaSyncType syncType;
    @Size(max = 20)
    private List<@Valid @NotBlank @Size(max = 32) String> languages;
}

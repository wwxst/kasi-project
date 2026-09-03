package com.kasi.backend.drama.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RequestDramaContentBatchSyncDTO {
    @NotEmpty
    @Size(max = 100)
    private List<@Valid @NotNull @Positive Long> dramaIds;
}

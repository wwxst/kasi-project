package com.kasi.backend.drama.download.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateDramaDownloadTaskDTO {
    @NotEmpty
    @Size(max = 100)
    private List<@NotNull @Positive Long> contentIds;
}

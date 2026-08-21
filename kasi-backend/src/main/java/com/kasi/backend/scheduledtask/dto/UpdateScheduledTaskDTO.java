package com.kasi.backend.scheduledtask.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateScheduledTaskDTO {
    @NotNull
    @Min(5)
    @Max(1440)
    private Integer intervalMinutes;

    @NotBlank
    @Size(max = 255)
    private String description;

    @NotNull
    private Boolean enabled;
}

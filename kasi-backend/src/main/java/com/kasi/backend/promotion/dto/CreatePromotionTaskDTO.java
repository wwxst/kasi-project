package com.kasi.backend.promotion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreatePromotionTaskDTO {
    @NotNull private Long providerId;
    @NotNull private Long dramaId;
    @NotBlank @Size(max = 128) private String taskName;
    @NotBlank @Size(max = 64) private String requestKey;
    @NotEmpty @Size(max = 4) private List<@NotBlank String> mediaTypes;
}

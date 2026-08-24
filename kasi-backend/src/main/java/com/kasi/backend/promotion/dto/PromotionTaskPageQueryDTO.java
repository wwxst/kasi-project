package com.kasi.backend.promotion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PromotionTaskPageQueryDTO {
    @Min(1) private int page = 1;
    @Min(1) @Max(100) private int size = 20;
    @Size(max = 128) private String taskName;
    @Size(max = 255) private String dramaTitle;
    @Size(max = 32) private String mediaType;
}

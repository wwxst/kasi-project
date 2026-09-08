package com.kasi.backend.promotion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AdminPromotionLinkPageQueryDTO {
    @Min(1)
    private int page = 1;
    @Min(1)
    @Max(100)
    private int size = 20;
    private String userNo;
    private Long providerId;
    private String externalCode;
    private String trackingNo;
}

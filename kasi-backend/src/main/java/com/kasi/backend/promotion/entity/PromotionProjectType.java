package com.kasi.backend.promotion.entity;

import com.kasi.backend.promotion.enums.PromotionProjectStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PromotionProjectType {
    private Long id;
    private String code;
    private String name;
    private PromotionProjectStatus status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.kasi.backend.promotion.vo;

import com.kasi.backend.promotion.enums.PromotionProjectStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PromotionProjectTypeVO {
    private Long id;
    private String code;
    private String name;
    private PromotionProjectStatus status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.kasi.backend.promotion.entity;

import com.kasi.backend.promotion.enums.PromotionProjectStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PromotionProject {
    private Long id;
    private String name;
    private String coverImageUrl;
    private String projectDocumentUrl;
    private PromotionProjectStatus status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

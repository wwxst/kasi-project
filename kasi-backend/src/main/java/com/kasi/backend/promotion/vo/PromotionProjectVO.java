package com.kasi.backend.promotion.vo;

import com.kasi.backend.promotion.enums.PromotionProjectStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PromotionProjectVO {
    private Long id;
    private String name;
    private String coverImageUrl;
    private String projectDocumentUrl;
    private PromotionProjectStatus status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

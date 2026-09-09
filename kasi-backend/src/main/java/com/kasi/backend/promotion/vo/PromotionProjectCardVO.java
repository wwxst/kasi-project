package com.kasi.backend.promotion.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PromotionProjectCardVO {
    private Long id;
    private String name;
    private String coverImageUrl;
    private String projectDocumentUrl;
    private Integer sortOrder;
}

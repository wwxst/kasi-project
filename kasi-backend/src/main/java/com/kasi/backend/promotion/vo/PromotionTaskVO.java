package com.kasi.backend.promotion.vo;

import com.kasi.backend.promotion.enums.MediaType;
import com.kasi.backend.promotion.enums.PromotionTaskStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder
public class PromotionTaskVO {
    private Long id; private String taskName; private MediaType mediaType; private String providerName; private String dramaTitle;
    private String trackingNo; private String externalCode; private String directUrl; private PromotionTaskStatus status;
    private String lastErrorMessage; private long codeSearchCount; private long directClickCount; private long appClickCount;
    private long leadCount; private BigDecimal orderAmount; private long orderCount; private BigDecimal adAmount; private LocalDateTime createdAt;
}

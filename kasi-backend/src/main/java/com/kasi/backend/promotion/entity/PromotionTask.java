package com.kasi.backend.promotion.entity;

import com.kasi.backend.promotion.enums.MediaType;
import com.kasi.backend.promotion.enums.PromotionTaskStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PromotionTask {
    private Long id; private Long userId; private Long providerId; private Long connectionId; private Long dramaId;
    private String providerName; private String dramaTitle; private String requestKey; private String taskName;
    private MediaType mediaType; private String trackingNo; private String externalCode; private String directUrl;
    private PromotionTaskStatus status; private String lastErrorCode; private String lastErrorMessage;
    private long codeSearchCount; private long directClickCount; private long appClickCount; private long leadCount;
    private BigDecimal orderAmount; private long orderCount; private BigDecimal adAmount;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
}

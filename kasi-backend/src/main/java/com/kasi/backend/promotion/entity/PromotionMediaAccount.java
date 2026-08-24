package com.kasi.backend.promotion.entity;

import com.kasi.backend.promotion.enums.MediaType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PromotionMediaAccount {
    private Long id;
    private Long userId;
    private MediaType mediaType;
    private String externalAccountId;
    private String accountName;
    private String accountLink;
    private Integer status;
    private Integer dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

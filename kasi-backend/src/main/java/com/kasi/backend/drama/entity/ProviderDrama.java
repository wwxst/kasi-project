package com.kasi.backend.drama.entity;

import com.kasi.backend.drama.enums.DramaLocalStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProviderDrama {
    private Long id;
    private Long connectionId;
    private Long providerId;
    private String providerName;
    private String externalDramaId;
    private String title;
    private String originalTitle;
    private String titleZh;
    private String description;
    private String coverUrl;
    private String labelNames;
    private String categoryName;
    private String language;
    private Integer remoteRank;
    private String dramaType;
    private String novelType;
    private Integer novelSubType;
    private String commissionScope;
    private String promotionDescription;
    private String remoteShowStatus;
    private DramaLocalStatus localStatus;
    private LocalDateTime remoteCreatedAt;
    private LocalDateTime remoteUpdatedAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

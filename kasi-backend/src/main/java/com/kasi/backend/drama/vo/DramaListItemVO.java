package com.kasi.backend.drama.vo;

import com.kasi.backend.drama.enums.DramaLocalStatus;
import com.kasi.backend.drama.enums.PromotionCommissionScope;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DramaListItemVO {
    private Long id;
    private Long providerId;
    private String providerName;
    private String externalDramaId;
    private String title;
    private String originalTitle;
    private String titleZh;
    private String description;
    private String coverUrl;
    private List<String> labelNames;
    private String categoryName;
    private String language;
    private Integer remoteRank;
    private String dramaType;
    private String novelType;
    private Integer novelSubType;
    private List<PromotionCommissionScope> commissionScopes;
    private String promotionDescription;
    private String remoteShowStatus;
    private DramaLocalStatus localStatus;
    private LocalDateTime remoteCreatedAt;
    private LocalDateTime remoteUpdatedAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime updatedAt;
}

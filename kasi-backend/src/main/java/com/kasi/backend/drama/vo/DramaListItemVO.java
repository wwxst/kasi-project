package com.kasi.backend.drama.vo;

import com.kasi.backend.drama.enums.DramaLocalStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DramaListItemVO {
    private Long id;
    private String externalDramaId;
    private String title;
    private String originalTitle;
    private String coverUrl;
    private String language;
    private String dramaType;
    private String remoteShowStatus;
    private DramaLocalStatus localStatus;
    private LocalDateTime remoteUpdatedAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime updatedAt;
}

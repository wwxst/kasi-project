package com.kasi.backend.drama.entity;

import com.kasi.backend.drama.enums.DramaLocalStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProviderDrama {
    private Long id;
    private Long connectionId;
    private String externalDramaId;
    private String title;
    private String originalTitle;
    private String description;
    private String coverUrl;
    private String language;
    private String dramaType;
    private String remoteShowStatus;
    private DramaLocalStatus localStatus;
    private LocalDateTime remoteUpdatedAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

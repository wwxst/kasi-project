package com.kasi.backend.drama.vo;

import com.kasi.backend.drama.enums.DramaLocalStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DramaDetailVO {
    private Long id;
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
    private List<DramaContentVO> contents;
}

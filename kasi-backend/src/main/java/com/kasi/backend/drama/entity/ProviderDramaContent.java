package com.kasi.backend.drama.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProviderDramaContent {
    private Long id;
    private Long dramaId;
    private String externalContentId;
    private Integer sequenceNo;
    private String title;
    private Boolean free;
    private Integer durationSeconds;
    private LocalDateTime remoteUpdatedAt;
}

package com.kasi.backend.drama.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DramaContentVO {
    private Long id;
    private String externalContentId;
    private int sequenceNo;
    private String title;
    private boolean free;
    private Integer durationSeconds;
    private LocalDateTime remoteUpdatedAt;
}

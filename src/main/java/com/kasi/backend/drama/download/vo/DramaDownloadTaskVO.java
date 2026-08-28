package com.kasi.backend.drama.download.vo;

import com.kasi.backend.drama.download.enums.DramaDownloadTaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DramaDownloadTaskVO {
    private Long taskId;
    private DramaDownloadTaskStatus status;
    private Integer totalCount;
    private Integer completedCount;
    private String downloadUrl;
    private String errorMessage;
    private LocalDateTime expiresAt;
}

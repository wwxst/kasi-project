package com.kasi.backend.drama.download.entity;

import com.kasi.backend.drama.download.enums.DramaDownloadTaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DramaDownloadTask {
    private Long id;
    private Long userId;
    private Long dramaId;
    private DramaDownloadTaskStatus status;
    private String contentIdsJson;
    private String filePath;
    private String fileName;
    private Integer totalCount;
    private Integer completedCount;
    private String errorMessage;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.kasi.backend.drama.download.service;

import com.kasi.backend.drama.download.dto.CreateDramaDownloadTaskDTO;
import com.kasi.backend.drama.download.vo.DramaDownloadTaskVO;

import java.nio.file.Path;

public interface DramaDownloadTaskService {
    DramaDownloadTaskVO create(Long userId, Long dramaId, CreateDramaDownloadTaskDTO request);
    DramaDownloadTaskVO get(Long userId, Long taskId);
    Path getFile(Long userId, Long taskId);
    void process(Long taskId);
    int cleanupExpired();
}

package com.kasi.backend.drama.service;

import com.kasi.backend.drama.vo.DramaContentSyncBatchVO;
import com.kasi.backend.drama.vo.DramaContentSyncTaskVO;

import java.util.List;

public interface DramaContentSyncService {
    DramaContentSyncTaskVO request(Long dramaId);
    DramaContentSyncBatchVO requestBatch(List<Long> dramaIds);
    DramaContentSyncBatchVO requestAll(Long providerId, String language, boolean missingOnly);
    DramaContentSyncTaskVO getStatus(Long dramaId);
    void requestAutomatic(Long dramaId);
    void requestAutomatic(Long dramaId, String displayRunId);
    void processDueBatch();
}

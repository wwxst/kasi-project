package com.kasi.backend.drama.service;

import com.kasi.backend.drama.enums.DramaSyncType;
import com.kasi.backend.drama.vo.DramaSyncTaskVO;

import java.util.List;

public interface DramaCatalogSyncService {
    List<DramaSyncTaskVO> requestSync(Long providerId, DramaSyncType syncType, List<String> languages);
    List<DramaSyncTaskVO> getStatuses(Long providerId);
    void processDueBatch();
}

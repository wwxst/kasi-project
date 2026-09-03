package com.kasi.backend.drama.service;

import com.kasi.backend.drama.vo.DramaContentSyncRecordDetailVO;
import com.kasi.backend.drama.vo.DramaSyncRecordDetailVO;
import com.kasi.backend.drama.vo.DramaSyncRecordVO;

import java.util.List;

public interface DramaSyncRecordQueryService {
    List<DramaSyncRecordVO> listCatalog(Long providerId);

    List<DramaSyncRecordVO> listContent(Long providerId);

    List<DramaSyncRecordDetailVO> catalogDetails(Long providerId, String runId);

    List<DramaContentSyncRecordDetailVO> contentDetails(Long providerId, String runId);
}

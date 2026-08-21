package com.kasi.backend.drama.service;

import com.kasi.backend.drama.dto.DramaPageQueryDTO;
import com.kasi.backend.drama.enums.DramaLocalStatus;
import com.kasi.backend.drama.vo.DramaDetailVO;
import com.kasi.backend.drama.vo.DramaPageVO;

public interface DramaCatalogAdminService {
    DramaPageVO getPage(DramaPageQueryDTO query);
    DramaDetailVO getById(Long id);
    DramaDetailVO updateLocalStatus(Long id, DramaLocalStatus localStatus);
}

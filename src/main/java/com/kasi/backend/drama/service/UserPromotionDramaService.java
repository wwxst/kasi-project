package com.kasi.backend.drama.service;

import com.kasi.backend.drama.dto.DramaPageQueryDTO;
import com.kasi.backend.drama.vo.DramaPageVO;

public interface UserPromotionDramaService {
    DramaPageVO getPublished(DramaPageQueryDTO query);
}

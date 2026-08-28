package com.kasi.backend.drama.service;

import com.kasi.backend.drama.dto.DramaPageQueryDTO;
import com.kasi.backend.drama.vo.DramaPageVO;
import com.kasi.backend.drama.vo.DramaDetailVO;
import com.kasi.backend.drama.vo.DramaContentResourceVO;

import java.util.List;

public interface UserPromotionDramaService {
    DramaPageVO getPublished(DramaPageQueryDTO query);
    DramaDetailVO getPublishedDetail(Long id);
    List<DramaContentResourceVO> getFreeContent(Long id);
    List<DramaContentResourceVO> getFreeContent(Long id, boolean refresh);
}

package com.kasi.backend.drama.service;

import com.kasi.backend.drama.dto.DramaPageQueryDTO;
import com.kasi.backend.drama.enums.DramaLocalStatus;
import com.kasi.backend.drama.enums.PromotionCommissionScope;
import com.kasi.backend.drama.vo.DramaDetailVO;
import com.kasi.backend.drama.vo.DramaPageVO;

import java.util.List;

public interface DramaCatalogAdminService {
    DramaPageVO getPage(DramaPageQueryDTO query);
    DramaDetailVO getById(Long id);
    DramaDetailVO updateLocalStatus(Long id, DramaLocalStatus localStatus);
    DramaDetailVO updatePromotionMetadata(Long id, List<PromotionCommissionScope> commissionScopes,
                                           String promotionDescription);
}

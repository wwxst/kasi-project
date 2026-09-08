package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.AdminPromotionLinkPageQueryDTO;
import com.kasi.backend.promotion.vo.AdminPromotionLinkPageVO;

public interface PromotionLinkAdminService {
    AdminPromotionLinkPageVO getPage(AdminPromotionLinkPageQueryDTO query);
}

package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.CreatePromotionLinkDTO;
import com.kasi.backend.promotion.dto.PromotionLinkPageQueryDTO;
import com.kasi.backend.promotion.vo.PromotionLinkPageVO;
import com.kasi.backend.promotion.vo.PromotionLinkVO;

public interface PromotionLinkService {
    PromotionLinkPageVO getMine(Long userId, PromotionLinkPageQueryDTO query);
    PromotionLinkVO createOrRetry(Long userId, CreatePromotionLinkDTO request);
}

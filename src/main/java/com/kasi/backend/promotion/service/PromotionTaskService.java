package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.CreatePromotionTaskDTO;
import com.kasi.backend.promotion.dto.PromotionTaskPageQueryDTO;
import com.kasi.backend.promotion.vo.PromotionTaskPageVO;

public interface PromotionTaskService {
    PromotionTaskPageVO getMine(Long userId, PromotionTaskPageQueryDTO query);
    PromotionTaskPageVO create(Long userId, CreatePromotionTaskDTO request);
}

package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.PromotionOrderMonthQueryDTO;
import com.kasi.backend.promotion.vo.PromotionMonthlyCommissionVO;
import com.kasi.backend.promotion.vo.UserPromotionOrderPageVO;

public interface PromotionOrderUserService {
    UserPromotionOrderPageVO getPage(Long userId, PromotionOrderMonthQueryDTO query);

    PromotionMonthlyCommissionVO getMonthly(Long userId, PromotionOrderMonthQueryDTO query);

    byte[] exportCsv(Long userId, PromotionOrderMonthQueryDTO query);
}

package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.PromotionOrderPageQueryDTO;
import com.kasi.backend.promotion.dto.PromotionOrderSyncDTO;
import com.kasi.backend.promotion.vo.PromotionOrderPageVO;
import com.kasi.backend.promotion.vo.PromotionOrderSyncResultVO;

public interface PromotionOrderAdminService {
    PromotionOrderSyncResultVO sync(PromotionOrderSyncDTO request);

    PromotionOrderPageVO getPage(PromotionOrderPageQueryDTO query);

    byte[] exportCsv(PromotionOrderPageQueryDTO query);
}

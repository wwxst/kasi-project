package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.vo.PromotionOrderSyncResultVO;

import java.time.LocalDateTime;

public interface PromotionOrderSyncService {
    PromotionOrderSyncResultVO sync(Long providerId, LocalDateTime startDate, LocalDateTime endDate);
}

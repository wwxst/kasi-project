package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.vo.PromotionAnalyticalReportSyncResultVO;

import java.time.LocalDate;

public interface PromotionAnalyticalReportSyncService {
    PromotionAnalyticalReportSyncResultVO sync(Long providerId, LocalDate startDate, LocalDate endDate,
                                                String code, String bookId, String customParams);
}

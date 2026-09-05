package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.PromotionAnalyticalReportPageQueryDTO;
import com.kasi.backend.promotion.dto.PromotionAnalyticalReportSyncDTO;
import com.kasi.backend.promotion.vo.PromotionAnalyticalReportPageVO;
import com.kasi.backend.promotion.vo.PromotionAnalyticalReportSyncResultVO;

public interface PromotionAnalyticalReportAdminService {
    PromotionAnalyticalReportSyncResultVO sync(PromotionAnalyticalReportSyncDTO request);
    PromotionAnalyticalReportPageVO getPage(PromotionAnalyticalReportPageQueryDTO query);
}

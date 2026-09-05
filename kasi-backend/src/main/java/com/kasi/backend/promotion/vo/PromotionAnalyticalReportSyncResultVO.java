package com.kasi.backend.promotion.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PromotionAnalyticalReportSyncResultVO {
    private int fetchedCount;
    private int upsertedCount;
}

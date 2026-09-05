package com.kasi.backend.promotion.service;

import com.kasi.backend.provider.spi.ProviderAnalyticalReportRecord;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;

public interface PromotionAnalyticalReportService {
    void upsert(ProviderRuntimeConnection runtime, ProviderAnalyticalReportRecord record);
}

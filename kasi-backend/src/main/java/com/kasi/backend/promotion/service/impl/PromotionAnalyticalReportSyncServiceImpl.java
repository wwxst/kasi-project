package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.service.PromotionAnalyticalReportService;
import com.kasi.backend.promotion.service.PromotionAnalyticalReportSyncService;
import com.kasi.backend.promotion.vo.PromotionAnalyticalReportSyncResultVO;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.AnalyticalReportProviderAdapter;
import com.kasi.backend.provider.spi.AnalyticalReportRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PromotionAnalyticalReportSyncServiceImpl implements PromotionAnalyticalReportSyncService {
    private static final int PAGE_SIZE = 500;
    private final ProviderRuntimeConnectionService runtimeService;
    private final PromotionAnalyticalReportService reportService;

    @Override
    public PromotionAnalyticalReportSyncResultVO sync(Long providerId, LocalDate startDate, LocalDate endDate,
                                                      String code, String bookId, String customParams) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)
                || endDate.toEpochDay() - startDate.toEpochDay() > 29) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        var runtime = runtimeService.resolve(providerId, ProviderCapability.ANALYTICS_SYNC);
        AnalyticalReportProviderAdapter adapter = (AnalyticalReportProviderAdapter) runtime.adapter();
        int pageNo = 1;
        int fetched = 0;
        int insertedOrUpdated = 0;
        boolean hasNext;
        do {
            var page = adapter.fetchAnalyticalReports(runtime.secret(),
                    new AnalyticalReportRequest(startDate, endDate, pageNo, PAGE_SIZE, code, bookId, customParams));
            for (var record : page.records()) {
                reportService.upsert(runtime, record);
                fetched++;
                insertedOrUpdated++;
            }
            hasNext = page.hasNext();
            pageNo++;
        } while (hasNext);
        return PromotionAnalyticalReportSyncResultVO.builder().fetchedCount(fetched)
                .upsertedCount(insertedOrUpdated).build();
    }
}

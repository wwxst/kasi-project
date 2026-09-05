package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.promotion.entity.PromotionAnalyticalReport;
import com.kasi.backend.promotion.mapper.PromotionAnalyticalReportMapper;
import com.kasi.backend.promotion.service.PromotionAnalyticalReportService;
import com.kasi.backend.provider.spi.ProviderAnalyticalReportRecord;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PromotionAnalyticalReportServiceImpl implements PromotionAnalyticalReportService {
    private final PromotionAnalyticalReportMapper mapper;

    @Override
    @Transactional
    public void upsert(ProviderRuntimeConnection runtime, ProviderAnalyticalReportRecord record) {
        PromotionAnalyticalReport report = new PromotionAnalyticalReport();
        report.setReportDate(record.reportDate());
        report.setPid(record.pid());
        report.setCustomParams(record.customParams());
        report.setBookId(record.bookId());
        report.setCode(record.code());
        report.setClickCount(record.clickCount());
        report.setAttributedUserCount(record.attributedUserCount());
        report.setNewRegisteredUserCount(record.newRegisteredUserCount());
        report.setNewPaidUserCount(record.newPaidUserCount());
        report.setNewMemberUserCount(record.newMemberUserCount());
        report.setPaidUserCount(record.paidUserCount());
        report.setOrderCount(record.orderCount());
        report.setOrderAmount(record.orderAmount());
        mapper.upsert(report);
    }
}

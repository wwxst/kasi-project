package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.promotion.dto.PromotionAnalyticalReportPageQueryDTO;
import com.kasi.backend.promotion.dto.PromotionAnalyticalReportSyncDTO;
import com.kasi.backend.promotion.entity.PromotionAnalyticalReport;
import com.kasi.backend.promotion.mapper.PromotionAnalyticalReportMapper;
import com.kasi.backend.promotion.service.PromotionAnalyticalReportAdminService;
import com.kasi.backend.promotion.service.PromotionAnalyticalReportSyncService;
import com.kasi.backend.promotion.vo.PromotionAnalyticalReportPageVO;
import com.kasi.backend.promotion.vo.PromotionAnalyticalReportSyncResultVO;
import com.kasi.backend.promotion.vo.PromotionAnalyticalReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PromotionAnalyticalReportAdminServiceImpl implements PromotionAnalyticalReportAdminService {
    private final PromotionAnalyticalReportSyncService syncService;
    private final PromotionAnalyticalReportMapper mapper;

    @Override
    public PromotionAnalyticalReportSyncResultVO sync(PromotionAnalyticalReportSyncDTO request) {
        return syncService.sync(request.getProviderId(), request.getStartDate(), request.getEndDate(),
                request.getCode(), request.getBookId(), request.getCustomParams());
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionAnalyticalReportPageVO getPage(PromotionAnalyticalReportPageQueryDTO query) {
        String customParams = query.getCustomParams() != null ? query.getCustomParams() : query.getUserNo();
        long total = mapper.countPage(query.getStartDate(), query.getEndDate(), customParams,
                query.getBookId(), query.getCode());
        var list = mapper.findPage(query.getStartDate(), query.getEndDate(), customParams,
                        query.getBookId(), query.getCode(), (query.getPage() - 1) * query.getSize(), query.getSize())
                .stream().map(PromotionAnalyticalReportAdminServiceImpl::toVO).toList();
        return PromotionAnalyticalReportPageVO.builder().list(list).page(query.getPage()).size(query.getSize())
                .total(total).build();
    }

    private static PromotionAnalyticalReportVO toVO(PromotionAnalyticalReport report) {
        return PromotionAnalyticalReportVO.builder().id(report.getId()).reportDate(report.getReportDate())
                .pid(report.getPid()).customParams(report.getCustomParams()).bookId(report.getBookId())
                .code(report.getCode()).clickCount(report.getClickCount())
                .attributedUserCount(report.getAttributedUserCount())
                .newRegisteredUserCount(report.getNewRegisteredUserCount())
                .newPaidUserCount(report.getNewPaidUserCount()).newMemberUserCount(report.getNewMemberUserCount())
                .paidUserCount(report.getPaidUserCount()).orderCount(report.getOrderCount())
                .orderAmount(report.getOrderAmount()).build();
    }
}

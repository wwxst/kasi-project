package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.promotion.dto.PromotionOrderMonthQueryDTO;
import com.kasi.backend.promotion.entity.PromotionOrder;
import com.kasi.backend.promotion.enums.PromotionAttributionStatus;
import com.kasi.backend.promotion.mapper.PromotionOrderMapper;
import com.kasi.backend.promotion.service.PromotionOrderUserService;
import com.kasi.backend.promotion.vo.PromotionMonthlyCommissionVO;
import com.kasi.backend.promotion.vo.UserPromotionOrderPageVO;
import com.kasi.backend.promotion.vo.UserPromotionOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class PromotionOrderUserServiceImpl implements PromotionOrderUserService {
    private final PromotionOrderMapper orderMapper;

    @Override
    @Transactional(readOnly = true)
    public UserPromotionOrderPageVO getPage(Long userId, PromotionOrderMonthQueryDTO query) {
        YearMonth month = YearMonth.parse(query.getMonth());
        var start = month.atDay(1).atStartOfDay();
        var end = month.plusMonths(1).atDay(1).atStartOfDay();
        long total = orderMapper.countPage(null, userId, null, PromotionAttributionStatus.ATTRIBUTED,
                start, end, true);
        var list = orderMapper.findPage(null, userId, null, PromotionAttributionStatus.ATTRIBUTED,
                        start, end, (query.getPage() - 1) * query.getSize(), query.getSize(), true).stream()
                .map(PromotionOrderUserServiceImpl::toUserVO).toList();
        return UserPromotionOrderPageVO.builder().list(list).page(query.getPage()).size(query.getSize())
                .total(total).build();
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionMonthlyCommissionVO getMonthly(Long userId, PromotionOrderMonthQueryDTO query) {
        YearMonth month = YearMonth.parse(query.getMonth());
        var summary = orderMapper.summarizeMonth(userId, month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay());
        BigDecimal calculated = zero(summary.getCalculatedCommission());
        BigDecimal reversed = zero(summary.getReversedCommission());
        return PromotionMonthlyCommissionVO.builder().month(query.getMonth())
                .paidOrderCount(summary.getPaidOrderCount() == null ? 0 : summary.getPaidOrderCount())
                .calculatedCommission(calculated)
                .reversedCommission(reversed).netCommission(calculated.subtract(reversed)).build();
    }

    private static UserPromotionOrderVO toUserVO(PromotionOrder order) {
        return UserPromotionOrderVO.builder().externalOrderId(order.getExternalOrderId())
                .currency(order.getCurrency()).status(order.getStatus()).paidAt(order.getPaidAt())
                .commissionAmount(order.getCommissionAmount()).build();
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }
}

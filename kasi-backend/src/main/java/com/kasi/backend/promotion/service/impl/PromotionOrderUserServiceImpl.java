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
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionOrderUserServiceImpl implements PromotionOrderUserService {
    private static final int EXPORT_LIMIT = 10_000;
    private final PromotionOrderMapper orderMapper;

    @Override
    @Transactional(readOnly = true)
    public UserPromotionOrderPageVO getPage(Long userId, PromotionOrderMonthQueryDTO query) {
        YearMonth month = YearMonth.parse(query.getMonth());
        var start = month.atDay(1).atStartOfDay();
        var end = month.plusMonths(1).atDay(1).atStartOfDay();
        long total = orderMapper.countPage(null, userId, null, PromotionAttributionStatus.ATTRIBUTED,
                start, end);
        var list = orderMapper.findPage(null, userId, null, PromotionAttributionStatus.ATTRIBUTED,
                        start, end, (query.getPage() - 1) * query.getSize(), query.getSize()).stream()
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
                .grossOrderAmount(zero(summary.getGrossOrderAmount())).calculatedCommission(calculated)
                .reversedCommission(reversed).netCommission(calculated.subtract(reversed)).build();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportCsv(Long userId, PromotionOrderMonthQueryDTO query) {
        YearMonth month = YearMonth.parse(query.getMonth());
        var orders = orderMapper.findPage(null, userId, null, PromotionAttributionStatus.ATTRIBUTED,
                month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay(),
                0, EXPORT_LIMIT);
        return userCsv(orders).getBytes(StandardCharsets.UTF_8);
    }

    private static UserPromotionOrderVO toUserVO(PromotionOrder order) {
        return UserPromotionOrderVO.builder().id(order.getId())
                .externalOrderId(order.getExternalOrderId()).orderAmount(order.getOrderAmount())
                .currency(order.getCurrency()).status(order.getStatus()).paidAt(order.getPaidAt())
                .trackingNo(order.getTrackingNo()).commissionAmount(order.getCommissionAmount())
                .commissionStatus(order.getCommissionStatus()).build();
    }

    private static String userCsv(List<PromotionOrder> orders) {
        List<String> rows = new ArrayList<>();
        rows.add("订单ID,订单金额,币种,状态,支付时间,trackingNo,佣金,佣金状态");
        for (var order : orders) {
            rows.add(String.join(",", csvValue(order.getExternalOrderId()), csvValue(order.getOrderAmount()),
                    csvValue(order.getCurrency()), csvValue(order.getStatus()), csvValue(order.getPaidAt()),
                    csvValue(order.getTrackingNo()), csvValue(order.getCommissionAmount()),
                    csvValue(order.getCommissionStatus())));
        }
        return "\uFEFF" + String.join("\r\n", rows) + "\r\n";
    }

    private static String csvValue(Object value) {
        String normalized = value == null ? "" : value.toString();
        return "\"" + normalized.replace("\"", "\"\"") + "\"";
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }
}

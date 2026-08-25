package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.promotion.dto.PromotionOrderPageQueryDTO;
import com.kasi.backend.promotion.dto.PromotionOrderSyncDTO;
import com.kasi.backend.promotion.entity.PromotionOrder;
import com.kasi.backend.promotion.mapper.PromotionOrderMapper;
import com.kasi.backend.promotion.service.PromotionOrderAdminService;
import com.kasi.backend.promotion.service.PromotionOrderSyncService;
import com.kasi.backend.promotion.vo.PromotionOrderPageVO;
import com.kasi.backend.promotion.vo.PromotionOrderSyncResultVO;
import com.kasi.backend.promotion.vo.PromotionOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionOrderAdminServiceImpl implements PromotionOrderAdminService {
    private static final int EXPORT_LIMIT = 10_000;
    private final PromotionOrderSyncService orderSyncService;
    private final PromotionOrderMapper orderMapper;

    @Override
    public PromotionOrderSyncResultVO sync(PromotionOrderSyncDTO request) {
        return orderSyncService.sync(request.getProviderId(), request.getStartDate(), request.getEndDate());
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionOrderPageVO getPage(PromotionOrderPageQueryDTO query) {
        long total = count(query);
        List<PromotionOrderVO> list = orderMapper.findPage(query.getProviderId(), query.getUserId(),
                        query.getStatus(), query.getAttributionStatus(), query.getStartDate(), query.getEndDate(),
                        (query.getPage() - 1) * query.getSize(), query.getSize()).stream()
                .map(PromotionOrderAdminServiceImpl::toVO).toList();
        return PromotionOrderPageVO.builder().list(list).page(query.getPage()).size(query.getSize())
                .total(total).build();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportCsv(PromotionOrderPageQueryDTO query) {
        List<PromotionOrder> orders = orderMapper.findPage(query.getProviderId(), query.getUserId(),
                query.getStatus(), query.getAttributionStatus(), query.getStartDate(), query.getEndDate(),
                0, EXPORT_LIMIT);
        return csv(orders).getBytes(StandardCharsets.UTF_8);
    }

    private long count(PromotionOrderPageQueryDTO query) {
        return orderMapper.countPage(query.getProviderId(), query.getUserId(), query.getStatus(),
                query.getAttributionStatus(), query.getStartDate(), query.getEndDate());
    }

    static PromotionOrderVO toVO(PromotionOrder order) {
        return PromotionOrderVO.builder().id(order.getId()).providerId(order.getProviderId())
                .externalOrderId(order.getExternalOrderId()).externalDramaId(order.getExternalDramaId())
                .searchCode(order.getSearchCode()).channelCode(order.getChannelCode())
                .orderAmount(order.getOrderAmount()).currency(order.getCurrency()).status(order.getStatus())
                .paidAt(order.getPaidAt()).customParams(order.getCustomParams()).trackingNo(order.getTrackingNo())
                .userId(order.getUserId()).mediaAccountId(order.getMediaAccountId()).dramaId(order.getDramaId())
                .attributionStatus(order.getAttributionStatus()).channelFeeRate(order.getChannelFeeRate())
                .principalFeeRate(order.getPrincipalFeeRate())
                .principalCommissionRate(order.getPrincipalCommissionRate())
                .downstreamFeeRate(order.getDownstreamFeeRate())
                .downstreamCommissionRate(order.getDownstreamCommissionRate())
                .commissionAmount(order.getCommissionAmount()).commissionStatus(order.getCommissionStatus())
                .lastSyncedAt(order.getLastSyncedAt()).build();
    }

    static String csv(List<PromotionOrder> orders) {
        List<String> rows = new ArrayList<>();
        rows.add("订单ID,平台ID,订单金额,币种,状态,支付时间,trackingNo,用户ID,归因状态,佣金,佣金状态");
        for (PromotionOrder order : orders) {
            rows.add(String.join(",", escape(order.getExternalOrderId()), value(order.getProviderId()),
                    value(order.getOrderAmount()), escape(order.getCurrency()), value(order.getStatus()),
                    value(order.getPaidAt()), escape(order.getTrackingNo()), value(order.getUserId()),
                    value(order.getAttributionStatus()), value(order.getCommissionAmount()),
                    value(order.getCommissionStatus())));
        }
        return "\uFEFF" + String.join("\r\n", rows) + "\r\n";
    }

    private static String value(Object value) {
        return escape(value == null ? null : value.toString());
    }

    private static String escape(String value) {
        String normalized = value == null ? "" : value;
        return "\"" + normalized.replace("\"", "\"\"") + "\"";
    }
}

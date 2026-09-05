package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.drama.calculator.ProviderCommissionCalculator;
import com.kasi.backend.drama.entity.ProviderCommissionRuleHistory;
import com.kasi.backend.drama.mapper.ProviderCommissionRuleHistoryMapper;
import com.kasi.backend.promotion.entity.PromotionOrder;
import com.kasi.backend.promotion.enums.PromotionAttributionStatus;
import com.kasi.backend.promotion.enums.PromotionCommissionStatus;
import com.kasi.backend.promotion.enums.PromotionOrderStatus;
import com.kasi.backend.promotion.mapper.PromotionOrderMapper;
import com.kasi.backend.promotion.service.PromotionOrderService;
import com.kasi.backend.promotion.service.PromotionOrderUpsertResult;
import com.kasi.backend.provider.spi.ProviderOrderRecord;
import com.kasi.backend.provider.spi.ProviderOrderStatus;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PromotionOrderServiceImpl implements PromotionOrderService {
    private final PromotionOrderMapper orderMapper;
    private final PromotionUserMapper userMapper;
    private final ProviderCommissionRuleHistoryMapper historyMapper;
    private final ProviderCommissionCalculator commissionCalculator;
    private final Clock clock;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PromotionOrderUpsertResult upsert(ProviderRuntimeConnection runtime,
                                             ProviderOrderRecord record,
                                             LocalDateTime syncStartDate,
                                             LocalDateTime syncEndDate) {
        PromotionOrder existing = orderMapper.findBySourceForUpdate(
                runtime.connectionId(), record.externalOrderId());
        if (existing == null) {
            PromotionOrder order = fromRecord(runtime, record, syncStartDate, syncEndDate);
            applyAttributionAndCommission(order);
            try {
                orderMapper.insert(order);
            } catch (DuplicateKeyException exception) {
                PromotionOrder concurrent = orderMapper.findBySource(
                        runtime.connectionId(), record.externalOrderId());
                if (concurrent == null) {
                    throw exception;
                }
                return new PromotionOrderUpsertResult(false,
                        concurrent.getAttributionStatus() == PromotionAttributionStatus.ATTRIBUTED);
            }
            return new PromotionOrderUpsertResult(true,
                    order.getAttributionStatus() == PromotionAttributionStatus.ATTRIBUTED);
        }

        copySourceFields(existing, record, syncStartDate, syncEndDate);
        orderMapper.updateSourceFields(existing);
        if (record.status() == ProviderOrderStatus.REFUNDED && existing.getCommissionAmount() != null) {
            orderMapper.markCommissionReversed(existing.getId());
        } else if (existing.getRuleHistoryId() == null && record.status() == ProviderOrderStatus.PAID) {
            applyAttributionAndCommission(existing);
            orderMapper.applyAttributionAndCommission(existing);
        }
        return new PromotionOrderUpsertResult(false,
                existing.getAttributionStatus() == PromotionAttributionStatus.ATTRIBUTED);
    }

    private PromotionOrder fromRecord(ProviderRuntimeConnection runtime,
                                      ProviderOrderRecord record,
                                      LocalDateTime syncStartDate,
                                      LocalDateTime syncEndDate) {
        PromotionOrder order = new PromotionOrder();
        order.setConnectionId(runtime.connectionId());
        order.setProviderId(runtime.providerId());
        order.setAttributionStatus(PromotionAttributionStatus.UNATTRIBUTED);
        copySourceFields(order, record, syncStartDate, syncEndDate);
        return order;
    }

    private void copySourceFields(PromotionOrder order,
                                  ProviderOrderRecord record,
                                  LocalDateTime syncStartDate,
                                  LocalDateTime syncEndDate) {
        order.setExternalOrderId(record.externalOrderId());
        order.setExternalUserId(record.externalUserId());
        order.setExternalDramaId(record.externalDramaId());
        order.setSearchCode(record.searchCode());
        order.setChannelCode(record.channelCode());
        order.setPartnerId(record.partnerId());
        order.setOrderAmountMinor(record.orderAmountMinor());
        order.setOrderAmount(record.orderAmount());
        order.setCurrency(record.currency());
        order.setRawStatus(record.rawStatus());
        order.setStatus(mapStatus(record.status()));
        order.setPaidAt(record.paidAt());
        order.setProviderUpdatedAt(record.providerUpdatedAt());
        order.setCustomParams(record.customParams());
        order.setRawPayloadJson(record.rawPayloadJson());
        order.setSyncStartDate(syncStartDate);
        order.setSyncEndDate(syncEndDate);
        order.setLastSyncedAt(LocalDateTime.now(clock));
    }

    private void applyAttributionAndCommission(PromotionOrder order) {
        var user = order.getCustomParams() == null ? null : userMapper.findByUserNo(order.getCustomParams());
        if (user == null) {
            order.setAttributionStatus(PromotionAttributionStatus.UNATTRIBUTED);
            order.setCommissionStatus(PromotionCommissionStatus.NOT_APPLICABLE);
            return;
        }

        order.setUserId(user.getId());
        order.setAttributionStatus(PromotionAttributionStatus.ATTRIBUTED);
        if (order.getStatus() != PromotionOrderStatus.PAID) {
            order.setCommissionStatus(PromotionCommissionStatus.NOT_APPLICABLE);
            return;
        }

        ProviderCommissionRuleHistory history = historyMapper.findLatestByProviderId(order.getProviderId());
        if (history == null) {
            order.setCommissionStatus(PromotionCommissionStatus.ERROR);
            order.setLastErrorMessage("平台尚未配置可用的分佣规则快照");
            return;
        }
        order.setRuleHistoryId(history.getId());
        order.setChannelFeeRate(history.getChannelFeeRate());
        order.setPrincipalFeeRate(history.getPrincipalFeeRate());
        order.setPrincipalCommissionRate(history.getPrincipalCommissionRate());
        order.setDownstreamFeeRate(history.getDownstreamFeeRate());
        order.setDownstreamCommissionRate(history.getDownstreamCommissionRate());
        order.setCommissionAmount(commissionCalculator.calculate(order.getOrderAmount(),
                history.getChannelFeeRate(), history.getPrincipalFeeRate(),
                history.getPrincipalCommissionRate(), history.getDownstreamFeeRate(),
                history.getDownstreamCommissionRate()));
        order.setCommissionStatus(PromotionCommissionStatus.CALCULATED);
        order.setLastErrorMessage(null);
    }

    private PromotionOrderStatus mapStatus(ProviderOrderStatus status) {
        return switch (status) {
            case UNPAID -> PromotionOrderStatus.UNPAID;
            case PAID -> PromotionOrderStatus.PAID;
            case REFUNDED -> PromotionOrderStatus.REFUNDED;
            case UNKNOWN -> PromotionOrderStatus.UNKNOWN;
        };
    }
}

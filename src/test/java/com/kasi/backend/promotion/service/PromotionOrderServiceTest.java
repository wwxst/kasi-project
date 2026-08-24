package com.kasi.backend.promotion.service;

import com.kasi.backend.drama.calculator.ProviderCommissionCalculator;
import com.kasi.backend.drama.entity.ProviderCommissionRuleHistory;
import com.kasi.backend.drama.mapper.ProviderCommissionRuleHistoryMapper;
import com.kasi.backend.promotion.entity.PromotionLink;
import com.kasi.backend.promotion.entity.PromotionOrder;
import com.kasi.backend.promotion.enums.PromotionAttributionStatus;
import com.kasi.backend.promotion.enums.PromotionCommissionStatus;
import com.kasi.backend.promotion.enums.PromotionOrderStatus;
import com.kasi.backend.promotion.mapper.PromotionLinkMapper;
import com.kasi.backend.promotion.mapper.PromotionOrderMapper;
import com.kasi.backend.promotion.service.impl.PromotionOrderServiceImpl;
import com.kasi.backend.provider.spi.ProviderOrderRecord;
import com.kasi.backend.provider.spi.ProviderOrderStatus;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PromotionOrderServiceTest {
    private PromotionOrderMapper orderMapper;
    private PromotionLinkMapper linkMapper;
    private ProviderCommissionRuleHistoryMapper historyMapper;
    private PromotionOrderService service;
    private ProviderRuntimeConnection runtime;

    @BeforeEach
    void setUp() {
        orderMapper = mock(PromotionOrderMapper.class);
        linkMapper = mock(PromotionLinkMapper.class);
        historyMapper = mock(ProviderCommissionRuleHistoryMapper.class);
        service = new PromotionOrderServiceImpl(orderMapper, linkMapper, historyMapper,
                new ProviderCommissionCalculator(),
                Clock.fixed(Instant.parse("2025-07-02T08:00:00Z"), ZoneOffset.UTC));
        runtime = new ProviderRuntimeConnection(3L, 7L, "GOODSHORT", "GoodShort", null, null);
    }

    @Test
    @DisplayName("首次已支付订单通过追踪号归因并保存费率和佣金快照")
    void paidOrderIsAttributedAndSnapshotted() {
        PromotionLink link = link();
        when(linkMapper.findByTrackingNo("tracking-1")).thenReturn(link);
        when(historyMapper.findLatestByProviderId(7L)).thenReturn(history());
        doAnswer(invocation -> {
            PromotionOrder order = invocation.getArgument(0);
            order.setId(99L);
            return 1;
        }).when(orderMapper).insert(any());

        PromotionOrderUpsertResult result = service.upsert(runtime, record(ProviderOrderStatus.PAID),
                LocalDateTime.of(2025, 7, 1, 0, 0),
                LocalDateTime.of(2025, 7, 1, 23, 59, 59));

        ArgumentCaptor<PromotionOrder> captor = ArgumentCaptor.forClass(PromotionOrder.class);
        verify(orderMapper).insert(captor.capture());
        PromotionOrder order = captor.getValue();
        assertThat(result.inserted()).isTrue();
        assertThat(result.attributed()).isTrue();
        assertThat(order.getUserId()).isEqualTo(11L);
        assertThat(order.getPromotionLinkId()).isEqualTo(21L);
        assertThat(order.getAttributionStatus()).isEqualTo(PromotionAttributionStatus.ATTRIBUTED);
        assertThat(order.getRuleHistoryId()).isEqualTo(31L);
        assertThat(order.getCommissionAmount()).isEqualByComparingTo("4.79");
        assertThat(order.getCommissionStatus()).isEqualTo(PromotionCommissionStatus.CALCULATED);
    }

    @Test
    @DisplayName("重复同步已有费率快照的订单只更新供应方字段而不重新计算")
    void duplicateOrderDoesNotRecalculateSnapshot() {
        PromotionOrder existing = new PromotionOrder();
        existing.setId(99L);
        existing.setConnectionId(3L);
        existing.setExternalOrderId("order-1");
        existing.setRuleHistoryId(31L);
        existing.setCommissionAmount(new BigDecimal("4.79"));
        existing.setAttributionStatus(PromotionAttributionStatus.ATTRIBUTED);
        when(orderMapper.findBySourceForUpdate(3L, "order-1")).thenReturn(existing);

        PromotionOrderUpsertResult result = service.upsert(runtime, record(ProviderOrderStatus.PAID),
                LocalDateTime.of(2025, 7, 1, 0, 0),
                LocalDateTime.of(2025, 7, 1, 23, 59, 59));

        assertThat(result.inserted()).isFalse();
        verify(orderMapper).updateSourceFields(existing);
        verifyNoInteractions(linkMapper, historyMapper);
        verify(orderMapper, never()).applyAttributionAndCommission(any());
    }

    @Test
    @DisplayName("退款订单保留原佣金金额并标记为冲销")
    void refundedOrderReversesExistingCommission() {
        PromotionOrder existing = new PromotionOrder();
        existing.setId(99L);
        existing.setConnectionId(3L);
        existing.setExternalOrderId("order-1");
        existing.setRuleHistoryId(31L);
        existing.setCommissionAmount(new BigDecimal("4.79"));
        existing.setAttributionStatus(PromotionAttributionStatus.ATTRIBUTED);
        when(orderMapper.findBySourceForUpdate(3L, "order-1")).thenReturn(existing);

        service.upsert(runtime, record(ProviderOrderStatus.REFUNDED),
                LocalDateTime.of(2025, 7, 1, 0, 0),
                LocalDateTime.of(2025, 7, 1, 23, 59, 59));

        verify(orderMapper).markCommissionReversed(99L);
    }

    @Test
    @DisplayName("无法匹配追踪号的订单保存为未归因且不猜测用户")
    void unknownTrackingNumberRemainsUnattributed() {
        when(linkMapper.findByTrackingNo("tracking-1")).thenReturn(null);

        PromotionOrderUpsertResult result = service.upsert(runtime, record(ProviderOrderStatus.PAID),
                LocalDateTime.of(2025, 7, 1, 0, 0),
                LocalDateTime.of(2025, 7, 1, 23, 59, 59));

        ArgumentCaptor<PromotionOrder> captor = ArgumentCaptor.forClass(PromotionOrder.class);
        verify(orderMapper).insert(captor.capture());
        assertThat(result.attributed()).isFalse();
        assertThat(captor.getValue().getAttributionStatus())
                .isEqualTo(PromotionAttributionStatus.UNATTRIBUTED);
        assertThat(captor.getValue().getCommissionStatus())
                .isEqualTo(PromotionCommissionStatus.NOT_APPLICABLE);
        assertThat(captor.getValue().getUserId()).isNull();
        verifyNoInteractions(historyMapper);
    }

    private PromotionLink link() {
        PromotionLink link = new PromotionLink();
        link.setId(21L);
        link.setUserId(11L);
        link.setMediaAccountId(41L);
        link.setDramaId(51L);
        link.setTrackingNo("tracking-1");
        return link;
    }

    private ProviderCommissionRuleHistory history() {
        ProviderCommissionRuleHistory history = new ProviderCommissionRuleHistory();
        history.setId(31L);
        history.setProviderId(7L);
        history.setRuleId(61L);
        history.setChannelFeeRate(new BigDecimal("0.1000000000"));
        history.setPrincipalFeeRate(new BigDecimal("0.0200000000"));
        history.setPrincipalCommissionRate(new BigDecimal("0.8000000000"));
        history.setDownstreamFeeRate(new BigDecimal("0.0300000000"));
        history.setDownstreamCommissionRate(new BigDecimal("0.7000000000"));
        return history;
    }

    private ProviderOrderRecord record(ProviderOrderStatus status) {
        return new ProviderOrderRecord("order-1", "remote-user", 999L, new BigDecimal("9.99"),
                "USD", LocalDateTime.of(2025, 7, 1, 15, 55, 30), status,
                status == ProviderOrderStatus.REFUNDED ? "3" : "1", "tracking-1", "book-1",
                "21302", "GRKOCABTT00001", "partner-1",
                LocalDateTime.of(2025, 7, 1, 16, 0), "{\"orderId\":\"order-1\"}");
    }
}

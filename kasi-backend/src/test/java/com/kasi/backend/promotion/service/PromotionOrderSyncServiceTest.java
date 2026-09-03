package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.service.impl.PromotionOrderSyncServiceImpl;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.OrderSyncProviderAdapter;
import com.kasi.backend.provider.spi.OrderSyncRequest;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.spi.ProviderOrderPage;
import com.kasi.backend.provider.spi.ProviderOrderRecord;
import com.kasi.backend.provider.spi.ProviderOrderStatus;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("推广订单同步服务")
class PromotionOrderSyncServiceTest {

    @Test
    @DisplayName("同步遍历供应方分页并汇总新增更新和未归因数量")
    void syncTraversesPagesAndReturnsCounts() {
        ProviderRuntimeConnectionService runtimeService = mock(ProviderRuntimeConnectionService.class);
        PromotionOrderService orderService = mock(PromotionOrderService.class);
        OrderSyncProviderAdapter adapter = mock(OrderSyncProviderAdapter.class);
        ProviderRuntimeConnection runtime = new ProviderRuntimeConnection(3L, 7L, "GOODSHORT", "GoodShort",
                new ProviderConnectionSecret("url", "pid", "key", "USD"), adapter);
        LocalDateTime startDate = LocalDateTime.of(2025, 7, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2025, 7, 1, 23, 59, 59);
        ProviderOrderRecord first = mock(ProviderOrderRecord.class);
        ProviderOrderRecord second = mock(ProviderOrderRecord.class);
        ProviderOrderRecord third = mock(ProviderOrderRecord.class);
        when(first.status()).thenReturn(ProviderOrderStatus.PAID);
        when(second.status()).thenReturn(ProviderOrderStatus.PAID);
        when(third.status()).thenReturn(ProviderOrderStatus.REFUNDED);
        when(runtimeService.resolve(7L, ProviderCapability.ORDER_SYNC)).thenReturn(runtime);
        when(adapter.fetchOrders(any(), eq(new OrderSyncRequest(startDate, endDate, 1, 500))))
                .thenReturn(new ProviderOrderPage(List.of(first, second), 1, 2, 2, 3, true));
        when(adapter.fetchOrders(any(), eq(new OrderSyncRequest(startDate, endDate, 2, 500))))
                .thenReturn(new ProviderOrderPage(List.of(third), 2, 2, 2, 3, false));
        when(orderService.upsert(runtime, first, startDate, endDate))
                .thenReturn(new PromotionOrderUpsertResult(true, true));
        when(orderService.upsert(runtime, second, startDate, endDate))
                .thenReturn(new PromotionOrderUpsertResult(true, false));
        when(orderService.upsert(runtime, third, startDate, endDate))
                .thenReturn(new PromotionOrderUpsertResult(false, true));

        PromotionOrderSyncService service = new PromotionOrderSyncServiceImpl(runtimeService, orderService);

        var result = service.sync(7L, startDate, endDate);

        assertThat(result.getFetchedCount()).isEqualTo(3);
        assertThat(result.getInsertedCount()).isEqualTo(2);
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getUnattributedCount()).isEqualTo(1);
        verify(adapter).fetchOrders(any(), eq(new OrderSyncRequest(startDate, endDate, 1, 500)));
        verify(adapter).fetchOrders(any(), eq(new OrderSyncRequest(startDate, endDate, 2, 500)));
        verify(orderService).upsert(runtime, first, startDate, endDate);
        verify(orderService).upsert(runtime, second, startDate, endDate);
        verify(orderService).upsert(runtime, third, startDate, endDate);
    }

    @Test
    @DisplayName("同步把未支付和未知状态也写入订单")
    void syncPersistsUnpaidAndUnknownStatuses() {
        ProviderRuntimeConnectionService runtimeService = mock(ProviderRuntimeConnectionService.class);
        PromotionOrderService orderService = mock(PromotionOrderService.class);
        OrderSyncProviderAdapter adapter = mock(OrderSyncProviderAdapter.class);
        ProviderRuntimeConnection runtime = new ProviderRuntimeConnection(3L, 7L, "GOODSHORT", "GoodShort",
                new ProviderConnectionSecret("url", "pid", "key", "USD"), adapter);
        LocalDateTime startDate = LocalDateTime.of(2025, 7, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2025, 7, 1, 23, 59, 59);
        ProviderOrderRecord unpaid = mock(ProviderOrderRecord.class);
        ProviderOrderRecord unknown = mock(ProviderOrderRecord.class);
        when(unpaid.status()).thenReturn(ProviderOrderStatus.UNPAID);
        when(unknown.status()).thenReturn(ProviderOrderStatus.UNKNOWN);
        when(runtimeService.resolve(7L, ProviderCapability.ORDER_SYNC)).thenReturn(runtime);
        when(adapter.fetchOrders(any(), eq(new OrderSyncRequest(startDate, endDate, 1, 500))))
                .thenReturn(new ProviderOrderPage(List.of(unpaid, unknown), 1, 500, 1, 2, false));
        when(orderService.upsert(runtime, unpaid, startDate, endDate))
                .thenReturn(new PromotionOrderUpsertResult(true, false));
        when(orderService.upsert(runtime, unknown, startDate, endDate))
                .thenReturn(new PromotionOrderUpsertResult(true, false));

        PromotionOrderSyncService service = new PromotionOrderSyncServiceImpl(runtimeService, orderService);

        var result = service.sync(7L, startDate, endDate);

        assertThat(result.getFetchedCount()).isEqualTo(2);
        assertThat(result.getInsertedCount()).isEqualTo(2);
        assertThat(result.getUpdatedCount()).isZero();
        verify(orderService).upsert(runtime, unpaid, startDate, endDate);
        verify(orderService).upsert(runtime, unknown, startDate, endDate);
    }
}

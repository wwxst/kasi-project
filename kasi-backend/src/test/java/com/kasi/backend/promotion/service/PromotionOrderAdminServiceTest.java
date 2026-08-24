package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.PromotionOrderSyncDTO;
import com.kasi.backend.promotion.service.impl.PromotionOrderAdminServiceImpl;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.OrderSyncProviderAdapter;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.spi.ProviderOrderPage;
import com.kasi.backend.provider.spi.ProviderOrderRecord;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PromotionOrderAdminServiceTest {
    @Test
    @DisplayName("管理员手动同步遍历供应方分页并汇总新增更新和未归因数量")
    void syncTraversesPagesAndReturnsCounts() {
        ProviderRuntimeConnectionService runtimeService = mock(ProviderRuntimeConnectionService.class);
        PromotionOrderService orderService = mock(PromotionOrderService.class);
        OrderSyncProviderAdapter adapter = mock(OrderSyncProviderAdapter.class);
        ProviderRuntimeConnection runtime = new ProviderRuntimeConnection(3L, 7L, "GOODSHORT", "GoodShort",
                new ProviderConnectionSecret("url", "pid", "key", "USD"), adapter);
        when(runtimeService.resolve(7L, ProviderCapability.ORDER_SYNC)).thenReturn(runtime);
        ProviderOrderRecord first = mock(ProviderOrderRecord.class);
        ProviderOrderRecord second = mock(ProviderOrderRecord.class);
        ProviderOrderRecord third = mock(ProviderOrderRecord.class);
        when(adapter.fetchOrders(any(), argThat(request -> request != null && request.pageNo() == 1)))
                .thenReturn(new ProviderOrderPage(List.of(first, second), 1, 2, 2, 3, true));
        when(adapter.fetchOrders(any(), argThat(request -> request != null && request.pageNo() == 2)))
                .thenReturn(new ProviderOrderPage(List.of(third), 2, 2, 2, 3, false));
        when(orderService.upsert(eq(runtime), eq(first), any(), any()))
                .thenReturn(new PromotionOrderUpsertResult(true, true));
        when(orderService.upsert(eq(runtime), eq(second), any(), any()))
                .thenReturn(new PromotionOrderUpsertResult(true, false));
        when(orderService.upsert(eq(runtime), eq(third), any(), any()))
                .thenReturn(new PromotionOrderUpsertResult(false, true));
        PromotionOrderAdminServiceImpl service = new PromotionOrderAdminServiceImpl(
                runtimeService, orderService, null);
        PromotionOrderSyncDTO request = new PromotionOrderSyncDTO();
        request.setProviderId(7L);
        request.setStartDate(LocalDateTime.of(2025, 7, 1, 0, 0));
        request.setEndDate(LocalDateTime.of(2025, 7, 1, 23, 59, 59));

        var result = service.sync(request);

        assertThat(result.getFetchedCount()).isEqualTo(3);
        assertThat(result.getInsertedCount()).isEqualTo(2);
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getUnattributedCount()).isEqualTo(1);
        verify(adapter, times(2)).fetchOrders(any(), any());
    }
}

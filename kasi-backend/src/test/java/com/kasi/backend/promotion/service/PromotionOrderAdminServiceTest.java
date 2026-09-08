package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.PromotionOrderPageQueryDTO;
import com.kasi.backend.promotion.dto.PromotionOrderSyncDTO;
import com.kasi.backend.promotion.entity.PromotionOrder;
import com.kasi.backend.promotion.enums.PromotionOrderStatus;
import com.kasi.backend.promotion.mapper.PromotionOrderMapper;
import com.kasi.backend.promotion.service.impl.PromotionOrderAdminServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromotionOrderAdminServiceTest {
    @Test
    @DisplayName("管理员手动同步委托共享同步服务并保留指定时间窗")
    void syncDelegatesToSharedService() {
        PromotionOrderSyncService syncService = mock(PromotionOrderSyncService.class);
        PromotionOrderAdminServiceImpl service = new PromotionOrderAdminServiceImpl(syncService, null);
        PromotionOrderSyncDTO request = new PromotionOrderSyncDTO();
        request.setProviderId(7L);
        request.setStartDate(LocalDateTime.of(2025, 7, 1, 0, 0));
        request.setEndDate(LocalDateTime.of(2025, 7, 1, 23, 59, 59));
        var expected = com.kasi.backend.promotion.vo.PromotionOrderSyncResultVO.builder()
                .fetchedCount(3).insertedCount(2).updatedCount(1).unattributedCount(1).build();
        when(syncService.sync(request.getProviderId(), request.getStartDate(), request.getEndDate()))
                .thenReturn(expected);

        var result = service.sync(request);

        assertThat(result).isSameAs(expected);
        verify(syncService).sync(7L, request.getStartDate(), request.getEndDate());
    }

    @Test
    @DisplayName("管理端订单返回真实追踪号且退款当前有效佣金为零")
    void adminOrderShowsTrackingAndZeroEffectiveRefundCommission() {
        PromotionOrderMapper mapper = mock(PromotionOrderMapper.class);
        PromotionOrder order = new PromotionOrder();
        order.setId(9L);
        order.setTrackingNo("tracking-9");
        order.setStatus(PromotionOrderStatus.REFUNDED);
        order.setCommissionAmount(new BigDecimal("4.79"));
        when(mapper.findPage(null, null, null, null, null, null, 0, 20, false))
                .thenReturn(List.of(order));
        PromotionOrderAdminServiceImpl service = new PromotionOrderAdminServiceImpl(null, mapper);
        PromotionOrderPageQueryDTO query = new PromotionOrderPageQueryDTO();

        var result = service.getPage(query);

        assertThat(result.getList().getFirst().getTrackingNo()).isEqualTo("tracking-9");
        assertThat(result.getList().getFirst().getCommissionAmount()).isEqualByComparingTo("0.00");
    }
}

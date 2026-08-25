package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.PromotionOrderSyncDTO;
import com.kasi.backend.promotion.service.impl.PromotionOrderAdminServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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
}

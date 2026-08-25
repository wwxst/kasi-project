package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.promotion.service.PromotionOrderService;
import com.kasi.backend.promotion.service.PromotionOrderSyncService;
import com.kasi.backend.promotion.vo.PromotionOrderSyncResultVO;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.OrderSyncProviderAdapter;
import com.kasi.backend.provider.spi.OrderSyncRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PromotionOrderSyncServiceImpl implements PromotionOrderSyncService {
    private static final int SYNC_PAGE_SIZE = 500;

    private final ProviderRuntimeConnectionService runtimeService;
    private final PromotionOrderService orderService;

    @Override
    public PromotionOrderSyncResultVO sync(Long providerId, LocalDateTime startDate, LocalDateTime endDate) {
        var runtime = runtimeService.resolve(providerId, ProviderCapability.ORDER_SYNC);
        OrderSyncProviderAdapter adapter = (OrderSyncProviderAdapter) runtime.adapter();
        int pageNo = 1;
        int fetched = 0;
        int inserted = 0;
        int updated = 0;
        int unattributed = 0;
        boolean hasNext;
        do {
            var page = adapter.fetchOrders(runtime.secret(),
                    new OrderSyncRequest(startDate, endDate, pageNo, SYNC_PAGE_SIZE));
            for (var record : page.records()) {
                var result = orderService.upsert(runtime, record, startDate, endDate);
                fetched++;
                if (result.inserted()) {
                    inserted++;
                } else {
                    updated++;
                }
                if (!result.attributed()) {
                    unattributed++;
                }
            }
            hasNext = page.hasNext();
            pageNo++;
        } while (hasNext);
        return PromotionOrderSyncResultVO.builder().fetchedCount(fetched).insertedCount(inserted)
                .updatedCount(updated).unattributedCount(unattributed).build();
    }
}

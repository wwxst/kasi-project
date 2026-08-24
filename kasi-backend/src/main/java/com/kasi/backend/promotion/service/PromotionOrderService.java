package com.kasi.backend.promotion.service;

import com.kasi.backend.provider.spi.ProviderOrderRecord;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;

import java.time.LocalDateTime;

public interface PromotionOrderService {
    PromotionOrderUpsertResult upsert(ProviderRuntimeConnection runtime,
                                      ProviderOrderRecord record,
                                      LocalDateTime syncStartDate,
                                      LocalDateTime syncEndDate);
}

package com.kasi.backend.provider.spi;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProviderOrderRecord(
        String externalOrderId,
        String externalUserId,
        long orderAmountMinor,
        BigDecimal orderAmount,
        String currency,
        LocalDateTime paidAt,
        ProviderOrderStatus status,
        String rawStatus,
        String customParams,
        String externalDramaId,
        String searchCode,
        String channelCode,
        String partnerId,
        LocalDateTime providerUpdatedAt,
        String rawPayloadJson) {
}

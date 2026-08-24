package com.kasi.backend.provider.spi;

import java.time.LocalDateTime;

public record OrderSyncRequest(
        LocalDateTime startDate,
        LocalDateTime endDate,
        int pageNo,
        int pageSize) {
}

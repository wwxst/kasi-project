package com.kasi.backend.provider.spi;

import java.util.List;

public record ProviderOrderPage(
        List<ProviderOrderRecord> records,
        int pageNo,
        int pageSize,
        int pages,
        long total,
        boolean hasNext) {
}

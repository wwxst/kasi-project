package com.kasi.backend.provider.spi;

import java.util.List;

public record DramaCatalogPage(
        List<ProviderDramaRecord> items,
        int pageNo,
        int pageSize,
        long total,
        boolean hasNext,
        Long nextUpdateTime) {

    public DramaCatalogPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}

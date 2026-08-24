package com.kasi.backend.provider.spi;

import java.time.LocalDateTime;
import java.util.List;

public record ProviderDramaRecord(
        String externalDramaId,
        String title,
        String originalTitle,
        String description,
        String coverUrl,
        String language,
        String dramaType,
        String remoteShowStatus,
        LocalDateTime remoteUpdatedAt,
        List<ProviderDramaContentRecord> contents) {

    public ProviderDramaRecord {
        contents = contents == null ? List.of() : List.copyOf(contents);
    }
}

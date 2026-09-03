package com.kasi.backend.provider.spi;

import java.time.LocalDateTime;
import java.util.List;

public record ProviderDramaRecord(
        String externalDramaId,
        String title,
        String originalTitle,
        String titleZh,
        String description,
        String coverUrl,
        List<String> labelNames,
        String categoryName,
        String language,
        Integer remoteRank,
        String dramaType,
        String novelType,
        Integer novelSubType,
        String remoteShowStatus,
        LocalDateTime remoteCreatedAt,
        LocalDateTime remoteUpdatedAt,
        List<ProviderDramaContentRecord> contents) {

    public ProviderDramaRecord {
        labelNames = labelNames == null ? List.of() : List.copyOf(labelNames);
        contents = contents == null ? List.of() : List.copyOf(contents);
    }

    public ProviderDramaRecord(String externalDramaId, String title, String originalTitle,
                               String description, String coverUrl, String language,
                               String dramaType, String remoteShowStatus,
                               LocalDateTime remoteUpdatedAt, List<ProviderDramaContentRecord> contents) {
        this(externalDramaId, title, originalTitle, null, description, coverUrl, List.of(), null,
                language, null, dramaType, null, null, remoteShowStatus, null, remoteUpdatedAt, contents);
    }
}

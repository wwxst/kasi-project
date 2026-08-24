package com.kasi.backend.provider.spi;

import java.time.LocalDateTime;

public record ProviderDramaContentRecord(
        String externalContentId,
        int sequenceNo,
        String title,
        boolean free,
        Integer durationSeconds,
        LocalDateTime remoteUpdatedAt) {
}

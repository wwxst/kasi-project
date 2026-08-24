package com.kasi.backend.drama.task;

import com.kasi.backend.drama.service.DramaCatalogSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.promotion.drama.sync", name = "scheduler-enabled",
        havingValue = "true", matchIfMissing = true)
public class DramaCatalogScheduler {
    private final DramaCatalogSyncService syncService;

    @Scheduled(fixedDelayString = "${app.promotion.drama.sync.fixed-delay:5m}")
    public void processDueDramas() {
        syncService.processDueBatch();
    }
}

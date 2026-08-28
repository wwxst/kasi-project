package com.kasi.backend.drama.download.task;

import com.kasi.backend.drama.download.service.DramaDownloadTaskService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DramaDownloadCleanupScheduler {
    private final DramaDownloadTaskService service;

    public DramaDownloadCleanupScheduler(DramaDownloadTaskService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.drama.download.cleanup-fixed-delay:1h}")
    public void cleanupExpired() {
        service.cleanupExpired();
    }
}

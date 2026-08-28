package com.kasi.backend.drama.download.task;

import com.kasi.backend.drama.download.service.DramaDownloadTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DramaDownloadCleanupSchedulerTest {

    @Test
    @DisplayName("清理调度器调用下载任务过期清理")
    void schedulerDelegatesExpiredCleanup() {
        DramaDownloadTaskService service = mock(DramaDownloadTaskService.class);

        new DramaDownloadCleanupScheduler(service).cleanupExpired();

        verify(service).cleanupExpired();
    }
}

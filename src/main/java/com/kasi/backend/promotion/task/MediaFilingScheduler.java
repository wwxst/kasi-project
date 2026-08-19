package com.kasi.backend.promotion.task;

import com.kasi.backend.promotion.service.MediaFilingTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.promotion.filing", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class MediaFilingScheduler {
    private final MediaFilingTaskService taskService;

    @Scheduled(fixedDelayString = "${app.promotion.filing.fixed-delay:30s}")
    public void processDueFilings() {
        taskService.processDueBatch();
    }
}

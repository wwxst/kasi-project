package com.kasi.backend.scheduledtask.task;

import com.kasi.backend.scheduledtask.service.ScheduledTaskDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.scheduled-task", name = "scheduler-enabled",
        havingValue = "true", matchIfMissing = true)
public class ScheduledTaskScheduler {
    private final ScheduledTaskDispatchService dispatchService;

    @Scheduled(fixedDelayString = "${app.scheduled-task.fixed-delay:1m}")
    public void processDueTasks() {
        dispatchService.processDueBatch();
    }
}

package com.kasi.backend.scheduledtask.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "app.scheduled-task")
public class ScheduledTaskProperties {
    private boolean schedulerEnabled = true;
    private Duration fixedDelay = Duration.ofMinutes(1);
    private int batchSize = 10;
    private Duration leaseDuration = Duration.ofMinutes(2);
}

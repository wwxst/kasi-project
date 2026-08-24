package com.kasi.backend.promotion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.promotion.filing")
public class MediaFilingProperties {
    private boolean schedulerEnabled = true;
    private Duration fixedDelay = Duration.ofSeconds(30);
    private int batchSize = 50;
    private Duration leaseDuration = Duration.ofMinutes(2);
    private Duration firstQueryDelay = Duration.ofMinutes(1);
    private Duration pendingQueryInterval = Duration.ofMinutes(5);
    private Duration approvedQueryInterval = Duration.ofHours(24);
    private int maxPendingRetries = 10;
    private List<Duration> retryDelays = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofMinutes(60));
}

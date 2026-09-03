package com.kasi.backend.drama.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.promotion.drama.content-sync")
public class DramaContentSyncProperties {
    private int batchSize = 50;
    private int candidatePageSize = 500;
    private Duration leaseDuration = Duration.ofMinutes(2);
    private int maxRetries = 5;
    private List<Duration> retryDelays = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15),
            Duration.ofMinutes(30), Duration.ofMinutes(60));
}

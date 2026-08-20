package com.kasi.backend.drama.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.promotion.drama.sync")
public class DramaSyncProperties {
    private boolean schedulerEnabled = true;
    private Duration fixedDelay = Duration.ofMinutes(5);
    private int batchSize = 10;
    private int pageSize = 100;
    private Duration leaseDuration = Duration.ofMinutes(2);
    private List<String> languages = List.of("ENGLISH");
}

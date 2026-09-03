package com.kasi.backend.drama.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.promotion.drama.sync")
public class DramaSyncProperties {
    public static final List<String> DEFAULT_LANGUAGES = List.of(
            "ENGLISH", "SPANISH", "PORTUGUESE", "DEUTSCH", "FRENCH",
            "BAHASA_INDONESIA", "KOREAN", "ARAB", "THAI", "JAPANESE",
            "TRADITIONAL_CHINESE", "POLISH", "TURKISH");

    private boolean schedulerEnabled = true;
    private Duration fixedDelay = Duration.ofMinutes(5);
    private int batchSize = 10;
    private int pageSize = 100;
    private Duration leaseDuration = Duration.ofMinutes(2);
    private List<String> languages = DEFAULT_LANGUAGES;
}

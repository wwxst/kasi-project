package com.kasi.backend.promotion.config;

import com.kasi.backend.drama.config.DramaSyncProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({MediaFilingProperties.class, DramaSyncProperties.class})
public class MediaFilingTaskConfig {
}

package com.kasi.backend.drama.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DramaContentSyncProperties.class)
public class DramaContentSyncConfig {
}

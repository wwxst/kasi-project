package com.kasi.backend.scheduledtask.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ScheduledTaskProperties.class)
public class ScheduledTaskConfig {
}

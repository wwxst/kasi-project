package com.kasi.backend.drama.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class DramaSyncTaskConfig {
    @Bean(name = "dramaSyncTaskExecutor")
    public ThreadPoolTaskExecutor dramaSyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("drama-sync-");
        executor.initialize();
        return executor;
    }
}

package com.retailzw.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SupportSchedulerConfig {
    @Bean("supportScheduler")
    public ThreadPoolTaskScheduler supportScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("support-events-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}

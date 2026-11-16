package com.luzihao.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncExecutorConfig {

    @Bean("diagnoseExecutor")
    public ThreadPoolTaskExecutor diagnoseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("diagnose-");
        executor.setCorePoolSize(2);          // 教学配置；生产按模型 QPS 与预算评估
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.initialize();
        return executor;
    }
}

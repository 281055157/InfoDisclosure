package com.example.disclosurereview.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncReviewConfig {

    @Bean(name = "reviewTaskExecutor")
    public Executor reviewTaskExecutor(ReviewProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getExecutor().getCorePoolSize());
        executor.setMaxPoolSize(properties.getExecutor().getMaxPoolSize());
        executor.setQueueCapacity(properties.getExecutor().getQueueCapacity());
        executor.setThreadNamePrefix(properties.getExecutor().getThreadNamePrefix());
        executor.initialize();
        return executor;
    }

    @Bean(name = "governanceToolExecutor")
    public Executor governanceToolExecutor(FeedbackGovernanceProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int threads = properties.getAgent().getParallelToolThreads();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(Math.max(threads * 8, 32));
        executor.setThreadNamePrefix("governance-tool-");
        executor.initialize();
        return executor;
    }
}

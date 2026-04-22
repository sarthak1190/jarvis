package com.jarvis.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Application-wide bean configuration.
 * Configures HTTP clients, thread pools, and shared infrastructure.
 */
@Configuration
public class AppConfig {

    /**
     * Shared OkHttp client for all external API calls.
     * Configured with reasonable timeouts for AI API interactions.
     */
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * Async executor for command processing and background tasks.
     * Thread pool sized for concurrent command execution.
     */
    @Bean(name = "jarvisExecutor")
    public Executor jarvisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("jarvis-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

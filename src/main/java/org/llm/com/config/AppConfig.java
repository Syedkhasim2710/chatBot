package org.llm.com.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Core application configuration.
 *
 * <p><b>Virtual Threads</b>: The Tomcat request thread pool uses virtual
 * threads via {@code spring.threads.virtual.enabled=true} in application.yml.
 *
 * <p><b>Retry</b>: {@code @EnableRetry} activates Spring Retry so
 * {@code @Retryable} on the AI call automatically retries on rate-limit errors.
 */
@EnableAsync
@EnableScheduling
@EnableRetry
@Configuration
@ConfigurationPropertiesScan("org.llm.com.config")
public class AppConfig {

    /**
     * Jackson ObjectMapper with Java-8 date/time support (ISO-8601).
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Virtual-thread-backed async executor.
     * Used by any {@code @Async} service method + scheduled tasks.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        return new ConcurrentTaskExecutor(
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }
}


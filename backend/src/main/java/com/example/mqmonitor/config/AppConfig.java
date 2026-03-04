package com.example.mqmonitor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AppConfig {

    /**
     * Dedicated thread pool for concurrent MQ collection.
     * Named threads simplify debugging (visible in thread dumps and profilers).
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService mqCollectorExecutor(MonitorProperties props) {
        int size = props.getThreadPoolSize();
        ThreadFactory namedFactory = new NamedThreadFactory("mq-collector");
        return Executors.newFixedThreadPool(size, namedFactory);
    }

    /**
     * Allow the React dev server (Vite default port 5173) to call the API.
     * In production, serve the React build from the Spring Boot static folder
     * and remove this configuration entirely.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET")
                        .maxAge(60);
            }
        };
    }

    // ── Inner helper ──────────────────────────────────────────────────────────

    private static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
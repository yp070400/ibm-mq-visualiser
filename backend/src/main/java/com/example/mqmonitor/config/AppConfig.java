package com.example.mqmonitor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;
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
     * Servlet-level CORS filter — runs before Spring MVC, so it always applies.
     * Allows any localhost port (Vite dev server uses 5173-5177+).
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(60L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
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
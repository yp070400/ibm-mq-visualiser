package com.example.mqmonitor.scheduler;

import com.example.mqmonitor.service.MQMonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives periodic metrics collection.
 *
 * On startup: warms the cache immediately via ApplicationReadyEvent so the
 * first API request does not return empty results.
 *
 * fixedDelay (not fixedRate): next cycle starts only after the previous one
 * completes, preventing collection overlap if MQ is slow.
 */
@Component
public class MQPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(MQPollingScheduler.class);

    private final MQMonitoringService monitoringService;

    public MQPollingScheduler(MQMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Application ready — running initial MQ collection...");
        try {
            monitoringService.collectAll();
        } catch (Exception e) {
            log.error("Initial collection failed: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelayString = "#{${mq.monitor.polling-interval-seconds:30} * 1000}")
    public void poll() {
        log.debug("Scheduled MQ poll starting");
        try {
            monitoringService.collectAll();
        } catch (Exception e) {
            log.error("Scheduled collection failed: {}", e.getMessage(), e);
        }
    }
}
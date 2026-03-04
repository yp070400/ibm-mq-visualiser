package com.example.mqmonitor.service;

import com.example.mqmonitor.config.MonitorProperties;
import com.example.mqmonitor.model.ConnectionStatus;
import com.example.mqmonitor.model.QueueManagerConfig;
import com.example.mqmonitor.model.QueueManagerStats;
import com.example.mqmonitor.model.QueueStats;
import com.example.mqmonitor.mq.MQMetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Central service for MQ monitoring. Drives concurrent collection and maintains
 * the in-memory cache. All REST reads go through this service, never to MQ directly.
 */
@Service
public class MQMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MQMonitoringService.class);

    private final MonitorProperties  properties;
    private final MQMetricsCollector collector;
    private final ExecutorService    mqCollectorExecutor;

    private final ConcurrentHashMap<String, QueueManagerStats> cache = new ConcurrentHashMap<>();

    public MQMonitoringService(MonitorProperties properties,
                                MQMetricsCollector collector,
                                ExecutorService mqCollectorExecutor) {
        this.properties          = properties;
        this.collector           = collector;
        this.mqCollectorExecutor = mqCollectorExecutor;
    }

    public void collectAll() {
        List<QueueManagerConfig> targets = properties.enabledQueueManagers();
        if (targets.isEmpty()) {
            log.warn("No enabled queue managers configured — nothing to collect");
            return;
        }

        log.debug("Starting collection cycle for {} queue manager(s)", targets.size());
        long cycleStart = System.currentTimeMillis();

        List<CompletableFuture<Void>> futures = targets.stream()
                .map(config -> CompletableFuture
                        .runAsync(() -> collectOne(config), mqCollectorExecutor)
                        .exceptionally(ex -> {
                            log.error("Unexpected error in collection task for {}: {}",
                                    config.getName(), ex.getMessage());
                            return null;
                        }))
                .toList();

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(properties.getCollectionTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Collection cycle timed out or was interrupted after {}s",
                    properties.getCollectionTimeoutSeconds());
        }

        log.debug("Collection cycle complete in {}ms", System.currentTimeMillis() - cycleStart);
    }

    private void collectOne(QueueManagerConfig config) {
        QueueManagerStats result = collector.collect(config);
        cache.put(config.getName(), result);
    }

    public Collection<QueueManagerStats> getAllQueueManagerStats() {
        List<QueueManagerStats> result = new ArrayList<>(cache.values());

        for (QueueManagerConfig cfg : properties.getQueueManagers()) {
            if (!cache.containsKey(cfg.getName())) {
                result.add(buildPendingResult(cfg));
            }
        }

        result.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return Collections.unmodifiableList(result);
    }

    public Optional<QueueManagerStats> getQueueManagerStats(String name) {
        QueueManagerStats stats = cache.get(name);
        if (stats != null) return Optional.of(stats);

        return properties.getQueueManagers().stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst()
                .map(this::buildPendingResult);
    }

    public Optional<QueueStats> getQueueStats(String queueManagerName, String queueName) {
        return getQueueManagerStats(queueManagerName)
                .stream()
                .flatMap(qm -> qm.getQueues() == null
                        ? java.util.stream.Stream.empty()
                        : qm.getQueues().stream())
                .filter(q -> q.getQueueName().equalsIgnoreCase(queueName))
                .findFirst();
    }

    public List<String> getAllQueueManagerNames() {
        return properties.getQueueManagers().stream()
                .map(QueueManagerConfig::getName)
                .sorted()
                .toList();
    }

    private QueueManagerStats buildPendingResult(QueueManagerConfig cfg) {
        return QueueManagerStats.builder()
                .name(cfg.getName())
                .host(cfg.getHost())
                .port(cfg.getPort())
                .connectionStatus(ConnectionStatus.DISCONNECTED)
                .errorMessage("Not yet collected")
                .queues(List.of())
                .build();
    }
}
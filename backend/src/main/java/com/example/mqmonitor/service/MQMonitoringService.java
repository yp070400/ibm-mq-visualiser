package com.example.mqmonitor.service;

import com.example.mqmonitor.config.IbmMqProperties;
import com.example.mqmonitor.config.IbmMqProperties.AdditionalConnection;
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
 *
 * Queue manager connection settings are read from IbmMqProperties (ibm.mq.*).
 * Monitoring settings (polling, thresholds) come from MonitorProperties (mq.monitor.*).
 */
@Service
public class MQMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MQMonitoringService.class);

    private final MonitorProperties            monitorProperties;
    private final MQMetricsCollector           collector;
    private final ExecutorService              mqCollectorExecutor;
    private final List<QueueManagerConfig>     queueManagerConfigs;

    private final ConcurrentHashMap<String, QueueManagerStats> cache = new ConcurrentHashMap<>();

    public MQMonitoringService(IbmMqProperties ibmMqProperties,
                                MonitorProperties monitorProperties,
                                MQMetricsCollector collector,
                                ExecutorService mqCollectorExecutor) {
        this.monitorProperties   = monitorProperties;
        this.collector           = collector;
        this.mqCollectorExecutor = mqCollectorExecutor;
        this.queueManagerConfigs = buildConfigs(ibmMqProperties, monitorProperties);

        if (queueManagerConfigs.isEmpty()) {
            log.warn("No queue managers configured under ibm.mq.*");
        } else {
            log.info("Loaded {} queue manager configuration(s): {}",
                    queueManagerConfigs.size(),
                    queueManagerConfigs.stream().map(QueueManagerConfig::getName).toList());
        }
    }

    public void collectAll() {
        if (queueManagerConfigs.isEmpty()) {
            log.warn("No queue managers configured — nothing to collect");
            return;
        }

        log.debug("Starting collection cycle for {} queue manager(s)", queueManagerConfigs.size());
        long cycleStart = System.currentTimeMillis();

        List<CompletableFuture<Void>> futures = queueManagerConfigs.stream()
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
                    .get(monitorProperties.getCollectionTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Collection cycle timed out or was interrupted after {}s",
                    monitorProperties.getCollectionTimeoutSeconds());
        }

        log.debug("Collection cycle complete in {}ms", System.currentTimeMillis() - cycleStart);
    }

    private void collectOne(QueueManagerConfig config) {
        QueueManagerStats result = collector.collect(config);
        cache.put(config.getName(), result);
    }

    public Collection<QueueManagerStats> getAllQueueManagerStats() {
        List<QueueManagerStats> result = new ArrayList<>(cache.values());

        for (QueueManagerConfig cfg : queueManagerConfigs) {
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

        return queueManagerConfigs.stream()
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
        return queueManagerConfigs.stream()
                .map(QueueManagerConfig::getName)
                .sorted()
                .toList();
    }

    public Optional<QueueManagerConfig> getQueueManagerConfig(String name) {
        return queueManagerConfigs.stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    // ── Config builder ────────────────────────────────────────────────────────

    private static List<QueueManagerConfig> buildConfigs(IbmMqProperties ibmMq,
                                                          MonitorProperties monitor) {
        List<QueueManagerConfig> configs = new ArrayList<>();

        // Primary queue manager
        if (ibmMq.getQueueManager() != null && !ibmMq.getQueueManager().isBlank()) {
            configs.add(toConfig(
                    ibmMq.getQueueManager(),
                    ibmMq.getConnName(),
                    ibmMq.getChannel(),
                    ibmMq.getUser(),
                    ibmMq.getPassword(),
                    ibmMq.getSslCipherSuite(),
                    monitor));
        }

        // Additional queue managers
        for (AdditionalConnection ac : ibmMq.getAdditionalConnections()) {
            if (ac.isEnabled() && ac.getQueueManager() != null && !ac.getQueueManager().isBlank()) {
                configs.add(toConfig(
                        ac.getQueueManager(),
                        ac.getConnName(),
                        ac.getChannel(),
                        ac.getUser(),
                        ac.getPassword(),
                        ac.getSslCipherSuite(),
                        monitor));
            }
        }

        return Collections.unmodifiableList(configs);
    }

    private static QueueManagerConfig toConfig(String queueManager, String connName,
                                                String channel, String user, String password,
                                                String sslCipherSuite, MonitorProperties monitor) {
        QueueManagerConfig cfg = new QueueManagerConfig();
        cfg.setName(queueManager);
        cfg.setConnectionName(connName);
        cfg.setHost(extractHost(connName));
        cfg.setPort(extractPort(connName));
        cfg.setChannel(channel);
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setSslCipherSuite(sslCipherSuite);
        cfg.setQueuePattern(monitor.getQueuePattern());
        cfg.setExcludeSystemQueues(monitor.isExcludeSystemQueues());
        cfg.setEnabled(true);
        return cfg;
    }

    /** Extracts the hostname from "host(port)" or "host1(1414),host2(1414)". Returns connName as-is if unparseable. */
    private static String extractHost(String connName) {
        if (connName == null || connName.isBlank()) return null;
        int paren = connName.indexOf('(');
        return paren > 0 ? connName.substring(0, paren).trim() : connName.trim();
    }

    /** Extracts the port from "host(port)". Returns 1414 if unparseable or multi-instance. */
    private static int extractPort(String connName) {
        if (connName == null || connName.isBlank()) return 1414;
        int open  = connName.indexOf('(');
        int close = connName.indexOf(')');
        if (open > 0 && close > open) {
            try {
                return Integer.parseInt(connName.substring(open + 1, close).trim());
            } catch (NumberFormatException ignored) {}
        }
        return 1414;
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

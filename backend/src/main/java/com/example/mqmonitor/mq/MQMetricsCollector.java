package com.example.mqmonitor.mq;

import com.example.mqmonitor.config.MonitorProperties;
import com.example.mqmonitor.model.ConnectionStatus;
import com.example.mqmonitor.model.QueueHealth;
import com.example.mqmonitor.model.QueueManagerConfig;
import com.example.mqmonitor.model.QueueManagerStats;
import com.example.mqmonitor.model.QueueStats;
import com.example.mqmonitor.mq.MQPCFClient.QueueDefinition;
import com.example.mqmonitor.mq.MQPCFClient.QueueStatus;
import com.ibm.mq.MQException;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.pcf.PCFMessageAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Collects all queue metrics for a single Queue Manager in one collection cycle.
 * Two PCF round-trips total: INQUIRE_Q + INQUIRE_Q_STATUS.
 */
@Component
public class MQMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(MQMetricsCollector.class);

    private final MQConnectionManager connectionManager;
    private final MQPCFClient         pcfClient;
    private final MonitorProperties   properties;

    public MQMetricsCollector(MQConnectionManager connectionManager,
                               MQPCFClient pcfClient,
                               MonitorProperties properties) {
        this.connectionManager = connectionManager;
        this.pcfClient         = pcfClient;
        this.properties        = properties;
    }

    public QueueManagerStats collect(QueueManagerConfig config) {
        long startMs = System.currentTimeMillis();
        String qmName = config.getName();

        try {
            log.debug("Collecting metrics for {}", qmName);
            PCFMessageAgent agent = connectionManager.getAgent(config);

            String pattern = config.getQueuePattern();

            Map<String, QueueDefinition> definitions = pcfClient.inquireQueues(agent, pattern);
            Map<String, QueueStatus>     statuses    = pcfClient.inquireQueueStatus(agent, pattern);

            List<QueueStats> queueStats = mergeAndBuild(definitions, statuses, qmName);

            if (config.isExcludeSystemQueues()) {
                queueStats = queueStats.stream()
                        .filter(q -> !isSystemQueue(q.getQueueName()))
                        .toList();
            }

            return buildSuccessResult(config, queueStats, startMs);

        } catch (Exception e) {
            String reason = extractReason(e);
            log.error("Collection failed for {}: {}", qmName, reason);
            connectionManager.markDisconnected(qmName, reason);
            return buildErrorResult(config, reason, startMs);
        }
    }

    private List<QueueStats> mergeAndBuild(Map<String, QueueDefinition> definitions,
                                            Map<String, QueueStatus>     statuses,
                                            String qmName) {
        int warnPct = properties.getWarningThresholdPercent();
        int critPct = properties.getCriticalThresholdPercent();
        List<QueueStats> result = new ArrayList<>();

        for (String name : MQPCFClient.mergedNames(definitions, statuses)) {
            QueueDefinition def    = definitions.get(name);
            QueueStatus     status = statuses.get(name);

            int     currentDepth = status != null ? status.currentDepth()    : 0;
            int     openInput    = status != null ? status.openInputCount()  : 0;
            int     openOutput   = status != null ? status.openOutputCount() : 0;
            int     maxDepth     = def    != null ? def.maxDepth()           : 0;
            String  queueType    = def    != null ? def.type()               : "UNKNOWN";
            boolean inhibitGet   = def    != null && def.inhibitGet();
            boolean inhibitPut   = def    != null && def.inhibitPut();

            double depthPct = maxDepth > 0 ? (currentDepth * 100.0) / maxDepth : 0.0;
            QueueHealth health = QueueHealth.evaluate(currentDepth, maxDepth, warnPct, critPct);

            result.add(QueueStats.builder()
                    .queueName(name)
                    .queueManagerName(qmName)
                    .currentDepth(currentDepth)
                    .maxDepth(maxDepth)
                    .openInputCount(openInput)
                    .openOutputCount(openOutput)
                    .queueType(queueType)
                    .inhibitGet(inhibitGet)
                    .inhibitPut(inhibitPut)
                    .depthPercent(Math.round(depthPct * 10.0) / 10.0)
                    .health(health)
                    .lastUpdated(Instant.now())
                    .build());
        }

        return result;
    }

    private QueueManagerStats buildSuccessResult(QueueManagerConfig config,
                                                  List<QueueStats> queues, long startMs) {
        long duration = System.currentTimeMillis() - startMs;

        long normal   = queues.stream().filter(q -> q.getHealth() == QueueHealth.NORMAL).count();
        long warning  = queues.stream().filter(q -> q.getHealth() == QueueHealth.WARNING).count();
        long critical = queues.stream().filter(q -> q.getHealth() == QueueHealth.CRITICAL).count();
        long unknown  = queues.stream().filter(q -> q.getHealth() == QueueHealth.UNKNOWN).count();

        log.info("Collected {} queues from {} in {}ms (W={} C={})",
                queues.size(), config.getName(), duration, warning, critical);

        return QueueManagerStats.builder()
                .name(config.getName())
                .host(config.getHost())
                .port(config.getPort())
                .connectionStatus(ConnectionStatus.CONNECTED)
                .queues(queues)
                .totalQueues(queues.size())
                .normalQueues((int) normal)
                .warningQueues((int) warning)
                .criticalQueues((int) critical)
                .unknownQueues((int) unknown)
                .lastCollected(Instant.now())
                .collectionDurationMs(duration)
                .build();
    }

    private QueueManagerStats buildErrorResult(QueueManagerConfig config,
                                                String errorMsg, long startMs) {
        return QueueManagerStats.builder()
                .name(config.getName())
                .host(config.getHost())
                .port(config.getPort())
                .connectionStatus(ConnectionStatus.ERROR)
                .errorMessage(errorMsg)
                .queues(List.of())
                .totalQueues(0)
                .normalQueues(0)
                .warningQueues(0)
                .criticalQueues(0)
                .unknownQueues(0)
                .lastCollected(Instant.now())
                .collectionDurationMs(System.currentTimeMillis() - startMs)
                .build();
    }

    private boolean isSystemQueue(String name) {
        return name.startsWith("SYSTEM.") || name.startsWith("AMQ.");
    }

    private String extractReason(Exception e) {
        if (e instanceof MQException mq) {
            return String.format("MQException: reasonCode=%d (%s)", mq.getReason(), mq.getMessage());
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    public static boolean isConnectionError(Exception e) {
        if (e instanceof MQException mq) {
            int rc = mq.getReason();
            return rc == CMQC.MQRC_CONNECTION_BROKEN
                || rc == CMQC.MQRC_CONNECTION_ERROR
                || rc == CMQC.MQRC_Q_MGR_NOT_AVAILABLE
                || rc == CMQC.MQRC_Q_MGR_QUIESCING
                || rc == CMQC.MQRC_CHANNEL_NOT_AVAILABLE
                || rc == 2009
                || rc == 2018;
        }
        return false;
    }
}
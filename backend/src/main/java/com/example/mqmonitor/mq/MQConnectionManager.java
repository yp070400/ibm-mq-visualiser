package com.example.mqmonitor.mq;

import com.example.mqmonitor.model.ConnectionStatus;
import com.example.mqmonitor.model.QueueManagerConfig;
import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.pcf.PCFMessageAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages MQQueueManager + PCFMessageAgent lifecycle per Queue Manager.
 *
 * SSL is handled entirely by JVM system properties set in SSLConfig.init().
 * This class only passes the cipher suite name — no SSLSocketFactory needed.
 *
 * Connection flow per QM:
 *   1. Build connection Hashtable (host, channel, credentials, cipher suite)
 *   2. new MQQueueManager(name, props)  — establishes TCP + TLS + auth
 *   3. new PCFMessageAgent(qmgr)        — ready to send PCF commands
 */
@Component
public class MQConnectionManager implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(MQConnectionManager.class);

    private final ConcurrentHashMap<String, MQQueueManager>   queueManagers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PCFMessageAgent>  agents        = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock>    locks         = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConnectionStatus> statuses      = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    public PCFMessageAgent getAgent(QueueManagerConfig config) throws MQException {
        PCFMessageAgent existing = agents.get(config.getName());
        if (existing != null) return existing;
        return connectWithLock(config);
    }

    public void markDisconnected(String qmName, String reason) {
        log.warn("QM {} marked disconnected: {}", qmName, reason);
        statuses.put(qmName, ConnectionStatus.DISCONNECTED);
        destroyConnection(qmName);
    }

    public ConnectionStatus getStatus(String qmName) {
        return statuses.getOrDefault(qmName, ConnectionStatus.DISCONNECTED);
    }

    public Map<String, ConnectionStatus> getAllStatuses() {
        return Collections.unmodifiableMap(statuses);
    }

    // ── Connection lifecycle ──────────────────────────────────────────────────

    private PCFMessageAgent connectWithLock(QueueManagerConfig config) throws MQException {
        ReentrantLock lock = locks.computeIfAbsent(config.getName(), k -> new ReentrantLock());
        lock.lock();
        try {
            // Re-check after acquiring lock — another thread may have connected
            PCFMessageAgent existing = agents.get(config.getName());
            if (existing != null) return existing;
            return createConnection(config);
        } finally {
            lock.unlock();
        }
    }

    private PCFMessageAgent createConnection(QueueManagerConfig config) throws MQException {
        String name = config.getName();
        log.info("Connecting to QM {} ({})", name, config.resolvedConnectionName());
        statuses.put(name, ConnectionStatus.CONNECTING);

        try {
            Hashtable<String, Object> props = buildProps(config);

            MQQueueManager qmgr = new MQQueueManager(name, props);
            queueManagers.put(name, qmgr);

            PCFMessageAgent agent = new PCFMessageAgent(qmgr);
            agents.put(name, agent);

            statuses.put(name, ConnectionStatus.CONNECTED);
            log.info("Connected to QM {}", name);
            return agent;

        } catch (MQException e) {
            statuses.put(name, ConnectionStatus.ERROR);
            queueManagers.remove(name);
            log.error("Failed to connect to QM {}: reasonCode={} ({})",
                    name, e.getReason(), mqReasonText(e.getReason()));
            throw e;
        }
    }

    private Hashtable<String, Object> buildProps(QueueManagerConfig config) {
        Hashtable<String, Object> props = new Hashtable<>();

        props.put("connectionName", config.resolvedConnectionName());
        props.put("channel",        config.getChannel());

        if (StringUtils.hasText(config.getUsername())) {
            props.put("userID",   config.getUsername());
        }
        if (StringUtils.hasText(config.getPassword())) {
            props.put("password", config.getPassword());
        }

        // SSL: only the cipher suite is needed here.
        // Keystore / truststore are set as JVM system properties by SSLConfig.init()
        // before the first connection attempt.
        if (StringUtils.hasText(config.getSslCipherSuite())) {
            props.put("sslCipherSuite", config.getSslCipherSuite());
            log.debug("QM {}: using cipher suite {}", config.getName(), config.getSslCipherSuite());
        }

        return props;
    }

    private void destroyConnection(String qmName) {
        PCFMessageAgent agent = agents.remove(qmName);
        if (agent != null) {
            try { agent.disconnect(); } catch (Exception e) {
                log.warn("Error closing PCFAgent for {}: {}", qmName, e.getMessage());
            }
        }
        MQQueueManager qmgr = queueManagers.remove(qmName);
        if (qmgr != null) {
            try { qmgr.disconnect(); } catch (MQException e) {
                log.warn("Error closing MQQueueManager for {}: {}", qmName, e.getMessage());
            }
        }
    }

    // ── Shutdown ──────────────────────────────────────────────────────────────

    @Override
    public void destroy() {
        log.info("Shutting down {} MQ connection(s)...", queueManagers.size());
        queueManagers.keySet().forEach(this::destroyConnection);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String mqReasonText(int rc) {
        return switch (rc) {
            case 2035 -> "MQRC_NOT_AUTHORIZED — check username/password";
            case 2063 -> "MQRC_SECURITY_ERROR — check SSL config";
            case 2393 -> "MQRC_SSL_INITIALIZATION_ERROR — check cipher suite and JKS files";
            case 2495 -> "MQRC_JSSE_ERROR — check ssl-cipher-suite matches server channel SSLCIPH";
            case 2538 -> "MQRC_HOST_NOT_AVAILABLE — check host/port";
            case 2059 -> "MQRC_Q_MGR_NOT_AVAILABLE — check QM name and channel";
            case 2085 -> "MQRC_UNKNOWN_OBJECT_NAME — check queue manager name";
            default   -> "see IBM MQ reason codes";
        };
    }
}

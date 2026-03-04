package com.example.mqmonitor.mq;

import com.example.mqmonitor.config.MonitorProperties;
import com.example.mqmonitor.config.SSLConfig;
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
 * Lifecycle manager for MQQueueManager + PCFMessageAgent pairs.
 *
 * PCFMessageAgent has no (String, Hashtable) constructor — the only way to pass
 * connection properties (credentials, SSL) is to create an MQQueueManager first
 * and wrap it: new PCFMessageAgent(mqQueueManager).
 *
 * Connection state machine per QM:
 *   absent / DISCONNECTED → CONNECTING → CONNECTED
 *                                      → ERROR  (stays until next poll attempt)
 * A per-QM ReentrantLock ensures only one thread reconnects at a time.
 */
@Component
public class MQConnectionManager implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(MQConnectionManager.class);

    private final MonitorProperties properties;
    private final SSLConfig         sslConfig;

    // Keyed by QM name
    private final ConcurrentHashMap<String, MQQueueManager>  queueManagers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PCFMessageAgent> agents        = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock>   locks         = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConnectionStatus> statuses      = new ConcurrentHashMap<>();

    public MQConnectionManager(MonitorProperties properties, SSLConfig sslConfig) {
        this.properties = properties;
        this.sslConfig  = sslConfig;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns a ready PCFMessageAgent for the given QM, connecting if needed.
     * Thread-safe; concurrent callers for the same QM serialise on a per-QM lock.
     */
    public PCFMessageAgent getAgent(QueueManagerConfig config) throws MQException {
        String name = config.getName();

        PCFMessageAgent existing = agents.get(name);
        if (existing != null) {
            return existing; // fast path
        }

        return connectWithLock(config);
    }

    /**
     * Destroys the stale agent/connection so the next getAgent() call reconnects.
     * Called by MQMetricsCollector when an MQ operation fails with a connection error.
     */
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
        String name = config.getName();
        ReentrantLock lock = locks.computeIfAbsent(name, k -> new ReentrantLock());

        lock.lock();
        try {
            // Another thread may have connected while we waited
            PCFMessageAgent existing = agents.get(name);
            if (existing != null) {
                return existing;
            }
            return createConnection(config);
        } finally {
            lock.unlock();
        }
    }

    private PCFMessageAgent createConnection(QueueManagerConfig config) throws MQException {
        String name = config.getName();
        log.info("Connecting to queue manager {} at {}:{}", name, config.getHost(), config.getPort());
        statuses.put(name, ConnectionStatus.CONNECTING);

        try {
            // Step 1: create MQQueueManager with connection properties (supports auth + SSL)
            Hashtable<String, Object> props = buildConnectionProperties(config);
            MQQueueManager qmgr = new MQQueueManager(name, props);
            queueManagers.put(name, qmgr);

            // Step 2: wrap in PCFMessageAgent — this is the only constructor that carries
            // the already-authenticated connection context
            PCFMessageAgent agent = new PCFMessageAgent(qmgr);
            agents.put(name, agent);

            statuses.put(name, ConnectionStatus.CONNECTED);
            log.info("Connected to queue manager {}", name);
            return agent;

        } catch (MQException e) {
            statuses.put(name, ConnectionStatus.ERROR);
            queueManagers.remove(name); // cleanup partial state
            log.error("Failed to connect to queue manager {}: reasonCode={}", name, e.getReason());
            throw e;
        }
    }

    private void destroyConnection(String qmName) {
        PCFMessageAgent agent = agents.remove(qmName);
        if (agent != null) {
            try { agent.disconnect(); }
            catch (Exception e) { log.warn("Error disconnecting PCFAgent for {}: {}", qmName, e.getMessage()); }
        }

        MQQueueManager qmgr = queueManagers.remove(qmName);
        if (qmgr != null) {
            try { qmgr.disconnect(); }
            catch (MQException e) { log.warn("Error disconnecting MQQueueManager for {}: {}", qmName, e.getMessage()); }
        }
    }

    private Hashtable<String, Object> buildConnectionProperties(QueueManagerConfig config) {
        Hashtable<String, Object> props = new Hashtable<>();

        // IBM MQ Java client connection property keys
        props.put("hostname", config.getHost());
        props.put("port",     config.getPort());
        props.put("channel",  config.getChannel());

        if (StringUtils.hasText(config.getUsername())) {
            props.put("userID",   config.getUsername());
        }
        if (StringUtils.hasText(config.getPassword())) {
            props.put("password", config.getPassword());
        }

        if (StringUtils.hasText(config.getSslCipherSuite())) {
            props.put("sslCipherSuite", config.getSslCipherSuite());

            var factory = sslConfig.buildSocketFactory(config);
            if (factory != null) {
                props.put("sslSocketFactory", factory);
            }
        }

        return props;
    }

    // ── Shutdown ──────────────────────────────────────────────────────────────

    @Override
    public void destroy() {
        log.info("Shutting down {} MQ connection(s)...", queueManagers.size());
        queueManagers.keySet().forEach(this::destroyConnection);
    }
}
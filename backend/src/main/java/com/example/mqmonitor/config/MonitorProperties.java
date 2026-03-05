package com.example.mqmonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Monitoring-specific settings (polling schedule, thresholds, queue filtering).
 * Connection settings live in IbmMqProperties (ibm.mq.*).
 */
@ConfigurationProperties(prefix = "mq.monitor")
public class MonitorProperties {

    private int     pollingIntervalSeconds   = 30;
    private int     threadPoolSize           = 10;
    private int     collectionTimeoutSeconds = 20;
    private int     warningThresholdPercent  = 70;
    private int     criticalThresholdPercent = 90;

    /** Wildcard pattern applied to INQUIRE_Q and INQUIRE_Q_STATUS PCF commands. */
    private String  queuePattern             = "*";

    /** When true, queues starting with SYSTEM.* or AMQ.* are excluded from results. */
    private boolean excludeSystemQueues      = true;

    /** Global SSL keystore / truststore — shared across all queue managers. */
    private SslConfig ssl = new SslConfig();

    // ── Getters / Setters ────────────────────────────────────────────────────

    public int     getPollingIntervalSeconds()          { return pollingIntervalSeconds; }
    public void    setPollingIntervalSeconds(int v)     { this.pollingIntervalSeconds = v; }

    public int     getThreadPoolSize()                  { return threadPoolSize; }
    public void    setThreadPoolSize(int v)             { this.threadPoolSize = v; }

    public int     getCollectionTimeoutSeconds()        { return collectionTimeoutSeconds; }
    public void    setCollectionTimeoutSeconds(int v)   { this.collectionTimeoutSeconds = v; }

    public int     getWarningThresholdPercent()         { return warningThresholdPercent; }
    public void    setWarningThresholdPercent(int v)    { this.warningThresholdPercent = v; }

    public int     getCriticalThresholdPercent()        { return criticalThresholdPercent; }
    public void    setCriticalThresholdPercent(int v)   { this.criticalThresholdPercent = v; }

    public String  getQueuePattern()                    { return queuePattern; }
    public void    setQueuePattern(String v)            { this.queuePattern = v; }

    public boolean isExcludeSystemQueues()              { return excludeSystemQueues; }
    public void    setExcludeSystemQueues(boolean v)    { this.excludeSystemQueues = v; }

    public SslConfig getSsl()                           { return ssl; }
    public void      setSsl(SslConfig v)                { this.ssl = v; }

    // ── Nested SSL config ────────────────────────────────────────────────────

    /**
     * Global SSL stores applied to all MQ connections via JVM system properties.
     */
    public static class SslConfig {

        private String keyStore;
        private String keyStorePassword;
        private String trustStore;
        private String trustStorePassword;

        public String getKeyStore()                     { return keyStore; }
        public void   setKeyStore(String v)             { this.keyStore = v; }

        public String getKeyStorePassword()             { return keyStorePassword; }
        public void   setKeyStorePassword(String v)     { this.keyStorePassword = v; }

        public String getTrustStore()                   { return trustStore; }
        public void   setTrustStore(String v)           { this.trustStore = v; }

        public String getTrustStorePassword()           { return trustStorePassword; }
        public void   setTrustStorePassword(String v)   { this.trustStorePassword = v; }
    }
}

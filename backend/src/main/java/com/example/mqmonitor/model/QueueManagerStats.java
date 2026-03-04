package com.example.mqmonitor.model;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated snapshot for one Queue Manager: connection status plus all queue stats.
 */
public final class QueueManagerStats {

    private final String           name;
    private final String           host;
    private final int              port;
    private final ConnectionStatus connectionStatus;
    private final String           errorMessage;
    private final List<QueueStats> queues;
    private final int              totalQueues;
    private final int              normalQueues;
    private final int              warningQueues;
    private final int              criticalQueues;
    private final int              unknownQueues;
    private final Instant          lastCollected;
    private final long             collectionDurationMs;

    private QueueManagerStats(Builder b) {
        this.name                 = b.name;
        this.host                 = b.host;
        this.port                 = b.port;
        this.connectionStatus     = b.connectionStatus;
        this.errorMessage         = b.errorMessage;
        this.queues               = b.queues;
        this.totalQueues          = b.totalQueues;
        this.normalQueues         = b.normalQueues;
        this.warningQueues        = b.warningQueues;
        this.criticalQueues       = b.criticalQueues;
        this.unknownQueues        = b.unknownQueues;
        this.lastCollected        = b.lastCollected;
        this.collectionDurationMs = b.collectionDurationMs;
    }

    public static Builder builder() { return new Builder(); }

    public String           getName()                 { return name; }
    public String           getHost()                 { return host; }
    public int              getPort()                 { return port; }
    public ConnectionStatus getConnectionStatus()     { return connectionStatus; }
    public String           getErrorMessage()         { return errorMessage; }
    public List<QueueStats> getQueues()               { return queues; }
    public int              getTotalQueues()           { return totalQueues; }
    public int              getNormalQueues()          { return normalQueues; }
    public int              getWarningQueues()         { return warningQueues; }
    public int              getCriticalQueues()        { return criticalQueues; }
    public int              getUnknownQueues()         { return unknownQueues; }
    public Instant          getLastCollected()         { return lastCollected; }
    public long             getCollectionDurationMs()  { return collectionDurationMs; }
    public boolean          hasAlerts()                { return warningQueues > 0 || criticalQueues > 0; }

    public static final class Builder {
        private String           name;
        private String           host;
        private int              port;
        private ConnectionStatus connectionStatus;
        private String           errorMessage;
        private List<QueueStats> queues;
        private int              totalQueues;
        private int              normalQueues;
        private int              warningQueues;
        private int              criticalQueues;
        private int              unknownQueues;
        private Instant          lastCollected;
        private long             collectionDurationMs;

        public Builder name(String v)                        { this.name = v;                 return this; }
        public Builder host(String v)                        { this.host = v;                 return this; }
        public Builder port(int v)                           { this.port = v;                 return this; }
        public Builder connectionStatus(ConnectionStatus v)  { this.connectionStatus = v;     return this; }
        public Builder errorMessage(String v)                { this.errorMessage = v;         return this; }
        public Builder queues(List<QueueStats> v)            { this.queues = v;               return this; }
        public Builder totalQueues(int v)                    { this.totalQueues = v;          return this; }
        public Builder normalQueues(int v)                   { this.normalQueues = v;         return this; }
        public Builder warningQueues(int v)                  { this.warningQueues = v;        return this; }
        public Builder criticalQueues(int v)                 { this.criticalQueues = v;       return this; }
        public Builder unknownQueues(int v)                  { this.unknownQueues = v;        return this; }
        public Builder lastCollected(Instant v)              { this.lastCollected = v;        return this; }
        public Builder collectionDurationMs(long v)          { this.collectionDurationMs = v; return this; }

        public QueueManagerStats build() { return new QueueManagerStats(this); }
    }
}
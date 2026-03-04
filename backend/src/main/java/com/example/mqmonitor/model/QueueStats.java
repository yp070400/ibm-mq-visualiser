package com.example.mqmonitor.model;

import java.time.Instant;

/**
 * Snapshot of a single queue's metrics at a point in time.
 */
public final class QueueStats {

    private final String      queueName;
    private final String      queueManagerName;
    private final int         currentDepth;
    private final int         maxDepth;
    private final int         openInputCount;
    private final int         openOutputCount;
    private final String      queueType;
    private final boolean     inhibitGet;
    private final boolean     inhibitPut;
    private final double      depthPercent;
    private final QueueHealth health;
    private final Instant     lastUpdated;

    private QueueStats(Builder b) {
        this.queueName        = b.queueName;
        this.queueManagerName = b.queueManagerName;
        this.currentDepth     = b.currentDepth;
        this.maxDepth         = b.maxDepth;
        this.openInputCount   = b.openInputCount;
        this.openOutputCount  = b.openOutputCount;
        this.queueType        = b.queueType;
        this.inhibitGet       = b.inhibitGet;
        this.inhibitPut       = b.inhibitPut;
        this.depthPercent     = b.depthPercent;
        this.health           = b.health;
        this.lastUpdated      = b.lastUpdated;
    }

    public static Builder builder() { return new Builder(); }

    public String      getQueueName()        { return queueName; }
    public String      getQueueManagerName() { return queueManagerName; }
    public int         getCurrentDepth()     { return currentDepth; }
    public int         getMaxDepth()         { return maxDepth; }
    public int         getOpenInputCount()   { return openInputCount; }
    public int         getOpenOutputCount()  { return openOutputCount; }
    public String      getQueueType()        { return queueType; }
    public boolean     isInhibitGet()        { return inhibitGet; }
    public boolean     isInhibitPut()        { return inhibitPut; }
    public double      getDepthPercent()     { return depthPercent; }
    public QueueHealth getHealth()           { return health; }
    public Instant     getLastUpdated()      { return lastUpdated; }

    public static final class Builder {
        private String      queueName;
        private String      queueManagerName;
        private int         currentDepth;
        private int         maxDepth;
        private int         openInputCount;
        private int         openOutputCount;
        private String      queueType;
        private boolean     inhibitGet;
        private boolean     inhibitPut;
        private double      depthPercent;
        private QueueHealth health;
        private Instant     lastUpdated;

        public Builder queueName(String v)        { this.queueName = v;        return this; }
        public Builder queueManagerName(String v) { this.queueManagerName = v; return this; }
        public Builder currentDepth(int v)        { this.currentDepth = v;     return this; }
        public Builder maxDepth(int v)            { this.maxDepth = v;         return this; }
        public Builder openInputCount(int v)      { this.openInputCount = v;   return this; }
        public Builder openOutputCount(int v)     { this.openOutputCount = v;  return this; }
        public Builder queueType(String v)        { this.queueType = v;        return this; }
        public Builder inhibitGet(boolean v)      { this.inhibitGet = v;       return this; }
        public Builder inhibitPut(boolean v)      { this.inhibitPut = v;       return this; }
        public Builder depthPercent(double v)     { this.depthPercent = v;     return this; }
        public Builder health(QueueHealth v)      { this.health = v;           return this; }
        public Builder lastUpdated(Instant v)     { this.lastUpdated = v;      return this; }

        public QueueStats build() { return new QueueStats(this); }
    }
}
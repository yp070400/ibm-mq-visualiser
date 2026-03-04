package com.example.mqmonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.example.mqmonitor.model.QueueManagerConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Strongly-typed binding for the mq.monitor configuration block.
 * Plain POJO — no annotation processor required.
 */
@ConfigurationProperties(prefix = "mq.monitor")
public class MonitorProperties {

    private int pollingIntervalSeconds = 30;
    private int threadPoolSize = 10;
    private int collectionTimeoutSeconds = 20;
    private int warningThresholdPercent = 70;
    private int criticalThresholdPercent = 90;
    private List<QueueManagerConfig> queueManagers = new ArrayList<>();

    public int getPollingIntervalSeconds()          { return pollingIntervalSeconds; }
    public void setPollingIntervalSeconds(int v)    { this.pollingIntervalSeconds = v; }

    public int getThreadPoolSize()                  { return threadPoolSize; }
    public void setThreadPoolSize(int v)            { this.threadPoolSize = v; }

    public int getCollectionTimeoutSeconds()        { return collectionTimeoutSeconds; }
    public void setCollectionTimeoutSeconds(int v)  { this.collectionTimeoutSeconds = v; }

    public int getWarningThresholdPercent()         { return warningThresholdPercent; }
    public void setWarningThresholdPercent(int v)   { this.warningThresholdPercent = v; }

    public int getCriticalThresholdPercent()        { return criticalThresholdPercent; }
    public void setCriticalThresholdPercent(int v)  { this.criticalThresholdPercent = v; }

    public List<QueueManagerConfig> getQueueManagers()          { return queueManagers; }
    public void setQueueManagers(List<QueueManagerConfig> v)    { this.queueManagers = v; }

    public List<QueueManagerConfig> enabledQueueManagers() {
        return queueManagers.stream().filter(QueueManagerConfig::isEnabled).toList();
    }
}
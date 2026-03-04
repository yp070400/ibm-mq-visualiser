package com.example.mqmonitor.model;

/**
 * Configuration for a single IBM MQ Queue Manager.
 * Bound from the mq.monitor.queue-managers list in application.yml.
 * Plain POJO — no annotation processor required.
 */
public class QueueManagerConfig {

    private String name;

    /**
     * IBM MQ connectionName — standard format: "hostname(port)"
     * Supports multi-instance/HA: "host1(1414),host2(1414)"
     * If set, takes precedence over host + port fields.
     */
    private String connectionName;

    /** Used when connectionName is not set. */
    private String host;
    private int port = 1414;

    private String channel;
    private String username;
    private String password;
    private String sslCipherSuite;
    private String sslKeyStore;
    private String sslKeyStorePassword;
    private String sslTrustStore;
    private String sslTrustStorePassword;
    private String queuePattern = "*";
    private boolean excludeSystemQueues = true;
    private boolean enabled = true;

    public String getName()                       { return name; }
    public void   setName(String v)               { this.name = v; }

    public String getConnectionName()             { return connectionName; }
    public void   setConnectionName(String v)     { this.connectionName = v; }

    public String getHost()                       { return host; }
    public void   setHost(String v)               { this.host = v; }

    public int    getPort()                       { return port; }
    public void   setPort(int v)                  { this.port = v; }

    /**
     * Returns connectionName if set, otherwise constructs "host(port)" from
     * the individual fields — so both config styles produce the same result.
     */
    public String resolvedConnectionName() {
        if (connectionName != null && !connectionName.isBlank()) {
            return connectionName;
        }
        return host + "(" + port + ")";
    }

    public String getChannel()                  { return channel; }
    public void   setChannel(String v)          { this.channel = v; }

    public String getUsername()                 { return username; }
    public void   setUsername(String v)         { this.username = v; }

    public String getPassword()                 { return password; }
    public void   setPassword(String v)         { this.password = v; }

    public String getSslCipherSuite()           { return sslCipherSuite; }
    public void   setSslCipherSuite(String v)   { this.sslCipherSuite = v; }

    public String getSslKeyStore()              { return sslKeyStore; }
    public void   setSslKeyStore(String v)      { this.sslKeyStore = v; }

    public String getSslKeyStorePassword()      { return sslKeyStorePassword; }
    public void   setSslKeyStorePassword(String v) { this.sslKeyStorePassword = v; }

    public String getSslTrustStore()            { return sslTrustStore; }
    public void   setSslTrustStore(String v)    { this.sslTrustStore = v; }

    public String getSslTrustStorePassword()    { return sslTrustStorePassword; }
    public void   setSslTrustStorePassword(String v) { this.sslTrustStorePassword = v; }

    public String getQueuePattern()             { return queuePattern; }
    public void   setQueuePattern(String v)     { this.queuePattern = v; }

    public boolean isExcludeSystemQueues()      { return excludeSystemQueues; }
    public void    setExcludeSystemQueues(boolean v) { this.excludeSystemQueues = v; }

    public boolean isEnabled()                  { return enabled; }
    public void    setEnabled(boolean v)        { this.enabled = v; }
}

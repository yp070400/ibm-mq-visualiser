package com.example.mqmonitor.model;

/**
 * Configuration for a single IBM MQ Queue Manager.
 * SSL keystores are configured globally in MonitorProperties.SslConfig.
 * Per-QM only needs the cipher suite (different QMs may use different cipher specs).
 */
public class QueueManagerConfig {

    private String name;

    /**
     * IBM MQ connectionName — standard format: "hostname(port)"
     * Supports multi-instance/HA: "host1(1414),host2(1414)"
     * If set, takes precedence over host + port.
     */
    private String connectionName;

    /** Used when connectionName is not set. */
    private String host;
    private int    port = 1414;

    private String  channel;
    private String  username;
    private String  password;

    /** Must match the SSLCIPH value on the MQ server channel. e.g. ANY_TLS12 */
    private String  sslCipherSuite;

    private String  queuePattern       = "*";
    private boolean excludeSystemQueues = true;
    private boolean enabled             = true;

    public String getName()                     { return name; }
    public void   setName(String v)             { this.name = v; }

    public String getConnectionName()           { return connectionName; }
    public void   setConnectionName(String v)   { this.connectionName = v; }

    public String getHost()                     { return host; }
    public void   setHost(String v)             { this.host = v; }

    public int    getPort()                     { return port; }
    public void   setPort(int v)                { this.port = v; }

    public String getChannel()                  { return channel; }
    public void   setChannel(String v)          { this.channel = v; }

    public String getUsername()                 { return username; }
    public void   setUsername(String v)         { this.username = v; }

    public String getPassword()                 { return password; }
    public void   setPassword(String v)         { this.password = v; }

    public String getSslCipherSuite()           { return sslCipherSuite; }
    public void   setSslCipherSuite(String v)   { this.sslCipherSuite = v; }

    public String getQueuePattern()             { return queuePattern; }
    public void   setQueuePattern(String v)     { this.queuePattern = v; }

    public boolean isExcludeSystemQueues()      { return excludeSystemQueues; }
    public void    setExcludeSystemQueues(boolean v) { this.excludeSystemQueues = v; }

    public boolean isEnabled()                  { return enabled; }
    public void    setEnabled(boolean v)        { this.enabled = v; }

    /** Returns connectionName if set, otherwise builds "host(port)". */
    public String resolvedConnectionName() {
        if (connectionName != null && !connectionName.isBlank()) {
            return connectionName;
        }
        return host + "(" + port + ")";
    }
}
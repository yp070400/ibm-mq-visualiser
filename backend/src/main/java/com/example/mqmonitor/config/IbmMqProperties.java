package com.example.mqmonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps the IBM MQ Spring Boot Starter property convention:
 *   ibm.mq.queue-manager, ibm.mq.conn-name, ibm.mq.channel, ibm.mq.user, ibm.mq.password
 *
 * The primary connection is configured at the top level.
 * Additional queue managers go under ibm.mq.additional-connections[].
 *
 * Example:
 *   ibm:
 *     mq:
 *       queue-manager: QM1
 *       conn-name: "host1(1414)"
 *       channel: DEV.ADMIN.SVRCONN
 *       user: admin
 *       password: passw0rd
 *       additional-connections:
 *         - queue-manager: QM2
 *           conn-name: "host2(1414)"
 *           channel: PROD.SVRCONN
 *           user: admin
 *           password: passw0rd
 */
@ConfigurationProperties(prefix = "ibm.mq")
public class IbmMqProperties {

    /** IBM MQ queue manager name (ibm.mq.queue-manager) */
    private String queueManager;

    /** Connection name: "host(port)" or "host1(1414),host2(1414)" for HA (ibm.mq.conn-name) */
    private String connName;

    private String channel;
    private String user;
    private String password;

    /** TLS cipher suite, e.g. ANY_TLS12. Must match the server channel SSLCIPH. */
    private String sslCipherSuite;

    /** Additional queue managers to monitor beyond the primary one. */
    private List<AdditionalConnection> additionalConnections = new ArrayList<>();

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getQueueManager()                                        { return queueManager; }
    public void   setQueueManager(String v)                                { this.queueManager = v; }

    public String getConnName()                                            { return connName; }
    public void   setConnName(String v)                                    { this.connName = v; }

    public String getChannel()                                             { return channel; }
    public void   setChannel(String v)                                     { this.channel = v; }

    public String getUser()                                                { return user; }
    public void   setUser(String v)                                        { this.user = v; }

    public String getPassword()                                            { return password; }
    public void   setPassword(String v)                                    { this.password = v; }

    public String getSslCipherSuite()                                      { return sslCipherSuite; }
    public void   setSslCipherSuite(String v)                              { this.sslCipherSuite = v; }

    public List<AdditionalConnection> getAdditionalConnections()           { return additionalConnections; }
    public void setAdditionalConnections(List<AdditionalConnection> v)     { this.additionalConnections = v; }

    // ── Additional connection entry ───────────────────────────────────────────

    public static class AdditionalConnection {

        private String  queueManager;
        private String  connName;
        private String  channel;
        private String  user;
        private String  password;
        private String  sslCipherSuite;
        private boolean enabled = true;

        public String  getQueueManager()           { return queueManager; }
        public void    setQueueManager(String v)   { this.queueManager = v; }

        public String  getConnName()               { return connName; }
        public void    setConnName(String v)       { this.connName = v; }

        public String  getChannel()                { return channel; }
        public void    setChannel(String v)        { this.channel = v; }

        public String  getUser()                   { return user; }
        public void    setUser(String v)           { this.user = v; }

        public String  getPassword()               { return password; }
        public void    setPassword(String v)       { this.password = v; }

        public String  getSslCipherSuite()         { return sslCipherSuite; }
        public void    setSslCipherSuite(String v) { this.sslCipherSuite = v; }

        public boolean isEnabled()                 { return enabled; }
        public void    setEnabled(boolean v)       { this.enabled = v; }
    }
}

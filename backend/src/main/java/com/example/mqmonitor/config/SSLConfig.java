package com.example.mqmonitor.config;

import org.springframework.stereotype.Component;

/**
 * SSL configuration placeholder.
 * SSL/TLS is not required — the MQ channel has SSLCIPH disabled.
 * If SSL is needed in future, configure ibm.mq.ssl-cipher-suite and
 * set javax.net.ssl.* JVM properties for keystore/truststore.
 */
@Component
public class SSLConfig {
    // No-op: no SSL properties set intentionally.
    // Previously set com.ibm.mq.cfg.useIBMCipherMappings=false unconditionally,
    // which triggered JSSE initialisation and caused MQRC_JSSE_ERROR (2495).
}

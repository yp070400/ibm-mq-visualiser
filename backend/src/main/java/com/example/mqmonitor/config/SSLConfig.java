package com.example.mqmonitor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;

/**
 * Configures SSL for IBM MQ connections via JVM system properties.
 *
 * Why JVM properties instead of a custom SSLSocketFactory?
 * IBM MQ Java client's internal TLS handshake reliably picks up the standard
 * javax.net.ssl.* properties set before the first connection is made.
 * Passing a custom SSLSocketFactory via the connection Hashtable has known
 * compatibility issues with certain IBM MQ versions and cipher suites.
 *
 * This runs once at startup (before any MQ connection is attempted) and sets:
 *   javax.net.ssl.keyStore / keyStorePassword / keyStoreType
 *   javax.net.ssl.trustStore / trustStorePassword / trustStoreType
 *   com.ibm.mq.cfg.useIBMCipherMappings = false  (required on non-IBM JDK)
 */
@Component
public class SSLConfig {

    private static final Logger log = LoggerFactory.getLogger(SSLConfig.class);

    private final MonitorProperties properties;

    public SSLConfig(MonitorProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        // Always required when running on OpenJDK / Microsoft JDK.
        // Tells IBM MQ client to use standard JSSE cipher names instead of IBM names.
        System.setProperty("com.ibm.mq.cfg.useIBMCipherMappings", "false");
        log.debug("IBM cipher mappings disabled");

        MonitorProperties.SslConfig ssl = properties.getSsl();

        if (StringUtils.hasText(ssl.getKeyStore())) {
            validateFile(ssl.getKeyStore(), "ssl.key-store");
            String type = detectStoreType(ssl.getKeyStore(), ssl.getKeyStorePassword());
            System.setProperty("javax.net.ssl.keyStore",         ssl.getKeyStore());
            System.setProperty("javax.net.ssl.keyStorePassword", nullSafe(ssl.getKeyStorePassword()));
            System.setProperty("javax.net.ssl.keyStoreType",     type);
            log.info("SSL key store    : {} ({})", ssl.getKeyStore(), type);
        }

        if (StringUtils.hasText(ssl.getTrustStore())) {
            validateFile(ssl.getTrustStore(), "ssl.trust-store");
            String type = detectStoreType(ssl.getTrustStore(), ssl.getTrustStorePassword());
            System.setProperty("javax.net.ssl.trustStore",         ssl.getTrustStore());
            System.setProperty("javax.net.ssl.trustStorePassword", nullSafe(ssl.getTrustStorePassword()));
            System.setProperty("javax.net.ssl.trustStoreType",     type);
            log.info("SSL trust store  : {} ({})", ssl.getTrustStore(), type);
        }

        if (!StringUtils.hasText(ssl.getKeyStore()) && !StringUtils.hasText(ssl.getTrustStore())) {
            log.info("No SSL stores configured — MQ connections will use plain-text or JVM defaults");
        }
    }

    /**
     * Auto-detects whether a store file is PKCS12 or JKS by attempting to load it.
     * Returns the correct type string for javax.net.ssl.keyStoreType.
     */
    private String detectStoreType(String path, String password) {
        char[] pwd = password != null ? password.toCharArray() : null;

        // Try PKCS12 first (default in Java 9+; also used by many MQ installations)
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(path)) {
                ks.load(fis, pwd);
            }
            return "PKCS12";
        } catch (Exception ignored) {}

        // Fall back to JKS
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(path)) {
                ks.load(fis, pwd);
            }
            return "JKS";
        } catch (Exception e) {
            throw new IllegalStateException(
                "Cannot load store '" + path + "' as PKCS12 or JKS. " +
                "Check the file and password. Error: " + e.getMessage(), e);
        }
    }

    private void validateFile(String path, String configKey) {
        if (!Files.exists(Paths.get(path))) {
            throw new IllegalStateException(
                "SSL store file not found for " + configKey + ": " + path);
        }
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}

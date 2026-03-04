package com.example.mqmonitor.config;

import com.example.mqmonitor.model.QueueManagerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * Builds SSL socket factories for queue managers that require TLS.
 */
@Component
public class SSLConfig {

    private static final Logger log = LoggerFactory.getLogger(SSLConfig.class);

    public SSLSocketFactory buildSocketFactory(QueueManagerConfig config) {
        if (!StringUtils.hasText(config.getSslCipherSuite())) {
            return null;
        }

        boolean hasKeyStore   = StringUtils.hasText(config.getSslKeyStore());
        boolean hasTrustStore = StringUtils.hasText(config.getSslTrustStore());

        if (!hasKeyStore && !hasTrustStore) {
            log.debug("QM {}: using JVM-wide SSL key/trust stores", config.getName());
            return null;
        }

        try {
            SSLContext ctx = SSLContext.getInstance("TLS");

            KeyManagerFactory kmf = null;
            if (hasKeyStore) {
                KeyStore ks = loadKeyStore(config.getSslKeyStore(), config.getSslKeyStorePassword());
                kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(ks, config.getSslKeyStorePassword().toCharArray());
            }

            TrustManagerFactory tmf = null;
            if (hasTrustStore) {
                KeyStore ts = loadKeyStore(config.getSslTrustStore(), config.getSslTrustStorePassword());
                tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ts);
            }

            ctx.init(
                kmf != null ? kmf.getKeyManagers()   : null,
                tmf != null ? tmf.getTrustManagers() : null,
                null
            );

            log.info("QM {}: SSL context built (cipher={})", config.getName(), config.getSslCipherSuite());
            return ctx.getSocketFactory();

        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to build SSL context for queue manager: " + config.getName(), e);
        }
    }

    private KeyStore loadKeyStore(String path, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(path)) {
            ks.load(fis, password != null ? password.toCharArray() : null);
        }
        return ks;
    }
}
package com.example.mqmonitor.service;

import com.example.mqmonitor.model.MessageDto;
import com.example.mqmonitor.model.PostMessageRequest;
import com.example.mqmonitor.model.PurgeResult;
import com.example.mqmonitor.model.QueueManagerConfig;
import com.example.mqmonitor.mq.MQConnectionManager;
import com.ibm.mq.MQException;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQPutMessageOptions;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.pcf.PCFException;
import com.ibm.mq.pcf.PCFMessage;
import com.ibm.mq.pcf.PCFMessageAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Stateless service for interactive queue message operations:
 * browse, put, delete-by-id, and purge-all.
 *
 * All methods open a queue handle, operate, and close it in a finally block.
 * MQ errors are translated to ResponseStatusException so the REST layer stays clean.
 */
@Service
public class MQQueueOperationsService {

    private static final Logger log = LoggerFactory.getLogger(MQQueueOperationsService.class);

    private static final int MAX_BODY_BYTES = 4096;

    private final MQConnectionManager connectionManager;
    private final MQMonitoringService  monitoringService;

    public MQQueueOperationsService(MQConnectionManager connectionManager,
                                    MQMonitoringService monitoringService) {
        this.connectionManager = connectionManager;
        this.monitoringService  = monitoringService;
    }

    // ── Browse ────────────────────────────────────────────────────────────────

    /**
     * Browse up to {@code limit} messages without removing them (MQOO_BROWSE).
     */
    public List<MessageDto> browseMessages(String qmName, String queueName, int limit) {
        QueueManagerConfig config = resolveConfig(qmName);
        MQQueue queue = null;
        try {
            MQQueueManager qmgr = connectionManager.getQueueManager(config);
            queue = qmgr.accessQueue(queueName,
                    CMQC.MQOO_BROWSE | CMQC.MQOO_FAIL_IF_QUIESCING);

            List<MessageDto> messages = new ArrayList<>();
            MQGetMessageOptions gmo = new MQGetMessageOptions();
            gmo.options = CMQC.MQGMO_BROWSE_FIRST
                    | CMQC.MQGMO_FAIL_IF_QUIESCING
                    | CMQC.MQGMO_ACCEPT_TRUNCATED_MSG;
            gmo.waitInterval = 0;

            for (int i = 0; i < limit; i++) {
                MQMessage msg = new MQMessage();
                try {
                    queue.get(msg, gmo);
                } catch (MQException e) {
                    if (e.reasonCode == CMQC.MQRC_NO_MSG_AVAILABLE) {
                        break; // no more messages
                    }
                    if (e.reasonCode == CMQC.MQRC_TRUNCATED_MSG_ACCEPTED) {
                        // message was truncated into the buffer — still usable
                        messages.add(toDto(msg));
                        gmo.options = CMQC.MQGMO_BROWSE_NEXT
                                | CMQC.MQGMO_FAIL_IF_QUIESCING
                                | CMQC.MQGMO_ACCEPT_TRUNCATED_MSG;
                        continue;
                    }
                    throw e;
                }
                messages.add(toDto(msg));
                gmo.options = CMQC.MQGMO_BROWSE_NEXT
                        | CMQC.MQGMO_FAIL_IF_QUIESCING
                        | CMQC.MQGMO_ACCEPT_TRUNCATED_MSG;
            }
            return messages;

        } catch (MQException e) {
            throw toHttpException(qmName, queueName, e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "IO error reading message: " + e.getMessage());
        } finally {
            closeQuietly(queue);
        }
    }

    // ── Put ───────────────────────────────────────────────────────────────────

    /**
     * Put a text message onto the queue. Returns the hex message ID assigned by the QM.
     */
    public String putMessage(String qmName, String queueName, PostMessageRequest req) {
        QueueManagerConfig config = resolveConfig(qmName);
        MQQueue queue = null;
        try {
            MQQueueManager qmgr = connectionManager.getQueueManager(config);
            queue = qmgr.accessQueue(queueName,
                    CMQC.MQOO_OUTPUT | CMQC.MQOO_FAIL_IF_QUIESCING);

            MQMessage msg = new MQMessage();
            String fmt = (req.getFormat() != null && !req.getFormat().isBlank())
                    ? req.getFormat() : "MQSTR";
            msg.format = String.format("%-8s", fmt).substring(0, 8); // pad to 8 chars
            msg.characterSet = 1208; // UTF-8

            String correlHex = req.getCorrelationId();
            if (correlHex != null && !correlHex.isBlank()) {
                msg.correlationId = hexToBytes(correlHex);
            }

            byte[] bodyBytes = (req.getBody() != null ? req.getBody() : "")
                    .getBytes(StandardCharsets.UTF_8);
            msg.write(bodyBytes);

            MQPutMessageOptions pmo = new MQPutMessageOptions();
            pmo.options = CMQC.MQPMO_FAIL_IF_QUIESCING | CMQC.MQPMO_NEW_MSG_ID;

            queue.put(msg, pmo);
            String msgId = bytesToHex(msg.messageId);
            log.debug("Put message to {}/{} msgId={}", qmName, queueName, msgId);
            return msgId;

        } catch (MQException e) {
            throw toHttpException(qmName, queueName, e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "IO error writing message body: " + e.getMessage());
        } finally {
            closeQuietly(queue);
        }
    }

    // ── Delete by message ID ──────────────────────────────────────────────────

    /**
     * Destructively get (delete) a single message matched by its message ID (hex string).
     */
    public void deleteMessage(String qmName, String queueName, String msgIdHex) {
        QueueManagerConfig config = resolveConfig(qmName);
        MQQueue queue = null;
        try {
            MQQueueManager qmgr = connectionManager.getQueueManager(config);
            queue = qmgr.accessQueue(queueName,
                    CMQC.MQOO_INPUT_SHARED | CMQC.MQOO_FAIL_IF_QUIESCING);

            MQMessage msg = new MQMessage();
            msg.messageId = hexToBytes(msgIdHex);

            MQGetMessageOptions gmo = new MQGetMessageOptions();
            gmo.options = CMQC.MQGMO_FAIL_IF_QUIESCING
                    | CMQC.MQGMO_NO_WAIT
                    | CMQC.MQGMO_ACCEPT_TRUNCATED_MSG;
            gmo.matchOptions = CMQC.MQMO_MATCH_MSG_ID;

            try {
                queue.get(msg, gmo);
                log.debug("Deleted message {} from {}/{}", msgIdHex, qmName, queueName);
            } catch (MQException e) {
                if (e.reasonCode == CMQC.MQRC_NO_MSG_AVAILABLE) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Message not found: " + msgIdHex);
                }
                throw e;
            }

        } catch (ResponseStatusException e) {
            throw e;
        } catch (MQException e) {
            throw toHttpException(qmName, queueName, e);
        } finally {
            closeQuietly(queue);
        }
    }

    // ── Purge (PCF CLEAR_Q) ───────────────────────────────────────────────────

    /**
     * Clear all messages via PCF MQCMD_CLEAR_Q (single round-trip, atomic).
     * The depth before the clear is read from the monitoring cache.
     */
    public PurgeResult purgeQueue(String qmName, String queueName) {
        QueueManagerConfig config = resolveConfig(qmName);
        try {
            // capture depth before clear for the response
            int depthBefore = monitoringService.getQueueStats(qmName, queueName)
                    .map(s -> s.getCurrentDepth())
                    .orElse(0);

            PCFMessageAgent agent = connectionManager.getAgent(config);
            PCFMessage request = new PCFMessage(CMQCFC.MQCMD_CLEAR_Q);
            request.addParameter(CMQC.MQCA_Q_NAME, queueName);
            agent.send(request);

            log.info("Purged queue {}/{} (depth was {})", qmName, queueName, depthBefore);
            return new PurgeResult(depthBefore, queueName, qmName);

        } catch (PCFException e) {
            log.error("PCF CLEAR_Q failed for {}/{}: reason={}", qmName, queueName, e.getReason());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "PCF CLEAR_Q failed: reason " + e.getReason());
        } catch (MQException e) {
            throw toHttpException(qmName, queueName, e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "IO error during purge: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private QueueManagerConfig resolveConfig(String qmName) {
        return monitoringService.getQueueManagerConfig(qmName)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Queue manager not found: " + qmName));
    }

    private MessageDto toDto(MQMessage msg) throws IOException {
        String msgId   = bytesToHex(msg.messageId);
        String correlId = isAllZero(msg.correlationId) ? "" : bytesToHex(msg.correlationId);
        String putTime = formatPutTime(msg.putDate, msg.putTime);
        String format  = (msg.format != null ? msg.format : "").trim();

        int available = msg.getDataLength();
        int readLen   = Math.min(available, MAX_BODY_BYTES);
        byte[] buf = new byte[readLen];
        if (readLen > 0) {
            msg.readFully(buf, 0, readLen);
        }

        String body = decodeBody(buf, format);
        return new MessageDto(msgId, correlId, putTime, format, available, body);
    }

    private static String formatPutTime(String putDate, String putTime) {
        if (putDate == null || putDate.isBlank()) return "";
        try {
            String d = putDate.trim();
            String t = (putTime != null) ? putTime.trim() : "000000";
            if (d.length() >= 8 && t.length() >= 6) {
                return d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8)
                        + "T" + t.substring(0, 2) + ":" + t.substring(2, 4) + ":" + t.substring(4, 6);
            }
        } catch (Exception ignored) {}
        return putDate + " " + putTime;
    }

    private static String decodeBody(byte[] data, String format) {
        if (data == null || data.length == 0) return "";
        // Known text formats: MQSTR, MQHRF2, MQRFH, MQRFH2, MQADMIN
        boolean isText = format.startsWith("MQSTR")
                || format.startsWith("MQHRF")
                || format.startsWith("MQRFH")
                || format.startsWith("MQADM");
        if (isText) {
            return new String(data, StandardCharsets.UTF_8);
        }
        // Try UTF-8; fall back to BASE64 if binary content detected
        String decoded = new String(data, StandardCharsets.UTF_8);
        boolean hasBinary = decoded.chars()
                .anyMatch(c -> c < 0x20 && c != 0x09 && c != 0x0A && c != 0x0D);
        return hasBinary
                ? "BASE64:" + Base64.getEncoder().encodeToString(data)
                : decoded;
    }

    private static boolean isAllZero(byte[] bytes) {
        if (bytes == null) return true;
        for (byte b : bytes) { if (b != 0) return false; }
        return true;
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        hex = hex.replaceAll("\\s+", "");
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static void closeQuietly(MQQueue queue) {
        if (queue != null) {
            try { queue.close(); } catch (MQException ignored) {}
        }
    }

    private ResponseStatusException toHttpException(String qmName, String queueName, MQException e) {
        log.error("MQ error on {}/{}: reasonCode={}", qmName, queueName, e.getReason());
        HttpStatus status = switch (e.getReason()) {
            case CMQC.MQRC_UNKNOWN_OBJECT_NAME -> HttpStatus.NOT_FOUND;
            case CMQC.MQRC_NOT_AUTHORIZED      -> HttpStatus.FORBIDDEN;
            default                             -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return new ResponseStatusException(status,
                "MQ error (reason " + e.getReason() + "): " + e.getMessage());
    }
}

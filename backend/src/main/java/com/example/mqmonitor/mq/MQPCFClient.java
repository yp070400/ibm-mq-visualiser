package com.example.mqmonitor.mq;

import com.ibm.mq.MQException;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.pcf.PCFException;
import com.ibm.mq.pcf.PCFMessage;
import com.ibm.mq.pcf.PCFMessageAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateless PCF command client.
 *
 * Two PCF round-trips per collection cycle per queue manager:
 *   1. MQCMD_INQUIRE_Q       — queue definitions (type, max depth, inhibit flags)
 *   2. MQCMD_INQUIRE_Q_STATUS — runtime state (current depth, open handles)
 */
@Component
public class MQPCFClient {

    private static final Logger log = LoggerFactory.getLogger(MQPCFClient.class);

    public Map<String, QueueDefinition> inquireQueues(PCFMessageAgent agent, String pattern)
            throws PCFException, MQException, IOException {

        PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q);
        request.addParameter(CMQC.MQCA_Q_NAME,  padRight(pattern, 48));
        request.addParameter(CMQC.MQIA_Q_TYPE,  CMQC.MQQT_LOCAL);
        request.addParameter(CMQCFC.MQIACF_Q_ATTRS, new int[]{
                CMQC.MQCA_Q_NAME,
                CMQC.MQIA_Q_TYPE,
                CMQC.MQIA_MAX_Q_DEPTH,
                CMQC.MQIA_INHIBIT_GET,
                CMQC.MQIA_INHIBIT_PUT
        });

        Map<String, QueueDefinition> definitions = new HashMap<>();

        try {
            PCFMessage[] responses = agent.send(request);
            for (PCFMessage resp : responses) {
                if (resp.getCompCode() != CMQC.MQCC_OK) continue;

                String name       = getString(resp, CMQC.MQCA_Q_NAME);
                int    type       = getInt(resp, CMQC.MQIA_Q_TYPE);
                int    maxDepth   = getInt(resp, CMQC.MQIA_MAX_Q_DEPTH);
                int    inhibitGet = getInt(resp, CMQC.MQIA_INHIBIT_GET);
                int    inhibitPut = getInt(resp, CMQC.MQIA_INHIBIT_PUT);

                if (name.isBlank()) continue;

                definitions.put(name, new QueueDefinition(
                        name,
                        resolveQueueType(type),
                        maxDepth,
                        inhibitGet == CMQC.MQQA_GET_INHIBITED,
                        inhibitPut == CMQC.MQQA_PUT_INHIBITED
                ));
            }
        } catch (PCFException e) {
            if (e.getReason() == CMQC.MQRC_UNKNOWN_OBJECT_NAME) {
                log.debug("No queues found for pattern '{}'", pattern);
            } else {
                throw e;
            }
        }

        log.debug("INQUIRE_Q: found {} queue definitions for pattern '{}'", definitions.size(), pattern);
        return definitions;
    }

    public Map<String, QueueStatus> inquireQueueStatus(PCFMessageAgent agent, String pattern)
            throws PCFException, MQException, IOException {

        PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q_STATUS);
        request.addParameter(CMQC.MQCA_Q_NAME, padRight(pattern, 48));
        request.addParameter(CMQCFC.MQIACF_Q_STATUS_TYPE, CMQCFC.MQIACF_Q_STATUS);
        request.addParameter(CMQCFC.MQIACF_Q_STATUS_ATTRS, new int[]{
                CMQC.MQCA_Q_NAME,
                CMQC.MQIA_CURRENT_Q_DEPTH,
                CMQC.MQIA_OPEN_INPUT_COUNT,   // = 17 — in CMQC, not CMQCFC
                CMQC.MQIA_OPEN_OUTPUT_COUNT   // = 18
        });

        Map<String, QueueStatus> statuses = new HashMap<>();

        try {
            PCFMessage[] responses = agent.send(request);
            for (PCFMessage resp : responses) {
                if (resp.getCompCode() != CMQC.MQCC_OK) continue;

                String name        = getString(resp, CMQC.MQCA_Q_NAME);
                int    currentDepth = getInt(resp, CMQC.MQIA_CURRENT_Q_DEPTH);
                int    openInput   = getInt(resp, CMQC.MQIA_OPEN_INPUT_COUNT);
                int    openOutput  = getInt(resp, CMQC.MQIA_OPEN_OUTPUT_COUNT);

                if (name.isBlank()) continue;

                statuses.put(name, new QueueStatus(
                        name,
                        Math.max(currentDepth, 0),
                        Math.max(openInput,    0),
                        Math.max(openOutput,   0)
                ));
            }
        } catch (PCFException e) {
            if (e.getReason() == CMQC.MQRC_UNKNOWN_OBJECT_NAME) {
                log.debug("No queue status found for pattern '{}'", pattern);
            } else {
                throw e;
            }
        }

        log.debug("INQUIRE_Q_STATUS: found {} queue statuses for pattern '{}'", statuses.size(), pattern);
        return statuses;
    }

    private String getString(PCFMessage msg, int param) {
        try {
            return msg.getStringParameterValue(param).trim();
        } catch (PCFException e) {
            return "";
        }
    }

    private int getInt(PCFMessage msg, int param) {
        try {
            return msg.getIntParameterValue(param);
        } catch (PCFException e) {
            return 0;
        }
    }

    private String padRight(String value, int length) {
        if (value.length() >= length) return value;
        return value + " ".repeat(length - value.length());
    }

    private String resolveQueueType(int type) {
        return switch (type) {
            case CMQC.MQQT_LOCAL  -> "LOCAL";
            case CMQC.MQQT_ALIAS  -> "ALIAS";
            case CMQC.MQQT_REMOTE -> "REMOTE";
            case CMQC.MQQT_MODEL  -> "MODEL";
            default                -> "UNKNOWN(" + type + ")";
        };
    }

    public record QueueDefinition(
            String  name,
            String  type,
            int     maxDepth,
            boolean inhibitGet,
            boolean inhibitPut
    ) {}

    public record QueueStatus(
            String name,
            int    currentDepth,
            int    openInputCount,
            int    openOutputCount
    ) {}

    public static List<String> mergedNames(Map<String, ?> definitions, Map<String, ?> statuses) {
        var names = new ArrayList<>(definitions.keySet());
        statuses.keySet().stream()
                .filter(k -> !definitions.containsKey(k))
                .forEach(names::add);
        return names;
    }
}
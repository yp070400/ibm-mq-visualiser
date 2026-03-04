package com.example.mqmonitor.api;

import com.example.mqmonitor.model.ConnectionStatus;
import com.example.mqmonitor.model.QueueManagerStats;
import com.example.mqmonitor.service.MQMonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/health — MQ-level health aggregation.
 * HTTP 200 = all connected, 207 = partial, 503 = all down.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final MQMonitoringService monitoringService;

    public HealthController(MQMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Collection<QueueManagerStats> allStats = monitoringService.getAllQueueManagerStats();

        Map<String, String> qmStatuses = new LinkedHashMap<>();
        int connected   = 0;
        int total       = 0;
        int alertQueues = 0;

        for (QueueManagerStats s : allStats) {
            total++;
            qmStatuses.put(s.getName(), s.getConnectionStatus().name());
            if (s.getConnectionStatus() == ConnectionStatus.CONNECTED) connected++;
            alertQueues += s.getWarningQueues() + s.getCriticalQueues();
        }

        String overallStatus;
        int httpStatus;
        if (total == 0) {
            overallStatus = "UNCONFIGURED";
            httpStatus = 200;
        } else if (connected == total) {
            overallStatus = alertQueues > 0 ? "HEALTHY_WITH_ALERTS" : "HEALTHY";
            httpStatus = 200;
        } else if (connected > 0) {
            overallStatus = "DEGRADED";
            httpStatus = 207;
        } else {
            overallStatus = "DOWN";
            httpStatus = 503;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",            overallStatus);
        body.put("timestamp",         Instant.now().toString());
        body.put("connectedManagers", connected);
        body.put("totalManagers",     total);
        body.put("totalAlertQueues",  alertQueues);
        body.put("queueManagers",     qmStatuses);

        return ResponseEntity.status(httpStatus).body(body);
    }
}
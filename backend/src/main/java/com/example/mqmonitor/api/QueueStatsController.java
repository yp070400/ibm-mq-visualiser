package com.example.mqmonitor.api;

import com.example.mqmonitor.model.QueueStats;
import com.example.mqmonitor.service.MQMonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queues")
public class QueueStatsController {

    private final MQMonitoringService monitoringService;

    public QueueStatsController(MQMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    /** GET /api/queues/{queueManager}/{queueName} — stats for a specific queue. */
    @GetMapping("/{queueManager}/{queueName}")
    public ResponseEntity<QueueStats> getQueue(
            @PathVariable String queueManager,
            @PathVariable String queueName) {

        return monitoringService.getQueueStats(queueManager, queueName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
package com.example.mqmonitor.api;

import com.example.mqmonitor.model.QueueManagerStats;
import com.example.mqmonitor.model.QueueStats;
import com.example.mqmonitor.service.MQMonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/queue-managers")
public class QueueManagerController {

    private final MQMonitoringService monitoringService;

    public QueueManagerController(MQMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    /** GET /api/queue-managers — summary of all configured queue managers. */
    @GetMapping
    public Collection<QueueManagerStats> listAll() {
        return monitoringService.getAllQueueManagerStats();
    }

    /** GET /api/queue-managers/{name} — full stats for one queue manager. */
    @GetMapping("/{name}")
    public ResponseEntity<QueueManagerStats> getOne(@PathVariable String name) {
        return monitoringService.getQueueManagerStats(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/queue-managers/{name}/queues — queue list sorted CRITICAL first,
     * then by depth% descending, so the most urgent queues appear at the top.
     */
    @GetMapping("/{name}/queues")
    public ResponseEntity<List<QueueStats>> listQueues(@PathVariable String name) {
        return monitoringService.getQueueManagerStats(name)
                .map(stats -> {
                    List<QueueStats> sorted = stats.getQueues() == null
                            ? List.of()
                            : stats.getQueues().stream()
                                .sorted(Comparator
                                    .comparingInt(QueueManagerController::healthPriority)
                                    .thenComparingDouble(q -> -q.getDepthPercent()))
                                .toList();
                    return ResponseEntity.ok(sorted);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private static int healthPriority(QueueStats q) {
        return switch (q.getHealth()) {
            case CRITICAL -> 0;
            case WARNING  -> 1;
            case UNKNOWN  -> 2;
            case NORMAL   -> 3;
        };
    }
}
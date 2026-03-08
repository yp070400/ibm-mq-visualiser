package com.example.mqmonitor.api;

import com.example.mqmonitor.model.MessageDto;
import com.example.mqmonitor.model.PostMessageRequest;
import com.example.mqmonitor.model.PurgeResult;
import com.example.mqmonitor.service.MQQueueOperationsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for interactive message operations on a single queue.
 *
 * GET    /api/queue-managers/{qmName}/queues/{queueName}/messages?limit=50  → browse
 * POST   /api/queue-managers/{qmName}/queues/{queueName}/messages            → put
 * DELETE /api/queue-managers/{qmName}/queues/{queueName}/messages            → purge all
 * DELETE /api/queue-managers/{qmName}/queues/{queueName}/messages/{msgId}   → delete by ID
 */
@RestController
@RequestMapping("/api/queue-managers/{qmName}/queues/{queueName}/messages")
public class QueueOperationsController {

    private final MQQueueOperationsService service;

    public QueueOperationsController(MQQueueOperationsService service) {
        this.service = service;
    }

    @GetMapping
    public List<MessageDto> browse(
            @PathVariable String qmName,
            @PathVariable String queueName,
            @RequestParam(defaultValue = "50") int limit) {
        return service.browseMessages(qmName, queueName, Math.min(limit, 500));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> put(
            @PathVariable String qmName,
            @PathVariable String queueName,
            @RequestBody PostMessageRequest req) {
        String msgId = service.putMessage(qmName, queueName, req);
        return Map.of("msgId", msgId);
    }

    @DeleteMapping("/{msgId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOne(
            @PathVariable String qmName,
            @PathVariable String queueName,
            @PathVariable String msgId) {
        service.deleteMessage(qmName, queueName, msgId);
    }

    @DeleteMapping
    public PurgeResult purgeAll(
            @PathVariable String qmName,
            @PathVariable String queueName) {
        return service.purgeQueue(qmName, queueName);
    }
}

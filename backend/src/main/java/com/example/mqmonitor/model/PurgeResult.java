package com.example.mqmonitor.model;

/**
 * Response for DELETE /messages (purge all) — reports how many messages were removed.
 */
public record PurgeResult(
        int    messagesDeleted,
        String queueName,
        String queueManager
) {}

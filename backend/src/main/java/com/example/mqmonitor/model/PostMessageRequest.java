package com.example.mqmonitor.model;

/**
 * Request body for PUT /messages — sends a new message to a queue.
 */
public class PostMessageRequest {

    private String body;
    private String format = "MQSTR";
    private String correlationId; // optional hex string; null/blank = no correlation ID

    public String getBody()          { return body; }
    public String getFormat()        { return format; }
    public String getCorrelationId() { return correlationId; }

    public void setBody(String body)                { this.body = body; }
    public void setFormat(String format)            { this.format = format; }
    public void setCorrelationId(String correlId)   { this.correlationId = correlId; }
}

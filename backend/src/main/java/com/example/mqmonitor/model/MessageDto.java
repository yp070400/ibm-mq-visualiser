package com.example.mqmonitor.model;

/**
 * Immutable snapshot of a single MQ message for the browse API.
 * Body is UTF-8 decoded and truncated at 4 KB; prefixed with "BASE64:" for non-text formats.
 */
public record MessageDto(
        String msgId,    // 48-char hex of the 24-byte MQ message ID
        String correlId, // hex, empty string if all-zero correlation ID
        String putTime,  // "YYYY-MM-DDTHH:MM:SS" from MQMD putDate + putTime
        String format,   // trimmed MQMD format, e.g. "MQSTR", "MQHRF2"
        int    length,   // total message body length in bytes
        String body      // UTF-8 decoded body (up to 4 KB), or "BASE64:<encoded>"
) {}

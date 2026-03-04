package com.example.mqmonitor.model;

/**
 * Health classification for a queue based on its depth relative to max depth.
 * Thresholds are configurable via application.yml.
 */
public enum QueueHealth {

    /** Queue depth is within acceptable limits. */
    NORMAL,

    /** Queue depth exceeds the warning threshold (default: 70% of max). */
    WARNING,

    /** Queue depth exceeds the critical threshold (default: 90% of max). */
    CRITICAL,

    /** Health could not be determined (e.g. max depth is zero or unknown). */
    UNKNOWN;

    public static QueueHealth evaluate(int currentDepth, int maxDepth,
                                       int warningPercent, int criticalPercent) {
        if (maxDepth <= 0) {
            return UNKNOWN;
        }
        double pct = (currentDepth * 100.0) / maxDepth;
        if (pct >= criticalPercent) return CRITICAL;
        if (pct >= warningPercent)  return WARNING;
        return NORMAL;
    }
}
package com.aops.agent.domain;

import java.time.Instant;
import java.util.Map;

/**
 * A normalized alert, converted from AlertManager webhook payloads (or simple JSON).
 */
public record AlertEvent(
        String fingerprint,
        String status,
        String alertName,
        Map<String, String> labels,
        Map<String, String> annotations,
        Instant startsAt,
        Instant endsAt,
        String generatorUrl,
        String serviceName
) {

    /** Stable dedup key used across the pipeline. */
    public String dedupKey() {
        String instance = labels.getOrDefault("instance", "unknown");
        String fp = fingerprint == null || fingerprint.isBlank() ? "" : fingerprint;
        return alertName + "|" + fp + "|" + instance;
    }

    /** Derive the owning service from labels; falls back to job/alertname. */
    public static String deriveService(Map<String, String> labels) {
        for (String key : new String[]{"service", "job", "namespace", "alertname"}) {
            String v = labels.get(key);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "unknown";
    }
}

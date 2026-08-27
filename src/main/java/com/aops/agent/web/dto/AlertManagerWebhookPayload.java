package com.aops.agent.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * AlertManager webhook payload (v4). Unknown fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertManagerWebhookPayload(
        String version,
        String status,
        String receiver,
        Map<String, String> groupLabels,
        Map<String, String> commonLabels,
        List<AlertItem> alerts
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlertItem(
            String status,
            Map<String, String> labels,
            Map<String, String> annotations,
            String startsAt,
            String endsAt,
            String generatorURL,
            String fingerprint
    ) {
    }
}

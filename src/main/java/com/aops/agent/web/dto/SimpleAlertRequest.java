package com.aops.agent.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Simplified single-alert payload for manual testing / non-AlertManager sources.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SimpleAlertRequest(
        String alertName,
        String status,
        Map<String, String> labels,
        Map<String, String> annotations,
        String startsAt,
        String fingerprint
) {
}

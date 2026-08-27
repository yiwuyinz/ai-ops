package com.aops.agent.client;

import com.aops.agent.config.AopsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal AlertManager API v2 client, formatted for LLM consumption.
 */
@Component
public class AlertManagerClient {

    private static final Logger log = LoggerFactory.getLogger(AlertManagerClient.class);

    private final RestClient restClient;
    private final ObjectMapper mapper;

    public AlertManagerClient(RestClient.Builder builder, AopsProperties props, ObjectMapper mapper) {
        this.restClient = builder.baseUrl(props.tools().alertmanager().baseUrl()).build();
        this.mapper = mapper;
    }

    /**
     * Fetch alerts, optionally filtered.
     *
     * @param filters e.g. ["alertname=\"HighCpu\"", "service=\"api\""]
     */
    public String getAlerts(List<String> filters, boolean activeOnly) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/v2/alerts");
        if (activeOnly) {
            builder.queryParam("active", true);
        }
        if (filters != null) {
            for (String f : filters) {
                if (f != null && !f.isBlank()) {
                    builder.queryParam("filter", f);
                }
            }
        }
        URI uri = builder.build().encode().toUri();
        log.info("AlertManager get_alerts: {} activeOnly={}", filters, activeOnly);
        String body = restClient.get().uri(uri).retrieve().body(String.class);
        return format(body);
    }

    private String format(String body) {
        try {
            JsonNode arr = mapper.readTree(body);
            if (!arr.isArray() || arr.isEmpty()) {
                return "No matching alerts in AlertManager.";
            }
            List<String> lines = new ArrayList<>();
            for (JsonNode a : arr) {
                String status = a.path("status").asText();
                String labels = a.path("labels").toString();
                String annotations = a.path("annotations").toString();
                String startsAt = a.path("startsAt").asText();
                lines.add("- [" + status + "] labels=" + labels + " annotations=" + annotations
                        + " startsAt=" + startsAt);
            }
            return "AlertManager alerts (" + lines.size() + "):\n" + String.join("\n", lines);
        } catch (Exception e) {
            return "ERROR parsing AlertManager response: " + e.getMessage();
        }
    }
}

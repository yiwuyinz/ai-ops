package com.aops.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Deploy window tracking: alerts during a deploy window are commonly transient.
 * Deploy events come from a JSON file (MVP) — replace with CD/CD webhooks later.
 */
@Service
public class DeployWindowService {

    private static final Logger log = LoggerFactory.getLogger(DeployWindowService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    public record DeployEvent(String service, String version, Instant startedAt, Instant endedAt, String note) {
    }

    private final List<DeployEvent> events = new ArrayList<>();

    public DeployWindowService(ObjectMapper mapper) {
        load("config/deploys.json", mapper);
    }

    private void load(String filePath, ObjectMapper mapper) {
        try {
            Path path = Path.of(filePath).toAbsolutePath();
            if (!Files.exists(path)) {
                log.info("Deploy events file not found: {} (empty deploy window table)", path);
                return;
            }
            DeployEvent[] parsed = mapper.readValue(Files.readAllBytes(path), DeployEvent[].class);
            if (parsed != null) {
                events.addAll(List.of(parsed));
            }
        } catch (Exception e) {
            log.warn("Failed to load deploy events {}: {}", filePath, e.getMessage());
        }
    }

    @PostConstruct
    void logLoaded() {
        log.info("Deploy window table loaded: {} events", events.size());
    }

    /**
     * Recent deploy events for a service within the given window, as LLM-readable text.
     */
    public String recentDeployments(String service, Duration window) {
        Instant since = Instant.now().minus(window);
        List<String> lines = new ArrayList<>();
        for (DeployEvent e : events) {
            if (!e.service().equals(service)) {
                continue;
            }
            if (e.startedAt().isAfter(since) || e.endedAt() != null && e.endedAt().isAfter(since)) {
                lines.add("- deploy version=" + e.version() + " started=" + TS.format(e.startedAt())
                        + " ended=" + (e.endedAt() == null ? "in-progress" : TS.format(e.endedAt()))
                        + (e.note() == null || e.note().isBlank() ? "" : " note=" + e.note()));
            }
        }
        return lines.isEmpty()
                ? "No deploy events for service '" + service + "' in the last "
                        + window.toHours() + "h."
                : "Recent deployments of '" + service + "':\n" + String.join("\n", lines);
    }
}

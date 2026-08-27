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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Loki HTTP client (LogQL query_range), formatted for LLM consumption.
 */
@Component
public class LokiClient {

    private static final Logger log = LoggerFactory.getLogger(LokiClient.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private final RestClient restClient;
    private final int defaultLimit;
    private final ObjectMapper mapper;

    public LokiClient(RestClient.Builder builder, AopsProperties props, ObjectMapper mapper) {
        this.restClient = builder.baseUrl(props.tools().loki().baseUrl()).build();
        this.defaultLimit = props.tools().loki().defaultLimit();
        this.mapper = mapper;
    }

    /**
     * Run a LogQL query over a time range.
     *
     * @return human-readable log lines (stream labels + timestamp + line)
     */
    public String queryRange(String logql, Instant start, Instant end, Integer limit) {
        int lim = limit == null ? defaultLimit : limit;
        // NOTE: the Loki HTTP API expects start/end in UNIX NANOSECONDS
        // (unlike Prometheus, which uses milliseconds) — sending ms silently
        // moves the query window to 1970 and returns empty results.
        URI uri = UriComponentsBuilder.fromPath("/loki/api/v1/query_range")
                .queryParam("query", logql)
                .queryParam("start", start.toEpochMilli() * 1_000_000L)
                .queryParam("end", end.toEpochMilli() * 1_000_000L)
                .queryParam("limit", lim)
                .build().encode().toUri();
        log.info("Loki query_range: {} [{}, {}] limit={}", logql, start, end, lim);
        String body = restClient.get().uri(uri).retrieve().body(String.class);
        return format(body, logql);
    }

    /**
     * List label NAMES available in Loki (for label discovery when a log query
     * returns nothing — the actual labels may differ from the guessed ones).
     */
    public String labels(Instant start, Instant end) {
        URI uri = UriComponentsBuilder.fromPath("/loki/api/v1/labels")
                .queryParam("start", start.toEpochMilli() * 1_000_000L)
                .queryParam("end", end.toEpochMilli() * 1_000_000L)
                .build().encode().toUri();
        String body = restClient.get().uri(uri).retrieve().body(String.class);
        return formatLabelResponse(body, "Available Loki label names");
    }

    /** List VALUES of one label (e.g. all pod names). */
    public String labelValues(String labelName, Instant start, Instant end) {
        URI uri = UriComponentsBuilder.fromPath("/loki/api/v1/label/" + labelName + "/values")
                .queryParam("start", start.toEpochMilli() * 1_000_000L)
                .queryParam("end", end.toEpochMilli() * 1_000_000L)
                .build().encode().toUri();
        String body = restClient.get().uri(uri).retrieve().body(String.class);
        return formatLabelResponse(body, "Values of Loki label \"" + labelName + "\"");
    }

    private String formatLabelResponse(String body, String title) {
        try {
            JsonNode root = mapper.readTree(body);
            if (!"success".equals(root.path("status").asText())) {
                return "ERROR from Loki: " + root.path("error").asText("unknown");
            }
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return title + ": (none)";
            }
            List<String> items = new ArrayList<>();
            data.forEach(n -> items.add(n.asText()));
            return title + ": " + items;
        } catch (Exception e) {
            return "ERROR parsing Loki labels response: " + e.getMessage();
        }
    }

    private String format(String body, String logql) {
        try {
            JsonNode root = mapper.readTree(body);
            String status = root.path("status").asText();
            if (!"success".equals(status)) {
                return "ERROR from Loki: " + root.path("error").asText(root.path("errorType").asText("unknown"));
            }
            JsonNode result = root.path("data").path("result");
            if (!result.isArray() || result.isEmpty()) {
                return "No log lines matched query \"" + logql + "\" in the given time range.";
            }
            List<String> lines = new ArrayList<>();
            for (JsonNode stream : result) {
                String labels = stream.path("stream").toString();
                for (JsonNode pair : stream.path("values")) {
                    if (!pair.isArray() || pair.size() < 2) {
                        continue;
                    }
                    long tsNanos = pair.get(0).asLong();
                    Instant ts = Instant.ofEpochMilli(tsNanos / 1_000_000);
                    lines.add("[" + TS.format(ts) + "] " + labels + " " + pair.get(1).asText());
                }
            }
            if (lines.isEmpty()) {
                return "No log lines matched query \"" + logql + "\" in the given time range. "
                        + "If this is unexpected, widen the window (e.g. sinceMinutesAgo=120) "
                        + "or discover the correct labels with get_log_labels before concluding.";
            }
            return "Loki query \"" + logql + "\" returned " + lines.size() + " lines:\n"
                    + String.join("\n", lines);
        } catch (Exception e) {
            return "ERROR parsing Loki response: " + e.getMessage() + "\nRaw body: " + safeExcerpt(body);
        }
    }

    private String safeExcerpt(String body) {
        if (body == null) {
            return "<empty>";
        }
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }
}

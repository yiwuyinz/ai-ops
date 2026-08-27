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
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Prometheus HTTP client (PromQL query / query_range), formatted for LLM consumption.
 */
@Component
public class PrometheusClient {

    private static final Logger log = LoggerFactory.getLogger(PrometheusClient.class);

    private final RestClient restClient;
    private final ObjectMapper mapper;

    public PrometheusClient(RestClient.Builder builder, AopsProperties props, ObjectMapper mapper) {
        this.restClient = builder.baseUrl(props.tools().prometheus().baseUrl()).build();
        this.mapper = mapper;
    }

    /** Instant vector query. */
    public String query(String promql, Instant time) {
        URI uri = UriComponentsBuilder.fromPath("/api/v1/query")
                .queryParam("query", promql)
                .queryParam("time", time.toEpochMilli() / 1000)
                .build().encode().toUri();
        log.info("Prometheus query: {} @ {}", promql, time);
        String body = restClient.get().uri(uri).retrieve().body(String.class);
        return format(body, promql);
    }

    /** Range query. */
    public String queryRange(String promql, Instant start, Instant end, String step, Integer maxDataPoints) {
        URI uri = UriComponentsBuilder.fromPath("/api/v1/query_range")
                .queryParam("query", promql)
                .queryParam("start", start.toEpochMilli() / 1000)
                .queryParam("end", end.toEpochMilli() / 1000)
                .queryParam("step", step == null ? "60s" : step)
                .build().encode().toUri();
        log.info("Prometheus query_range: {} [{}, {}]", promql, start, end);
        String body = restClient.get().uri(uri).retrieve().body(String.class);
        String formatted = format(body, promql);
        if (maxDataPoints != null && maxDataPoints > 0) {
            formatted = downsample(formatted, maxDataPoints);
        }
        return formatted;
    }

    private String format(String body, String promql) {
        try {
            JsonNode root = mapper.readTree(body);
            String status = root.path("status").asText();
            if (!"success".equals(status)) {
                return "ERROR from Prometheus: " + root.path("error").asText("unknown");
            }
            JsonNode data = root.path("data");
            String resultType = data.path("resultType").asText();
            JsonNode result = data.path("result");
            if (!result.isArray() || result.isEmpty()) {
                return "No series matched query \"" + promql + "\".";
            }
            List<String> lines = new ArrayList<>();
            for (JsonNode series : result) {
                String labels = series.path("metric").toString();
                JsonNode value = series.path("value");
                if (value.isArray() && value.size() >= 2) {
                    lines.add(labels + " = " + value.get(1).asText());
                    continue;
                }
                JsonNode values = series.path("values");
                if (values.isArray()) {
                    StringBuilder sb = new StringBuilder(labels).append(" = ");
                    for (int i = 0; i < values.size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        JsonNode v = values.get(i);
                        sb.append("@").append(v.get(0).asLong()).append(":").append(v.get(1).asText());
                    }
                    lines.add(sb.toString());
                }
            }
            return "Prometheus " + resultType + " query \"" + promql + "\" returned " + lines.size()
                    + " series:\n" + String.join("\n", lines);
        } catch (Exception e) {
            return "ERROR parsing Prometheus response: " + e.getMessage();
        }
    }

    /** Keep range results readable: cap lines per series by uniform sampling. */
    private String downsample(String formatted, int maxDataPoints) {
        String[] lines = formatted.split("\n");
        if (lines.length <= maxDataPoints) {
            return formatted;
        }
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (i % Math.max(1, lines.length / maxDataPoints) == 0) {
                kept.add(lines[i]);
            }
        }
        kept.add("... [" + (lines.length - kept.size()) + " data points omitted by downsampling]");
        return String.join("\n", kept);
    }
}

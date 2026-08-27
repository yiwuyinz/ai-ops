package com.aops.agent.tool;

import com.aops.agent.client.PrometheusClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * query_metric: run PromQL against Prometheus (instant or range query).
 */
@Component
public class QueryMetricTool implements Tool {

    private final PrometheusClient prometheusClient;

    public QueryMetricTool(PrometheusClient prometheusClient) {
        this.prometheusClient = prometheusClient;
    }

    @Override
    public String name() {
        return "query_metric";
    }

    @Override
    public String description() {
        return """
                Query metrics from Prometheus using PromQL.
                Use to verify resource usage (CPU/memory/disk), error rates, request latency and
                to confirm or refute alert conditions.
                Examples:
                  query='rate(http_requests_total{job="api"}[5m])'
                  query='up{job="demo-app"}'
                  query='sum by (pod) (container_cpu_usage_seconds_total{namespace="prod"}[5m])'
                If durationMinutes is provided this runs a RANGE query, otherwise an instant query at now.
                Parameters: promql (required), sinceMinutesAgo (default 15), durationMinutes (optional),
                step (default "60s"), maxDataPoints (default 50).
                """;
    }

    @Override
    public JsonObjectSchema parameters() {
        return JsonObjectSchema.builder()
                .addStringProperty("promql")
                .addIntegerProperty("sinceMinutesAgo")
                .addIntegerProperty("durationMinutes")
                .addStringProperty("step")
                .addIntegerProperty("maxDataPoints")
                .required("promql")
                .build();
    }

    @Override
    public String execute(String argumentsJson) {
        ToolArgs args = ToolArgs.of(argumentsJson);
        String promql = args.str("promql", "");
        if (promql.isBlank()) {
            throw new ToolException("query_metric requires a non-empty 'promql'.");
        }
        int since = Math.max(0, args.integer("sinceMinutesAgo", 15));
        int duration = args.integer("durationMinutes", -1);
        if (duration <= 0) {
            return prometheusClient.query(promql, Instant.now());
        }
        Instant end = Instant.now();
        Instant start = end.minus(since, ChronoUnit.MINUTES);
        String step = args.str("step", "60s");
        int maxDataPoints = Math.max(5, args.integer("maxDataPoints", 50));
        return prometheusClient.queryRange(promql, start, end, step, maxDataPoints);
    }
}

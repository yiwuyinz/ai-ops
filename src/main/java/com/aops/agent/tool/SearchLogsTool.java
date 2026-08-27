package com.aops.agent.tool;

import com.aops.agent.client.LokiClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * search_logs: query Loki with LogQL over a time window.
 */
@Component
public class SearchLogsTool implements Tool {

    private final LokiClient lokiClient;

    public SearchLogsTool(LokiClient lokiClient) {
        this.lokiClient = lokiClient;
    }

    @Override
    public String name() {
        return "search_logs";
    }

    @Override
    public String description() {
        return """
                Search application logs via Loki using LogQL.
                Use for finding error messages, exceptions, stack traces and application events.
                IMPORTANT: when investigating a specific service/pod, use a TARGETED selector
                (e.g. {pod="payment-api"} or {job="eval", pod="payment-api"}) — broad selectors
                like {job="x"} are truncated at 'limit' lines and may hide the stream you need.
                Examples:
                  query='{pod="payment-api"} |= "error"'
                  query='{job="demo-app"} |= "error"'
                  query='{namespace="prod"} |= "OOMKilled"'
                Parameters: query (required, full LogQL stream selector plus optional filter),
                sinceMinutesAgo (default 15), durationMinutes (default 30), limit (default 500).
                """;
    }

    @Override
    public JsonObjectSchema parameters() {
        return JsonObjectSchema.builder()
                .addStringProperty("query")
                .addIntegerProperty("sinceMinutesAgo")
                .addIntegerProperty("durationMinutes")
                .addIntegerProperty("limit")
                .required("query")
                .build();
    }

    @Override
    public String execute(String argumentsJson) {
        ToolArgs args = ToolArgs.of(argumentsJson);
        String query = args.str("query", "");
        if (query.isBlank()) {
            throw new ToolException("search_logs requires a non-empty 'query' (LogQL).");
        }
        int since = Math.max(0, args.integer("sinceMinutesAgo", 15));
        int duration = Math.max(1, args.integer("durationMinutes", 30));
        int limit = Math.max(1, Math.min(500, args.integer("limit", 500)));

        Instant end = Instant.now();
        Instant start = end.minus(since, ChronoUnit.MINUTES);
        Instant windowEnd = start.plus(duration, ChronoUnit.MINUTES);
        if (windowEnd.isBefore(end)) {
            end = windowEnd;
        }
        return lokiClient.queryRange(query, start, end, limit);
    }
}

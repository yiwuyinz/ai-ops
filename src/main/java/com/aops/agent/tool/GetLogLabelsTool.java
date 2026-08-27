package com.aops.agent.tool;

import com.aops.agent.client.LokiClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * get_log_labels: discover which labels (and values) exist in Loki — essential
 * when a log query returns nothing because the actual labels differ from the
 * guessed ones (e.g. pod_name instead of pod).
 */
@Component
public class GetLogLabelsTool implements Tool {

    private final LokiClient lokiClient;

    public GetLogLabelsTool(LokiClient lokiClient) {
        this.lokiClient = lokiClient;
    }

    @Override
    public String name() {
        return "get_log_labels";
    }

    @Override
    public String description() {
        return """
                Discover which labels (and their values) are available in Loki.
                Use when a search_logs query returns nothing — the real labels may differ
                from your guess (e.g. 'pod_name' instead of 'pod'), and this tool tells you
                the exact selectors to use.
                Parameters: labelName (optional — omit to list all label names, provide a
                name like "pod" to list its values), sinceMinutesAgo (default 60).
                """;
    }

    @Override
    public JsonObjectSchema parameters() {
        return JsonObjectSchema.builder()
                .addStringProperty("labelName")
                .addIntegerProperty("sinceMinutesAgo")
                .build();
    }

    @Override
    public String execute(String argumentsJson) {
        ToolArgs args = ToolArgs.of(argumentsJson);
        String labelName = args.str("labelName", "");
        int since = Math.max(5, Math.min(1440, args.integer("sinceMinutesAgo", 60)));
        // NOTE: always query [now - since, now]. Do NOT anchor to the alert start —
        // synthetic/QA alerts have startsAt == now, which collapses the window to
        // zero length and makes the labels API return "(none)" for everything.
        Instant end = Instant.now();
        Instant start = end.minus(since, ChronoUnit.MINUTES);

        return labelName == null || labelName.isBlank()
                ? lokiClient.labels(start, end)
                : lokiClient.labelValues(labelName, start, end);
    }
}

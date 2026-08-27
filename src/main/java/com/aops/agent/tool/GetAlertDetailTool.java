package com.aops.agent.tool;

import com.aops.agent.client.AlertManagerClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * get_alert_detail: fetch current alerts from AlertManager for extra context.
 */
@Component
public class GetAlertDetailTool implements Tool {

    private final AlertManagerClient alertManagerClient;

    public GetAlertDetailTool(AlertManagerClient alertManagerClient) {
        this.alertManagerClient = alertManagerClient;
    }

    @Override
    public String name() {
        return "get_alert_detail";
    }

    @Override
    public String description() {
        return """
                Fetch currently firing alerts from AlertManager to get extra context
                (labels, annotations, start times) about an alert or its neighbors.
                Parameters (all optional): alertName (e.g. "DemoAppDown"), service (e.g. "demo-app").
                """;
    }

    @Override
    public JsonObjectSchema parameters() {
        return JsonObjectSchema.builder()
                .addStringProperty("alertName")
                .addStringProperty("service")
                .build();
    }

    @Override
    public String execute(String argumentsJson) {
        ToolArgs args = ToolArgs.of(argumentsJson);
        List<String> filters = new ArrayList<>();
        String alertName = args.str("alertName", "");
        String service = args.str("service", "");
        if (!alertName.isBlank()) {
            filters.add("alertname=\"" + alertName + "\"");
        }
        if (!service.isBlank()) {
            filters.add("service=\"" + service + "\"");
        }
        return alertManagerClient.getAlerts(filters, true);
    }
}

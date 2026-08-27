package com.aops.agent.tool;

import com.aops.agent.service.RunbookService;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * fetch_runbook: retrieve a runbook's handling instructions.
 */
@Component
public class FetchRunbookTool implements Tool {

    private final RunbookService runbookService;

    public FetchRunbookTool(RunbookService runbookService) {
        this.runbookService = runbookService;
    }

    @Override
    public String name() {
        return "fetch_runbook";
    }

    @Override
    public String description() {
        return """
                Fetch a runbook by name. Runbooks contain step-by-step handling instructions
                for known issues; follow them during the investigation.
                Pass name="list" (or empty) to see all available runbooks.
                Parameters: name (required).
                """;
    }

    @Override
    public JsonObjectSchema parameters() {
        return JsonObjectSchema.builder()
                .addStringProperty("name")
                .required("name")
                .build();
    }

    @Override
    public String execute(String argumentsJson) {
        ToolArgs args = ToolArgs.of(argumentsJson);
        String name = args.str("name", "");
        if (name.isBlank() || "list".equalsIgnoreCase(name)) {
            List<RunbookService.Runbook> all = runbookService.all();
            if (all.isEmpty()) {
                return "No runbooks in the catalog.";
            }
            StringBuilder sb = new StringBuilder("Available runbooks:\n");
            for (RunbookService.Runbook r : all) {
                sb.append("- ").append(r.name()).append(": ").append(r.title())
                        .append(" (alerts: ").append(r.applicableAlerts()).append(")\n");
            }
            return sb.toString();
        }
        return runbookService.findByName(name)
                .map(r -> "Runbook: " + r.name() + " — " + r.title() + "\n\n" + r.content())
                .orElseGet(() -> "ERROR: runbook '" + name + "' not found. Use fetch_runbook with "
                        + "name='list' to see the catalog.");
    }
}

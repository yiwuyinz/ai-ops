package com.aops.agent.tool;

import com.aops.agent.agent.InvestigationContext;
import com.aops.agent.agent.InvestigationContextHolder;
import com.aops.agent.service.DeployWindowService;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * get_deploy_window: check whether the service was recently deployed.
 */
@Component
public class GetDeployWindowTool implements Tool {

    private final DeployWindowService deployWindowService;

    public GetDeployWindowTool(DeployWindowService deployWindowService) {
        this.deployWindowService = deployWindowService;
    }

    @Override
    public String name() {
        return "get_deploy_window";
    }

    @Override
    public String description() {
        return """
                Check whether the service was recently deployed. Alerts that fire inside a
                deploy window are frequently transient — always check this before concluding.
                Parameters: service (optional, defaults to the alert's service), hours (default 6).
                """;
    }

    @Override
    public JsonObjectSchema parameters() {
        return JsonObjectSchema.builder()
                .addStringProperty("service")
                .addIntegerProperty("hours")
                .build();
    }

    @Override
    public String execute(String argumentsJson) {
        ToolArgs args = ToolArgs.of(argumentsJson);
        String service = args.str("service", "");
        if (service.isBlank()) {
            InvestigationContext ctx = InvestigationContextHolder.get();
            if (ctx != null && ctx.getAlert() != null) {
                service = ctx.getAlert().serviceName();
            }
        }
        int hours = Math.max(1, Math.min(72, args.integer("hours", 6)));
        return deployWindowService.recentDeployments(service, Duration.ofHours(hours));
    }
}

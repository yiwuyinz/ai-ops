package com.aops.agent.tool;

import com.aops.agent.service.TopologyService;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * get_topology: look up the asset topology for a service.
 */
@Component
public class GetTopologyTool implements Tool {

    private final TopologyService topologyService;

    public GetTopologyTool(TopologyService topologyService) {
        this.topologyService = topologyService;
    }

    @Override
    public String name() {
        return "get_topology";
    }

    @Override
    public String description() {
        return """
                Look up the asset topology for a service: its log selector, metric namespaces,
                runbooks, owner and dependencies. Use to decide which log/metric sources to query
                and to check dependent services during cascading failures.
                Parameters: service (optional; empty lists all known services).
                """;
    }

    @Override
    public JsonObjectSchema parameters() {
        return JsonObjectSchema.builder()
                .addStringProperty("service")
                .build();
    }

    @Override
    public String execute(String argumentsJson) {
        ToolArgs args = ToolArgs.of(argumentsJson);
        String service = args.str("service", "");
        if (!service.isBlank()) {
            return topologyService.findByService(service)
                    .map(TopologyService.ServiceAsset::toString)
                    .orElseGet(() -> "ERROR: service '" + service
                            + "' not found in topology. Use get_topology with empty service to list all.");
        }
        List<TopologyService.ServiceAsset> all = topologyService.all();
        if (all.isEmpty()) {
            return "Topology is empty (no service assets configured).";
        }
        StringBuilder sb = new StringBuilder("All known services:\n");
        for (TopologyService.ServiceAsset a : all) {
            sb.append("- ").append(a.name()).append(" (namespace=").append(a.namespace())
                    .append(", logSelector=").append(a.logSelector()).append(")\n");
        }
        return sb.toString();
    }
}

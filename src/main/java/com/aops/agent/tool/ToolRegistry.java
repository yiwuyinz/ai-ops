package com.aops.agent.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of all available tools (injected automatically as Spring beans).
 * Exposes LangChain4j {@link ToolSpecification}s for the model and name lookup
 * for the executor.
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> toolsByName = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> tools) {
        for (Tool tool : tools) {
            if (toolsByName.put(tool.name(), tool) != null) {
                throw new IllegalStateException("Duplicate tool name: " + tool.name());
            }
        }
    }

    public Tool get(String name) {
        return toolsByName.get(name);
    }

    public Collection<Tool> all() {
        return toolsByName.values();
    }

    public List<ToolSpecification> specifications() {
        return toolsByName.values().stream()
                .map(t -> ToolSpecification.builder()
                        .name(t.name())
                        .description(t.description())
                        .parameters(t.parameters())
                        .build())
                .toList();
    }

    /** Human-readable tool manifest for /api/info and debugging. */
    public List<Map<String, String>> manifest() {
        return toolsByName.values().stream()
                .map(t -> Map.of("name", t.name(), "description", t.description()))
                .toList();
    }
}

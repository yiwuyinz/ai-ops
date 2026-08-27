package com.aops.agent.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Builds the system prompt: role rules + investigation heuristics + the
 * available tool manifest. The template lives in
 * src/main/resources/prompts/system-prompt-v1.txt.
 */
@Component
public class SystemPromptBuilder {

    private final String template;

    public SystemPromptBuilder() {
        this.template = loadTemplate();
    }

    public String build(java.util.List<ToolSpecification> tools) {
        StringBuilder sb = new StringBuilder();
        for (ToolSpecification tool : tools) {
            sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }
        return template.replace("{tools}", sb.toString());
    }

    private String loadTemplate() {
        try {
            return new ClassPathResource("prompts/system-prompt-v1.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load system prompt template", e);
        }
    }
}

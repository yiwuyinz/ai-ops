package com.aops.agent.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemPromptBuilderTest {

    private final SystemPromptBuilder builder = new SystemPromptBuilder();

    @Test
    void buildIncludesToolsHeuristicsAndOutputFormat() {
        List<ToolSpecification> specs = List.of(
                ToolSpecification.builder()
                        .name("search_logs")
                        .description("Search logs via Loki LogQL.")
                        .build(),
                ToolSpecification.builder()
                        .name("query_metric")
                        .description("Query Prometheus metrics.")
                        .build());

        String prompt = builder.build(specs);

        assertTrue(prompt.contains("search_logs: Search logs via Loki LogQL."));
        assertTrue(prompt.contains("query_metric: Query Prometheus metrics."));
        assertTrue(prompt.contains("LIKELY_FALSE_POSITIVE"));
        assertTrue(prompt.contains("needsHuman"));
        assertTrue(prompt.contains("READ-ONLY"));
        assertTrue(prompt.contains("deploy window"));
    }
}

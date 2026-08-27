package com.aops.agent.tool;

import com.aops.agent.agent.InvestigationContext;
import com.aops.agent.agent.InvestigationContextHolder;
import com.aops.agent.domain.AlertEvent;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutorTest {

    private final Tool echoTool = new Tool() {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "Echo the message argument.";
        }

        @Override
        public JsonObjectSchema parameters() {
            return JsonObjectSchema.builder().addStringProperty("message").required("message").build();
        }

        @Override
        public String execute(String argumentsJson) {
            return ToolArgs.of(argumentsJson).str("message", "");
        }
    };

    private final ToolRegistry registry = new ToolRegistry(List.of(echoTool));
    private final ToolExecutor executor = new ToolExecutor(registry);

    @AfterEach
    void cleanUp() {
        InvestigationContextHolder.clear();
    }

    @Test
    void executesToolAndRecordsEvidence() {
        InvestigationContext ctx = new InvestigationContext("case-1", alert(), null);
        InvestigationContextHolder.set(ctx);

        String result = executor.execute("echo", "{\"message\":\"hello\"}", 6000);

        assertEquals("hello", result);
        assertEquals(1, ctx.getEvidence().size());
        assertEquals("echo", ctx.getEvidence().get(0).getToolName());
    }

    @Test
    void unknownToolReturnsSelfCorrectingError() {
        String result = executor.execute("no_such_tool", "{}", 6000);
        assertTrue(result.contains("unknown tool"), result);
        assertTrue(result.contains("echo"), result);
    }

    @Test
    void failingToolReturnsErrorMessageNotException() {
        Tool boom = new Tool() {
            @Override
            public String name() {
                return "boom";
            }

            @Override
            public String description() {
                return "Always fails.";
            }

            @Override
            public JsonObjectSchema parameters() {
                return JsonObjectSchema.builder().build();
            }

            @Override
            public String execute(String argumentsJson) {
                throw new ToolException("backend returned 500");
            }
        };
        ToolExecutor executor2 = new ToolExecutor(new ToolRegistry(List.of(boom)));

        String result = executor2.execute("boom", "{}", 6000);

        assertTrue(result.contains("ERROR calling tool 'boom'"), result);
        assertTrue(result.contains("backend returned 500"), result);
    }

    private AlertEvent alert() {
        return new AlertEvent("fp", "firing", "TestAlert",
                Map.of("alertname", "TestAlert", "service", "demo"),
                Map.of(), Instant.now(), null, null, "demo");
    }
}

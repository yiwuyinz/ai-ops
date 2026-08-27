package com.aops.agent.agent;

import com.aops.agent.domain.AlertEvent;
import com.aops.agent.tool.Tool;
import com.aops.agent.tool.ToolArgs;
import com.aops.agent.tool.ToolExecutor;
import com.aops.agent.tool.ToolRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentLoopTest {

    private final Tool echoTool = new Tool() {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "Echo the message.";
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

    @AfterEach
    void cleanUp() {
        InvestigationContextHolder.clear();
    }

    @Test
    void executesToolCallsAndReturnsFinalText() {
        ToolRegistry registry = new ToolRegistry(List.of(echoTool));
        AgentLoop loop = new AgentLoop(new ToolExecutor(registry));
        FakeChatModel model = new FakeChatModel();
        model.script(
                AiMessage.builder()
                        .toolExecutionRequests(List.of(
                                ToolExecutionRequest.builder()
                                        .id("call-1").name("echo").arguments("{\"message\":\"hello\"}")
                                        .build()))
                        .build(),
                AiMessage.from("final text with {\"verdict\":\"CONFIRMED\"}"));

        InvestigationContext ctx = new InvestigationContext("c1", alert(), null);
        InvestigationContextHolder.set(ctx);
        try {
            String result = loop.run(model, "system", "user", registry.specifications(),
                    5, 6000, 1, 60, Instant.now().plusSeconds(30));

            assertEquals("final text with {\"verdict\":\"CONFIRMED\"}", result);
            assertEquals(1, ctx.getEvidence().size());
            assertEquals("echo", ctx.getEvidence().get(0).getToolName());
        } finally {
            InvestigationContextHolder.clear();
        }
    }

    @Test
    void stopsWhenStepBudgetExhausted() {
        ToolRegistry registry = new ToolRegistry(List.of(echoTool));
        AgentLoop loop = new AgentLoop(new ToolExecutor(registry));
        FakeChatModel model = new FakeChatModel();
        // the model keeps asking for tool calls — the loop must stop at maxSteps=2
        for (int i = 0; i < 10; i++) {
            model.script(AiMessage.builder()
                    .toolExecutionRequests(List.of(
                            ToolExecutionRequest.builder()
                                    .id("call-" + i).name("echo").arguments("{\"message\":\"x\"}")
                                    .build()))
                    .build());
        }

        InvestigationContext ctx = new InvestigationContext("c2", alert(), null);
        InvestigationContextHolder.set(ctx);
        try {
            String result = loop.run(model, "system", "user", registry.specifications(),
                    2, 6000, 1, 60, Instant.now().plusSeconds(30));

            assertEquals(2, ctx.getEvidence().size(), "step budget must cap tool calls");
            assertEquals("(no output from LLM)", result);
        } finally {
            InvestigationContextHolder.clear();
        }
    }

    private AlertEvent alert() {
        return new AlertEvent("fp", "firing", "TestAlert",
                Map.of("alertname", "TestAlert", "service", "demo"),
                Map.of(), Instant.now(), null, null, "demo");
    }
}

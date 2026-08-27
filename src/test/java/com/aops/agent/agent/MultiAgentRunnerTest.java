package com.aops.agent.agent;

import com.aops.agent.TestProps;
import com.aops.agent.domain.AlertEvent;
import com.aops.agent.service.DeployWindowService;
import com.aops.agent.service.RunbookService;
import com.aops.agent.service.TopologyService;
import com.aops.agent.tool.ToolExecutor;
import com.aops.agent.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 orchestration test: 3 specialists must all run (deterministically
 * routed by the fake model), and the supervisor synthesis must produce the
 * standard verdict JSON.
 */
class MultiAgentRunnerTest {

    @Test
    void runsAllSpecialistsInParallelAndSynthesizesVerdict() {
        var executor = Executors.newFixedThreadPool(4);
        try {
            ToolRegistry registry = new ToolRegistry(List.of());
            AgentLoop loop = new AgentLoop(new ToolExecutor(registry));
            FakeChatModel model = new FakeChatModel();

            TopologyService topology = new TopologyService(TestProps.defaultProps(), new ObjectMapper());
            RunbookService runbooks = new RunbookService(TestProps.defaultProps());
            DeployWindowService deploys = new DeployWindowService(new ObjectMapper());
            UserPromptBuilder userPromptBuilder = new UserPromptBuilder(topology, runbooks, deploys);

            MultiAgentRunner runner = new MultiAgentRunner(model, loop, registry, userPromptBuilder,
                    TestProps.defaultProps(), executor);

            AlertEvent alert = new AlertEvent("fp-eval", "firing", "HighLatencyPaymentApi",
                    Map.of("alertname", "HighLatencyPaymentApi", "service", "payment-api"),
                    Map.of("summary", "payment api latency high"), Instant.now(), null, null, "payment-api");
            InvestigationContext ctx = new InvestigationContext("c3", alert, null);
            InvestigationContextHolder.set(ctx);
            try {
                String result = runner.investigate(alert, null, ctx, Instant.now().plusSeconds(60));

                // every specialist ran at least once, and the supervisor synthesized
                assertEquals(1, model.calls(FakeChatModel.Role.LOG));
                assertEquals(1, model.calls(FakeChatModel.Role.METRIC));
                assertEquals(1, model.calls(FakeChatModel.Role.KB));
                assertEquals(1, model.calls(FakeChatModel.Role.SUPERVISOR));

                assertTrue(result.contains("CONFIRMED"), result);
                assertTrue(result.contains("db-primary"), result);
            } finally {
                InvestigationContextHolder.clear();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}

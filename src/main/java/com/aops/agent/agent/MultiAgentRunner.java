package com.aops.agent.agent;

import com.aops.agent.config.AopsProperties;
import com.aops.agent.domain.AlertEvent;
import com.aops.agent.service.TopologyService;
import com.aops.agent.tool.ToolRegistry;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Phase 3 multi-agent orchestration:
 *
 * <pre>
 *   alert ──► [log_analyst]  (search_logs, get_log_labels)   ─┐ parallel
 *          ──► [metric_analyst] (query_metric)                 ├─► supervisor synthesis ──► verdict
 *          ──► [knowledge_analyst] (kb/runbook/topology)       ┘
 * </pre>
 *
 * Specialists run in parallel with NARROW tool subsets (token budget isolation);
 * the supervisor (a plain synthesis pass, no tools) joins the findings and
 * produces the standard verdict JSON. All tool calls land in the case's
 * evidence chain via the shared {@link InvestigationContext}.
 */
@Service
public class MultiAgentRunner {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentRunner.class);
    private static final int SPECIALIST_MAX_STEPS = 4;

    private record SpecialistDef(String name, String systemPrompt, List<String> toolNames) {
    }

    private static final String LOG_SPECIALIST_PROMPT = """
            You are a LOG ANALYSIS specialist on an SRE investigation team.
            You ONLY use these tools: search_logs, get_log_labels, summarize_output.
            Focus exclusively on log evidence: find error patterns, exact error messages,
            exception types, hostnames and timestamps. Quote log lines VERBATIM.
            If no logs match, say so explicitly — never invent log content.
            Keep your findings under 200 words, concrete, with exact identifiers.
            """;

    private static final String METRIC_SPECIALIST_PROMPT = """
            You are a METRICS specialist on an SRE investigation team.
            You ONLY use these tools: query_metric, summarize_output.
            Focus exclusively on metric evidence: verify resource usage, error rates,
            latency and availability relevant to the alert. Report exact values, series
            and time ranges. If no data matches, say so explicitly.
            Keep your findings under 200 words, concrete, with exact identifiers.
            """;

    private static final String KNOWLEDGE_SPECIALIST_PROMPT = """
            You are a KNOWLEDGE specialist on an SRE investigation team.
            You ONLY use these tools: search_kb, fetch_runbook, get_topology,
            get_deploy_window, get_alert_detail, summarize_output.
            Gather context: applicable runbooks and their key handling steps, known issues
            in the knowledge base, the service's topology, deploy windows and related alerts.
            Keep your findings under 200 words, concrete, with exact identifiers.
            """;

    private static final String SUPERVISOR_SYSTEM_PROMPT = """
            You are the SUPERVISOR of an SRE investigation team. Three specialist agents
            (log analyst, metric analyst, knowledge analyst) investigated a production alert
            in parallel and reported their findings below.

            Rules:
            - Base every claim ONLY on the specialists' findings. Never invent evidence.
            - QUOTE EVIDENCE VERBATIM: reproduce the exact error messages, exception types,
              hostnames and metric names from the findings in your analysis and in the JSON
              summary — never paraphrase technical identifiers.
            - If the findings implicate a downstream/dependency service, describe the
              root-cause chain explicitly (e.g. "payment-api times out -> db-primary slow").
            - If findings are missing, empty or contradictory, say so and lean INCONCLUSIVE
              with needsHuman=true.
            - Keep your analysis under 250 words.

            End your answer with the standard JSON block:
            {"verdict":"CONFIRMED|LIKELY_FALSE_POSITIVE|INCONCLUSIVE","confidence":0.0,"rootCause":"...","summary":"...","evidenceSummary":"...","suggestedActions":["..."],"needsHuman":true,"reasonForEscalation":"..."}
            """;

    private final ChatModel mainChatModel;
    private final AgentLoop agentLoop;
    private final ToolRegistry toolRegistry;
    private final UserPromptBuilder userPromptBuilder;
    private final AopsProperties props;
    private final Executor executor;

    public MultiAgentRunner(ChatModel mainChatModel,
                            AgentLoop agentLoop,
                            ToolRegistry toolRegistry,
                            UserPromptBuilder userPromptBuilder,
                            AopsProperties props,
                            @Qualifier("aopsExecutor") Executor executor) {
        this.mainChatModel = mainChatModel;
        this.agentLoop = agentLoop;
        this.toolRegistry = toolRegistry;
        this.userPromptBuilder = userPromptBuilder;
        this.props = props;
        this.executor = executor;
    }

    /** Result of one specialist pass. */
    public record SpecialistResult(String name, String findings, String error) {
        boolean ok() {
            return error == null;
        }
    }

    /**
     * Run the parallel specialists + supervisor synthesis.
     *
     * @return the supervisor's raw output (analysis + verdict JSON)
     */
    public String investigate(AlertEvent alert, TopologyService.ServiceAsset asset,
                              InvestigationContext ctx, Instant deadline) {
        String standardPrompt = userPromptBuilder.build(alert);

        List<SpecialistDef> specialists = List.of(
                new SpecialistDef("log_analyst", LOG_SPECIALIST_PROMPT, List.of(
                        "search_logs", "get_log_labels", "summarize_output")),
                new SpecialistDef("metric_analyst", METRIC_SPECIALIST_PROMPT, List.of(
                        "query_metric", "summarize_output")),
                new SpecialistDef("knowledge_analyst", KNOWLEDGE_SPECIALIST_PROMPT, List.of(
                        "search_kb", "fetch_runbook", "get_topology", "get_deploy_window",
                        "get_alert_detail", "summarize_output")));

        log.info("Multi-agent investigation: {} specialists in parallel for alert '{}'",
                specialists.size(), alert.alertName());

        List<CompletableFuture<SpecialistResult>> futures = new ArrayList<>();
        for (SpecialistDef def : specialists) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    SpecialistAgent agent = new SpecialistAgent(
                            def.name(), def.systemPrompt(),
                            selectTools(def.toolNames()),
                            SPECIALIST_MAX_STEPS,
                            mainChatModel, agentLoop,
                            props.agent().maxToolOutputChars(),
                            props.agent().llmRetries(),
                            props.agent().timeoutSeconds());
                    String taskPrompt = buildSpecialistTask(def.name(), standardPrompt, asset);
                    return new SpecialistResult(def.name(), agent.run(ctx, taskPrompt, deadline), null);
                } catch (Exception e) {
                    log.warn("Specialist '{}' failed: {}", def.name(), e.getMessage());
                    return new SpecialistResult(def.name(), null, e.getMessage());
                }
            }, executor));
        }

        List<SpecialistResult> results = new ArrayList<>();
        for (CompletableFuture<SpecialistResult> future : futures) {
            long remainingMs = remainingMillis(deadline);
            try {
                results.add(future.get(remainingMs, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                future.cancel(true);
                results.add(new SpecialistResult("?", null, "timed out"));
            } catch (Exception e) {
                results.add(new SpecialistResult("?", null, e.getMessage()));
            }
        }

        String synthesisPrompt = buildSynthesisPrompt(standardPrompt, results);
        return agentLoop.run(mainChatModel, SUPERVISOR_SYSTEM_PROMPT, synthesisPrompt,
                List.of(), props.agent().maxSteps(),
                props.agent().maxToolOutputChars(), props.agent().llmRetries(),
                props.agent().timeoutSeconds(), deadline);
    }

    private List<ToolSpecification> selectTools(List<String> names) {
        return toolRegistry.all().stream()
                .filter(t -> names.contains(t.name()))
                .map(t -> ToolSpecification.builder()
                        .name(t.name()).description(t.description()).parameters(t.parameters())
                        .build())
                .toList();
    }

    private String buildSpecialistTask(String specialistName, String standardPrompt,
                                       TopologyService.ServiceAsset asset) {
        String targetedSelectorHint = switch (specialistName) {
            case "log_analyst" -> """
                    Query the alert's OWN pod/service logs FIRST with a targeted selector
                    (e.g. {pod="<service-name>"} or {job="eval", pod="<service-name>"}),
                    then follow dependency leads (e.g. the downstream DB pod) with the same
                    targeted pattern. Broad selectors are truncated and can hide streams.
                    """;
            case "metric_analyst" -> """
                    Query the alert's OWN metrics FIRST (targeted label selectors on the
                    service name), then the metrics of any implicated dependency.
                    """;
            default -> "Use the alert's service name as the primary search target.";
        };
        return standardPrompt + "\n\n"
                + "# Your specialty: " + specialistName + "\n"
                + targetedSelectorHint + "\n"
                + "Analyze the alert from your specialty's angle only. "
                + "Quote exact error messages, exception types, hostnames and metric names "
                + "VERBATIM in your findings. "
                + "Return a concise findings summary (under 200 words).";
    }

    private String buildSynthesisPrompt(String standardPrompt, List<SpecialistResult> results) {
        StringBuilder sb = new StringBuilder(standardPrompt);
        sb.append("\n\n# Specialist findings\n");
        for (SpecialistResult r : results) {
            sb.append("## ").append(r.name()).append("\n");
            if (r.ok()) {
                sb.append(r.findings()).append("\n");
            } else {
                sb.append("(no findings — error: ").append(r.error()).append(")\n");
            }
        }
        sb.append("\nSynthesize the findings into the final verdict JSON.");
        return sb.toString();
    }

    private long remainingMillis(Instant deadline) {
        if (deadline == null) {
            return Duration.ofSeconds(120).toMillis();
        }
        return Math.max(1, Duration.between(Instant.now(), deadline).toMillis());
    }
}

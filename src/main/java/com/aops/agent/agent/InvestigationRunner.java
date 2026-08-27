package com.aops.agent.agent;

import com.aops.agent.config.AopsProperties;
import com.aops.agent.domain.AlertEvent;
import com.aops.agent.domain.CaseEntity;
import com.aops.agent.domain.CaseReport;
import com.aops.agent.domain.EvidenceEntity;
import com.aops.agent.domain.VerdictReport;
import com.aops.agent.notifier.Notifier;
import com.aops.agent.service.CaseService;
import com.aops.agent.service.TopologyService;
import com.aops.agent.tool.ToolRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates one investigation: builds the prompts, runs the agentic loop
 * (single-agent with all tools, or Phase-3 supervisor mode with parallel
 * specialists), extracts the verdict, persists the evidence chain, completes
 * the case and notifies.
 */
@Service
public class InvestigationRunner {

    private static final Logger log = LoggerFactory.getLogger(InvestigationRunner.class);

    private final CaseService caseService;
    private final ToolRegistry toolRegistry;
    private final ChatModel mainChatModel;
    private final AgentLoop agentLoop;
    private final MultiAgentRunner multiAgentRunner;
    private final SystemPromptBuilder systemPromptBuilder;
    private final UserPromptBuilder userPromptBuilder;
    private final VerdictExtractor verdictExtractor;
    private final ReportBuilder reportBuilder;
    private final TopologyService topologyService;
    private final Notifier notifier;
    private final AopsProperties props;
    private final ObjectMapper mapper;

    public InvestigationRunner(CaseService caseService,
                               ToolRegistry toolRegistry,
                               ChatModel mainChatModel,
                               AgentLoop agentLoop,
                               MultiAgentRunner multiAgentRunner,
                               SystemPromptBuilder systemPromptBuilder,
                               UserPromptBuilder userPromptBuilder,
                               VerdictExtractor verdictExtractor,
                               ReportBuilder reportBuilder,
                               TopologyService topologyService,
                               Notifier notifier,
                               AopsProperties props,
                               ObjectMapper mapper) {
        this.caseService = caseService;
        this.toolRegistry = toolRegistry;
        this.mainChatModel = mainChatModel;
        this.agentLoop = agentLoop;
        this.multiAgentRunner = multiAgentRunner;
        this.systemPromptBuilder = systemPromptBuilder;
        this.userPromptBuilder = userPromptBuilder;
        this.verdictExtractor = verdictExtractor;
        this.reportBuilder = reportBuilder;
        this.topologyService = topologyService;
        this.notifier = notifier;
        this.props = props;
        this.mapper = mapper;
    }

    @Async("aopsExecutor")
    public void investigate(String caseId) {
        investigate(caseId, null);
    }

    /**
     * Investigate a case. When {@code customUserPrompt} is provided (question
     * mode — used by QA evals), it replaces the alert-derived user prompt.
     */
    @Async("aopsExecutor")
    public void investigate(String caseId, String customUserPrompt) {
        CaseEntity entity = caseService.findById(caseId).orElse(null);
        if (entity == null) {
            log.warn("Investigation requested for unknown case {}", caseId);
            return;
        }
        InvestigationContext ctx = null;
        try {
            caseService.transitionUnchecked(entity, com.aops.agent.domain.CaseStatus.INVESTIGATING);
            AlertEvent alert = reconstructAlert(entity);
            Optional<TopologyService.ServiceAsset> asset = topologyService.findByAlert(alert);
            ctx = new InvestigationContext(caseId, alert, asset.orElse(null));
            InvestigationContextHolder.set(ctx);

            String systemPrompt = systemPromptBuilder.build(toolRegistry.specifications());
            String userPrompt = (customUserPrompt == null || customUserPrompt.isBlank())
                    ? userPromptBuilder.build(alert)
                    : customUserPrompt;

            VerdictReport verdict;
            String rawText;
            if (!props.agent().enabled() || !props.agent().hasApiKey()) {
                log.info("Case {}: shadow mode (agent disabled or no API key) — skipping LLM", caseId);
                rawText = "[shadow mode] Agent disabled or no LLM API key configured. Investigation skipped.";
                verdict = VerdictReport.fallback(rawText,
                        "Agent disabled (shadow mode) or no LLM API key configured.");
            } else {
                Instant deadline = Instant.now().plusSeconds(props.agent().timeoutSeconds());
                if (props.agent().supervisorMode() && customUserPrompt == null) {
                    log.info("Case {}: supervisor mode — parallel specialists", caseId);
                    rawText = multiAgentRunner.investigate(alert, asset.orElse(null), ctx, deadline);
                } else {
                    rawText = agentLoop.run(mainChatModel, systemPrompt, userPrompt,
                            toolRegistry.specifications(),
                            props.agent().maxSteps(), props.agent().maxToolOutputChars(),
                            props.agent().llmRetries(), props.agent().timeoutSeconds(), deadline);
                }
                verdict = verdictExtractor.extract(rawText);
            }

            List<EvidenceEntity> evidence = ctx.getEvidence();
            caseService.saveEvidence(evidence);
            com.aops.agent.domain.CaseStatus finalStatus = caseService.finalStatusFor(verdict);
            String reportMarkdown = reportBuilder.build(entity, verdict, evidence, rawText, finalStatus);
            caseService.complete(entity, verdict, reportMarkdown);

            notifier.send(toCaseReport(entity, verdict, evidence.size()));
        } catch (Exception e) {
            log.error("Investigation failed for case {}", caseId, e);
            try {
                caseService.markError(entity, e.getMessage());
            } catch (Exception ex) {
                log.error("Failed to mark case {} as ERROR", caseId, ex);
            }
        } finally {
            InvestigationContextHolder.clear();
        }
    }

    private AlertEvent reconstructAlert(CaseEntity entity) {
        Map<String, String> labels = readJsonMap(entity.getLabelsJson());
        Map<String, String> annotations = readJsonMap(entity.getAnnotationsJson());
        return new AlertEvent(
                entity.getAlertFingerprint(),
                entity.getAlertStatus(),
                entity.getAlertName(),
                labels,
                annotations,
                entity.getAlertStartedAt(),
                entity.getAlertEndedAt(),
                null,
                entity.getServiceName() != null ? entity.getServiceName()
                        : AlertEvent.deriveService(labels));
    }

    private Map<String, String> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private CaseReport toCaseReport(CaseEntity entity, VerdictReport verdict, int evidenceCount) {
        return new CaseReport(
                entity.getId(),
                entity.getAlertName(),
                entity.getServiceName(),
                entity.getStatus(),
                entity.getVerdict(),
                entity.getConfidence(),
                entity.getRootCause(),
                entity.getSummary(),
                verdict == null ? null : verdict.evidenceSummary(),
                verdict == null || verdict.suggestedActions() == null ? List.of() : verdict.suggestedActions(),
                evidenceCount,
                entity.isNeedsHuman(),
                verdict == null ? null : verdict.reasonForEscalation(),
                entity.getReportMarkdown()
        );
    }
}

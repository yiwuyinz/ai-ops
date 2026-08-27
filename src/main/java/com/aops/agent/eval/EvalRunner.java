package com.aops.agent.eval;

import com.aops.agent.config.AopsProperties;
import com.aops.agent.domain.AlertEvent;
import com.aops.agent.domain.CaseEntity;
import com.aops.agent.domain.CaseStatus;
import com.aops.agent.domain.EvidenceEntity;
import com.aops.agent.service.AlertIntakeService;
import com.aops.agent.service.CaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs evaluation scenarios through the REAL alert pipeline (intake -> case ->
 * investigation -> verdict) and scores the finished case with
 * {@link EvalEvaluator}. Results are kept in memory and written to
 * {@code evals/report-<runId>.md}.
 */
@Service
public class EvalRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);
    private static final Duration WAIT_TIMEOUT = Duration.ofMinutes(6);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final int EXCERPT_CHARS = 600;

    private final EvalScenarioService scenarioService;
    private final AlertIntakeService alertIntakeService;
    private final CaseService caseService;
    private final com.aops.agent.agent.InvestigationRunner investigationRunner;
    private final EvalEvaluator evaluator;
    private final AopsProperties props;
    private final Map<String, EvalRun> runs = new ConcurrentHashMap<>();

    public EvalRunner(EvalScenarioService scenarioService,
                      AlertIntakeService alertIntakeService,
                      CaseService caseService,
                      com.aops.agent.agent.InvestigationRunner investigationRunner,
                      EvalEvaluator evaluator,
                      AopsProperties props) {
        this.scenarioService = scenarioService;
        this.alertIntakeService = alertIntakeService;
        this.caseService = caseService;
        this.investigationRunner = investigationRunner;
        this.evaluator = evaluator;
        this.props = props;
    }

    /** Fire an async eval run for one scenario id or "all". Returns the run id. */
    public String start(String scenarioId) {
        String runId = UUID.randomUUID().toString();
        run(runId, scenarioId);
        return runId;
    }

    @Async("aopsExecutor")
    public void run(String runId, String scenarioId) {
        Instant started = Instant.now();
        boolean shadowMode = !props.agent().enabled() || !props.agent().hasApiKey();
        List<EvalScenario> scenarios = scenarioId == null || "all".equalsIgnoreCase(scenarioId)
                ? scenarioService.all()
                : List.of(scenarioService.findById(scenarioId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown eval scenario: " + scenarioId)));

        List<EvalResult> results = new ArrayList<>();
        for (EvalScenario scenario : scenarios) {
            results.add(shadowMode ? skippedResult(scenario) : runScenario(runId, scenario));
        }
        EvalRun run = new EvalRun(runId, started, Instant.now(), results, shadowMode);
        runs.put(runId, run);
        writeReport(run);
        log.info("Eval run {} finished: {}/{} passed (shadowMode={})",
                runId, run.passed(), run.total(), shadowMode);
    }

    private EvalResult runScenario(String runId, EvalScenario scenario) {
        Instant started = Instant.now();
        String caseId = null;
        try {
            if (scenario.isQuestionMode()) {
                // Question mode: create a synthetic case and ask the agent directly.
                Map<String, String> labels = scenario.alert() != null && scenario.alert().labels() != null
                        ? scenario.alert().labels()
                        : Map.of("service", "eval");
                String alertName = scenario.alert() != null && scenario.alert().alertName() != null
                        ? scenario.alert().alertName()
                        : scenario.id();
                AlertEvent synthetic = new AlertEvent(
                        "eval-" + scenario.id() + "-" + runId, "firing", alertName,
                        labels, Map.of(), Instant.now(), null, null,
                        labels.getOrDefault("service", "eval"));
                CaseEntity created = caseService.createCase(synthetic);
                caseId = created.getId();
                investigationRunner.investigate(caseId, scenario.userPrompt());
            } else {
                AlertEvent alert = toAlert(scenario, runId);
                CaseEntity created = alertIntakeService.ingest(alert);
                if (created == null) {
                    return failed(scenario, started, null,
                            "alert was rejected by dedup (unexpected for an eval fingerprint)");
                }
                caseId = created.getId();
            }
            CaseEntity terminal = waitForCompletion(caseId);
            List<String> tools = caseService.evidenceFor(caseId).stream()
                    .map(EvidenceEntity::getToolName).distinct().toList();
            String verdict = terminal.getVerdict() == null ? "ERROR" : terminal.getVerdict().name();

            List<String> failures = evaluator.evaluate(scenario, verdict, terminal.getConfidence(),
                    terminal.isNeedsHuman(), tools, terminal.getReportMarkdown());
            return new EvalResult(scenario.id(), scenario.name(), caseId,
                    scenario.expectedVerdict(), verdict, terminal.getConfidence(), terminal.isNeedsHuman(),
                    tools, failures.isEmpty(), failures,
                    excerpt(terminal.getReportMarkdown()), null, started, Instant.now());
        } catch (Exception e) {
            log.warn("Eval scenario {} failed: {}", scenario.id(), e.getMessage());
            return failed(scenario, started, caseId, e.getMessage());
        }
    }

    private CaseEntity waitForCompletion(String caseId) throws InterruptedException {
        Instant deadline = Instant.now().plus(WAIT_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            CaseEntity entity = caseService.findById(caseId)
                    .orElseThrow(() -> new IllegalStateException("Case vanished: " + caseId));
            CaseStatus status = entity.getStatus();
            if (status == CaseStatus.REPORTED || status == CaseStatus.ESCALATED
                    || status == CaseStatus.FALSE_POSITIVE || status == CaseStatus.CLOSED
                    || status == CaseStatus.ERROR) {
                return entity;
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
        throw new IllegalStateException("investigation did not finish within " + WAIT_TIMEOUT);
    }

    private EvalResult skippedResult(EvalScenario scenario) {
        return failed(scenario, Instant.now(), null,
                "shadow mode: configure DEEPSEEK_API_KEY (and aops.agent.enabled=true) to run evals");
    }

    private EvalResult failed(EvalScenario scenario, Instant started, String caseId, String error) {
        return new EvalResult(scenario.id(), scenario.name(), caseId,
                scenario.expectedVerdict(), null, null, null,
                List.of(), false, List.of("error: " + error), null, error, started, Instant.now());
    }

    private AlertEvent toAlert(EvalScenario scenario, String runId) {
        EvalScenario.AlertSpec spec = scenario.alert();
        Map<String, String> labels = new HashMap<>(spec.labels() == null ? Map.of() : spec.labels());
        labels.putIfAbsent("alertname", spec.alertName());
        return new AlertEvent(
                "eval-" + scenario.id() + "-" + runId,
                spec.status() == null ? "firing" : spec.status(),
                spec.alertName(),
                labels,
                spec.annotations() == null ? Map.of() : spec.annotations(),
                parseInstant(spec.startsAt()),
                null,
                null,
                AlertEvent.deriveService(labels));
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String excerpt(String report) {
        if (report == null) {
            return "";
        }
        return report.length() <= EXCERPT_CHARS ? report : report.substring(0, EXCERPT_CHARS) + "...";
    }

    private void writeReport(EvalRun run) {
        try {
            Path dir = Path.of("evals");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("report-" + run.runId() + ".md"), markdown(run));
        } catch (IOException e) {
            log.warn("Failed to write eval report: {}", e.getMessage());
        }
    }

    /** Markdown report for humans: pass/fail per scenario plus the run summary. */
    public static String markdown(EvalRun run) {
        DateTimeFormatter ts = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);
        StringBuilder sb = new StringBuilder();
        sb.append("# Eval Report ").append(run.runId()).append("\n\n");
        sb.append("- started: ").append(ts.format(run.startedAt())).append("\n");
        sb.append("- finished: ").append(run.finishedAt() == null ? "-" : ts.format(run.finishedAt())).append("\n");
        sb.append("- result: **").append(run.passed()).append("/").append(run.total()).append(" passed**")
                .append(run.skipped() ? " (skipped: agent not in LLM mode)" : "").append("\n\n");

        sb.append("| Scenario | Verdict (exp -> act) | Confidence | needsHuman | Tools | Result |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (EvalResult r : run.results()) {
            sb.append("| ").append(r.scenarioId())
                    .append(" | ").append(display(r.expectedVerdict())).append(" -> ")
                    .append(display(r.actualVerdict()))
                    .append(" | ").append(r.actualConfidence() == null ? "-" : String.format("%.2f", r.actualConfidence()))
                    .append(" | ").append(r.actualNeedsHuman() == null ? "-" : r.actualNeedsHuman())
                    .append(" | ").append(String.join(",", r.evidenceTools()))
                    .append(" | ").append(r.passed() ? "✅ PASS" : "❌ FAIL").append(" |\n");
        }

        boolean anyFailure = run.results().stream().anyMatch(r -> !r.passed());
        if (anyFailure) {
            sb.append("\n## Failures\n");
            for (EvalResult r : run.results()) {
                if (!r.passed()) {
                    sb.append("\n### ").append(r.scenarioId()).append("\n");
                    for (String f : r.failures()) {
                        sb.append("- ").append(f).append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String display(String s) {
        return s == null || s.isBlank() ? "any" : s;
    }

    public List<EvalRun> recent() {
        return runs.values().stream()
                .sorted((a, b) -> b.startedAt().compareTo(a.startedAt()))
                .toList();
    }

    public EvalRun get(String runId) {
        return runs.get(runId);
    }
}

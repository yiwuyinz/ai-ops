package com.aops.agent.web;

import com.aops.agent.eval.EvalRun;
import com.aops.agent.eval.EvalRunner;
import com.aops.agent.eval.EvalScenario;
import com.aops.agent.eval.EvalScenarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Eval endpoints: list scenarios, start a run, inspect run results.
 */
@RestController
@RequestMapping("/api/evals")
public class EvalController {

    private final EvalScenarioService scenarioService;
    private final EvalRunner runner;

    public EvalController(EvalScenarioService scenarioService, EvalRunner runner) {
        this.scenarioService = scenarioService;
        this.runner = runner;
    }

    @GetMapping("/scenarios")
    public List<Map<String, String>> scenarios() {
        return scenarioService.all().stream()
                .map(s -> Map.of("id", s.id(), "name", s.name(), "description", s.description()))
                .toList();
    }

    /** Start an async eval run for one scenario id or "all". */
    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> run(@RequestParam(defaultValue = "all") String scenario) {
        if (!"all".equalsIgnoreCase(scenario) && scenarioService.findById(scenario).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown eval scenario: " + scenario);
        }
        String runId = runner.start(scenario);
        return ResponseEntity.accepted().body(Map.of("runId", runId));
    }

    @GetMapping("/runs")
    public List<Map<String, Object>> runs() {
        return runner.recent().stream()
                .map(r -> Map.<String, Object>of(
                        "runId", r.runId(),
                        "startedAt", r.startedAt(),
                        "finishedAt", r.finishedAt() == null ? null : r.finishedAt(),
                        "passed", r.passed(),
                        "total", r.total(),
                        "skipped", r.skipped()))
                .toList();
    }

    @GetMapping("/runs/{runId}")
    public Map<String, Object> runDetail(@PathVariable String runId) {
        EvalRun run = runner.get(runId);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown eval run: " + runId);
        }
        return Map.of(
                "runId", run.runId(),
                "startedAt", run.startedAt(),
                "finishedAt", run.finishedAt(),
                "passed", run.passed(),
                "total", run.total(),
                "skipped", run.skipped(),
                "results", run.results(),
                "reportMarkdown", EvalRunner.markdown(run));
    }
}

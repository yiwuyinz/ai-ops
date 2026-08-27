package com.aops.agent.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalEvaluatorTest {

    private final EvalEvaluator evaluator = new EvalEvaluator();

    private EvalScenario scenario(String expectedVerdict, Double minConfidence, Boolean expectNeedsHuman,
                                  List<String> requiredTools, List<String> mustContain) {
        return new EvalScenario("s1", "test", "desc",
                new EvalScenario.AlertSpec("TestAlert", "firing", java.util.Map.of("service", "demo"), null, null),
                null,
                expectedVerdict, minConfidence, expectNeedsHuman, requiredTools, mustContain);
    }

    @Test
    void allAssertionsPass() {
        EvalScenario s = scenario("CONFIRMED", 0.6, false,
                List.of("query_metric", "search_logs"), List.of("up{job=\"demo-app\"}"));
        List<String> failures = evaluator.evaluate(s, "CONFIRMED", 0.9, false,
                List.of("query_metric", "search_logs", "fetch_runbook"),
                "report mentions up{job=\"demo-app\"} = 0");
        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void wrongVerdictFails() {
        EvalScenario s = scenario("CONFIRMED", null, null, null, null);
        List<String> failures = evaluator.evaluate(s, "INCONCLUSIVE", 0.9, false, List.of(), "r");
        assertEquals(1, failures.size());
        assertTrue(failures.get(0).contains("verdict"), failures.get(0));
    }

    @Test
    void lowConfidenceFails() {
        EvalScenario s = scenario(null, 0.6, null, null, null);
        List<String> failures = evaluator.evaluate(s, "CONFIRMED", 0.3, false, List.of(), "r");
        assertEquals(1, failures.size());
        assertTrue(failures.get(0).contains("confidence"), failures.get(0));
    }

    @Test
    void missingRequiredToolFails() {
        EvalScenario s = scenario(null, null, null, List.of("query_metric"), null);
        List<String> failures = evaluator.evaluate(s, "CONFIRMED", 0.9, false, List.of("search_logs"), "r");
        assertEquals(1, failures.size());
        assertTrue(failures.get(0).contains("query_metric"), failures.get(0));
    }

    @Test
    void missingTextInReportFails() {
        EvalScenario s = scenario(null, null, null, null, List.of("No log lines matched"));
        List<String> failures = evaluator.evaluate(s, "CONFIRMED", 0.9, false, List.of(), "found nothing");
        assertEquals(1, failures.size());
        assertTrue(failures.get(0).contains("No log lines matched"), failures.get(0));
    }

    @Test
    void anyVerdictNeverFailsOnVerdict() {
        EvalScenario s = scenario("any", null, null, null, null);
        assertTrue(evaluator.evaluate(s, "INCONCLUSIVE", 0.1, true, List.of(), "r").isEmpty());
    }

    @Test
    void needsHumanMismatchFails() {
        EvalScenario s = scenario(null, null, true, null, null);
        List<String> failures = evaluator.evaluate(s, "CONFIRMED", 0.9, false, List.of(), "r");
        assertEquals(1, failures.size());
        assertTrue(failures.get(0).contains("needsHuman"), failures.get(0));
    }
}

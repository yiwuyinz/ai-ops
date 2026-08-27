package com.aops.agent.eval;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure assertion logic for eval scenarios — no I/O, fully unit-testable.
 * Returns the list of failed assertions (empty = scenario passed).
 */
@Component
public class EvalEvaluator {

    public List<String> evaluate(EvalScenario scenario,
                                 String actualVerdict,
                                 Double confidence,
                                 Boolean needsHuman,
                                 List<String> evidenceTools,
                                 String reportText) {
        List<String> failures = new ArrayList<>();

        if (!scenario.expectsAnyVerdict()) {
            String expected = scenario.expectedVerdict().trim().toUpperCase();
            String actual = actualVerdict == null ? "" : actualVerdict.trim().toUpperCase();
            if (!expected.equals(actual)) {
                failures.add("verdict: expected " + expected + " but got " + actual);
            }
        }
        if (scenario.minConfidence() != null
                && (confidence == null || confidence < scenario.minConfidence())) {
            failures.add("confidence: expected >= " + scenario.minConfidence() + " but got " + confidence);
        }
        if (scenario.expectNeedsHuman() != null && !scenario.expectNeedsHuman().equals(needsHuman)) {
            failures.add("needsHuman: expected " + scenario.expectNeedsHuman() + " but got " + needsHuman);
        }
        if (scenario.requiredTools() != null) {
            for (String tool : scenario.requiredTools()) {
                if (evidenceTools == null || !evidenceTools.contains(tool)) {
                    failures.add("required tool '" + tool + "' was never called");
                }
            }
        }
        if (scenario.mustContain() != null && reportText != null) {
            for (String s : scenario.mustContain()) {
                if (!reportText.contains(s)) {
                    failures.add("report must contain \"" + s + "\"");
                }
            }
        }
        return failures;
    }
}

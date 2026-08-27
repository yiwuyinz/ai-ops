package com.aops.agent.eval;

import java.util.List;
import java.util.Map;

/**
 * One evaluation scenario, fed through the REAL investigation pipeline.
 *
 * <p>Two modes:</p>
 * <ul>
 *   <li><b>alert mode</b> (HolmesGPT-style): {@code alert} is set, the intake
 *       pipeline runs (dedup -> case -> investigation);</li>
 *   <li><b>question mode</b>: {@code userPrompt} is set, the agent is asked the
 *       question directly (no alert) — used for QA / transparency evals.</li>
 * </ul>
 *
 * <p>Anti-hallucination pattern (HolmesGPT-style): {@code mustContain} can check
 * for strings that only real tool output would produce (e.g. "No log lines
 * matched"), and {@code requiredTools} verifies the agent actually queried the
 * expected sources instead of guessing.</p>
 */
public record EvalScenario(
        String id,
        String name,
        String description,
        AlertSpec alert,
        /** Question-mode prompt (mutually exclusive with alert). */
        String userPrompt,
        /** CONFIRMED | LIKELY_FALSE_POSITIVE | INCONCLUSIVE | any (blank = any). */
        String expectedVerdict,
        Double minConfidence,
        Boolean expectNeedsHuman,
        List<String> requiredTools,
        List<String> mustContain
) {

    public record AlertSpec(
            String alertName,
            String status,
            Map<String, String> labels,
            Map<String, String> annotations,
            String startsAt
    ) {
    }

    public boolean isQuestionMode() {
        return userPrompt != null && !userPrompt.isBlank();
    }

    public boolean expectsAnyVerdict() {
        return expectedVerdict == null || expectedVerdict.isBlank()
                || "any".equalsIgnoreCase(expectedVerdict);
    }
}

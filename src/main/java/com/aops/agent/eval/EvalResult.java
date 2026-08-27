package com.aops.agent.eval;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of running one scenario: what happened, which assertions failed.
 */
public record EvalResult(
        String scenarioId,
        String name,
        String caseId,
        String expectedVerdict,
        String actualVerdict,
        Double actualConfidence,
        Boolean actualNeedsHuman,
        List<String> evidenceTools,
        boolean passed,
        List<String> failures,
        String reportExcerpt,
        String error,
        Instant startedAt,
        Instant finishedAt
) {
}

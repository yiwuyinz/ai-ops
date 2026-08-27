package com.aops.agent.domain;

import java.util.List;

/**
 * Structured verdict extracted from the LLM report.
 *
 * @param rawText the full agent output (kept for audit)
 */
public record VerdictReport(
        Verdict verdict,
        Double confidence,
        String rootCause,
        String summary,
        String evidenceSummary,
        List<String> suggestedActions,
        boolean needsHuman,
        String reasonForEscalation,
        String rawText
) {

    public static VerdictReport fallback(String rawText, String reason) {
        return new VerdictReport(
                Verdict.INCONCLUSIVE,
                0.0,
                null,
                reason,
                null,
                List.of(),
                true,
                reason,
                rawText
        );
    }
}

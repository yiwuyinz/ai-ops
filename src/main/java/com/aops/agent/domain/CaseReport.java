package com.aops.agent.domain;

import java.util.List;

/**
 * Final report handed to the Notifier (Slack / console / etc.).
 */
public record CaseReport(
        String caseId,
        String alertName,
        String serviceName,
        CaseStatus status,
        Verdict verdict,
        Double confidence,
        String rootCause,
        String summary,
        String evidenceSummary,
        List<String> suggestedActions,
        int evidenceCount,
        boolean needsHuman,
        String reasonForEscalation,
        String reportMarkdown
) {
}

package com.aops.agent.web.dto;

import com.aops.agent.domain.CaseStatus;
import com.aops.agent.domain.Verdict;

import java.time.Instant;

/**
 * Summary view of a case for list endpoints.
 */
public record CaseView(
        String id,
        String alertName,
        String serviceName,
        CaseStatus status,
        Verdict verdict,
        Double confidence,
        boolean needsHuman,
        Instant createdAt,
        Instant investigatedAt
) {
}

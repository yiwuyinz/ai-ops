package com.aops.agent.web.dto;

import com.aops.agent.domain.FeedbackOutcome;
import jakarta.validation.constraints.NotNull;

/**
 * Human feedback on a case.
 */
public record FeedbackRequest(
        @NotNull FeedbackOutcome outcome,
        String comment
) {
}

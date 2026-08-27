package com.aops.agent.web;

import com.aops.agent.domain.FeedbackEntity;
import com.aops.agent.service.FeedbackService;
import com.aops.agent.web.dto.FeedbackRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Feedback endpoints: humans annotate cases (false positive / true positive /
 * resolved), feeding the tuning flywheel.
 */
@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/cases/{id}/feedback")
    public FeedbackEntity feedback(@PathVariable String id, @Valid @RequestBody FeedbackRequest request) {
        return feedbackService.record(id, request.outcome(), request.comment());
    }

    /** Per-alert false-positive statistics for the tuning report. */
    @GetMapping("/feedback/stats")
    public Map<String, long[]> stats() {
        return feedbackService.falsePositiveStats();
    }
}

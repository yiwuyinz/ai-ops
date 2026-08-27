package com.aops.agent.service;

import com.aops.agent.domain.CaseEntity;
import com.aops.agent.domain.CaseStatus;
import com.aops.agent.domain.FeedbackEntity;
import com.aops.agent.domain.FeedbackOutcome;
import com.aops.agent.repository.CaseRepository;
import com.aops.agent.repository.FeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Feedback flywheel: human verdicts are stored and reflected back into the
 * case status (FALSE_POSITIVE -> case closed as FP; TRUE_POSITIVE/RESOLVED ->
 * closed as confirmed). Aggregate stats feed the false-positive report.
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final FeedbackRepository feedbackRepository;
    private final CaseRepository caseRepository;

    public FeedbackService(FeedbackRepository feedbackRepository, CaseRepository caseRepository) {
        this.feedbackRepository = feedbackRepository;
        this.caseRepository = caseRepository;
    }

    @Transactional
    public FeedbackEntity record(String caseId, FeedbackOutcome outcome, String comment) {
        CaseEntity caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        FeedbackEntity feedback = FeedbackEntity.builder()
                .id(UUID.randomUUID().toString())
                .caseId(caseId)
                .outcome(outcome)
                .comment(comment)
                .createdAt(Instant.now())
                .build();
        feedbackRepository.save(feedback);

        switch (outcome) {
            case FALSE_POSITIVE -> setStatusQuietly(caseEntity, CaseStatus.FALSE_POSITIVE);
            case TRUE_POSITIVE, RESOLVED -> setStatusQuietly(caseEntity, CaseStatus.CLOSED);
            case UNCERTAIN -> log.info("Feedback UNCERTAIN for case {}", caseId);
        }
        log.info("Feedback recorded for case {}: {}", caseId, outcome);
        return feedback;
    }

    private void setStatusQuietly(CaseEntity caseEntity, CaseStatus target) {
        CaseStatus current = caseEntity.getStatus();
        boolean allowed = switch (current) {
            case REPORTED, ESCALATED, FALSE_POSITIVE, CLOSED -> true;
            default -> false;
        };
        if (allowed && current != target) {
            caseEntity.setStatus(target);
            caseEntity.setUpdatedAt(Instant.now());
            caseRepository.save(caseEntity);
        }
    }

    public List<FeedbackEntity> byCase(String caseId) {
        return feedbackRepository.findByCaseId(caseId);
    }

    /**
     * Per-alert false positive statistics for the tuning report.
     *
     * @return alertName -> [totalCases, falsePositives, truePositives]
     */
    public Map<String, long[]> falsePositiveStats() {
        Map<String, long[]> stats = new HashMap<>();
        for (CaseEntity c : caseRepository.findAll()) {
            long[] row = stats.computeIfAbsent(c.getAlertName(), k -> new long[3]);
            row[0]++;
            List<FeedbackEntity> feedback = feedbackRepository.findByCaseId(c.getId());
            if (feedback.isEmpty()) {
                if (c.getStatus() == CaseStatus.FALSE_POSITIVE) {
                    row[1]++;
                }
                continue;
            }
            boolean fp = feedback.stream().anyMatch(f -> f.getOutcome() == FeedbackOutcome.FALSE_POSITIVE);
            boolean tp = feedback.stream().anyMatch(f -> f.getOutcome() == FeedbackOutcome.TRUE_POSITIVE
                    || f.getOutcome() == FeedbackOutcome.RESOLVED);
            if (fp) {
                row[1]++;
            }
            if (tp) {
                row[2]++;
            }
        }
        return stats;
    }
}

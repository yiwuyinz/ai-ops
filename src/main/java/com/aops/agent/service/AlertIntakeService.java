package com.aops.agent.service;

import com.aops.agent.agent.InvestigationRunner;
import com.aops.agent.domain.AlertEvent;
import com.aops.agent.domain.CaseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Central alert intake: dedup -> create case -> trigger async investigation.
 * Shared by the AlertManager webhook and the simple-alert endpoint.
 *
 * <p>Dedup is two-layered: the fast cache (memory/Redis) first, then the database
 * unique constraint as the source of truth — in-memory dedup is reset on restart,
 * so a case for the same fingerprint may already exist in the DB.</p>
 */
@Service
public class AlertIntakeService {

    private final DedupService dedupService;
    private final CaseService caseService;
    private final InvestigationRunner investigationRunner;

    public AlertIntakeService(DedupService dedupService,
                              CaseService caseService,
                              InvestigationRunner investigationRunner) {
        this.dedupService = dedupService;
        this.caseService = caseService;
        this.investigationRunner = investigationRunner;
    }

    public record IntakeResult(int accepted, int duplicates) {
    }

    /**
     * @return the created case, or null when the alert was a duplicate.
     */
    public CaseEntity ingest(AlertEvent alert) {
        if (dedupService.isDuplicate(alert.dedupKey())) {
            return null;
        }
        // DB is the source of truth: a case for this fingerprint may exist from a
        // previous run (the in-memory cache was reset on restart).
        Optional<CaseEntity> existing = caseService.findByFingerprint(alert.dedupKey());
        if (existing.isPresent()) {
            return null;
        }
        try {
            CaseEntity entity = caseService.createCase(alert);
            investigationRunner.investigate(entity.getId());
            return entity;
        } catch (DataIntegrityViolationException e) {
            // Concurrent duplicate insert (e.g. two webhook deliveries racing).
            return null;
        }
    }
}

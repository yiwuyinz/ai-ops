package com.aops.agent.service;

import com.aops.agent.agent.InvestigationRunner;
import com.aops.agent.domain.AlertEvent;
import com.aops.agent.domain.CaseEntity;
import com.aops.agent.domain.CaseStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertIntakeServiceTest {

    private final DedupService dedupService = mock(DedupService.class);
    private final CaseService caseService = mock(CaseService.class);
    private final InvestigationRunner runner = mock(InvestigationRunner.class);
    private final AlertIntakeService intake = new AlertIntakeService(dedupService, caseService, runner);

    private AlertEvent alert() {
        return new AlertEvent("fp-1", "firing", "DemoAppDown",
                Map.of("alertname", "DemoAppDown", "service", "demo-app"),
                Map.of("summary", "demo down"), Instant.now(), null, null, "demo-app");
    }

    @Test
    void newAlertCreatesCaseAndTriggersInvestigation() {
        when(dedupService.isDuplicate(any())).thenReturn(false);
        CaseEntity created = CaseEntity.builder().id("case-1").status(CaseStatus.NEW)
                .alertName("DemoAppDown").build();
        when(caseService.createCase(any())).thenReturn(created);

        CaseEntity result = intake.ingest(alert());

        assertNotNull(result);
        verify(runner).investigate("case-1");
    }

    @Test
    void duplicateAlertIsIgnored() {
        when(dedupService.isDuplicate(any())).thenReturn(true);

        CaseEntity result = intake.ingest(alert());

        assertNull(result);
        verify(caseService, never()).createCase(any());
        verify(runner, never()).investigate(any());
    }

    /**
     * Regression: after an app restart the in-memory dedup cache is empty, but the
     * case may already exist in the DB (unique constraint on alert_fingerprint).
     * The DB lookup must treat it as a duplicate instead of crashing with a 500.
     */
    @Test
    void existingCaseInDbIsTreatedAsDuplicate() {
        when(dedupService.isDuplicate(any())).thenReturn(false);
        CaseEntity existing = CaseEntity.builder().id("old-case").status(CaseStatus.REPORTED)
                .alertName("DemoAppDown").build();
        when(caseService.findByFingerprint(any())).thenReturn(java.util.Optional.of(existing));

        CaseEntity result = intake.ingest(alert());

        assertNull(result);
        verify(caseService, never()).createCase(any());
        verify(runner, never()).investigate(any());
    }

    /**
     * Regression: a race between two identical webhook deliveries must not 500 —
     * the unique-constraint exception is caught and treated as a duplicate.
     */
    @Test
    void concurrentDuplicateInsertIsSwallowed() {
        when(dedupService.isDuplicate(any())).thenReturn(false);
        when(caseService.createCase(any())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        CaseEntity result = intake.ingest(alert());

        assertNull(result);
        verify(runner, never()).investigate(any());
    }
}

package com.aops.agent.service;

import com.aops.agent.domain.CaseEntity;
import com.aops.agent.domain.CaseStatus;
import com.aops.agent.domain.Verdict;
import com.aops.agent.domain.VerdictReport;
import com.aops.agent.repository.CaseRepository;
import com.aops.agent.repository.EvidenceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CaseStateMachineTest {

    private CaseRepository caseRepository;
    private CaseService caseService;

    @BeforeEach
    void setUp() {
        caseRepository = mock(CaseRepository.class);
        EvidenceRepository evidenceRepository = mock(EvidenceRepository.class);
        caseService = new CaseService(caseRepository, evidenceRepository, new ObjectMapper(),
                com.aops.agent.TestProps.defaultProps());
    }

    private CaseEntity caseWithStatus(CaseStatus status) {
        return CaseEntity.builder()
                .id("case-1")
                .alertFingerprint("fp")
                .alertName("TestAlert")
                .serviceName("demo")
                .status(status)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void newToInvestigatingIsAllowed() {
        CaseEntity c = caseWithStatus(CaseStatus.NEW);
        caseService.transitionUnchecked(c, CaseStatus.INVESTIGATING);
        assertEquals(CaseStatus.INVESTIGATING, c.getStatus());
    }

    @Test
    void newToReportedIsRejected() {
        CaseEntity c = caseWithStatus(CaseStatus.NEW);
        assertThrows(IllegalStateException.class,
                () -> caseService.transitionUnchecked(c, CaseStatus.REPORTED));
    }

    @Test
    void confirmedVerdictCompletesAsReported() {
        CaseEntity c = caseWithStatus(CaseStatus.INVESTIGATING);
        VerdictReport verdict = new VerdictReport(
                Verdict.CONFIRMED, 0.9, "DB connection pool exhausted", "summary",
                "evidence", List.of("scale db"), false, "", "raw");
        caseService.complete(c, verdict, "# report");
        assertEquals(CaseStatus.REPORTED, c.getStatus());
        assertEquals(0.9, c.getConfidence());
        assertTrue(c.getSuggestedActionsJson() != null
                        && c.getSuggestedActionsJson().contains("scale db"),
                "suggested actions must be persisted as JSON");
    }

    @Test
    void lowConfidenceVerdictEscalatesToHuman() {
        CaseEntity c = caseWithStatus(CaseStatus.INVESTIGATING);
        VerdictReport verdict = new VerdictReport(
                Verdict.INCONCLUSIVE, 0.3, null, "cannot determine",
                "evidence", List.of(), true, "confidence too low", "raw");
        caseService.complete(c, verdict, "# report");
        assertEquals(CaseStatus.ESCALATED, c.getStatus());
        assertEquals(true, c.isNeedsHuman());
    }

    @Test
    void likelyFalsePositiveCompletesAsFalsePositive() {
        CaseEntity c = caseWithStatus(CaseStatus.INVESTIGATING);
        VerdictReport verdict = new VerdictReport(
                Verdict.LIKELY_FALSE_POSITIVE, 0.8, null, "metrics show idle CPU",
                "evidence", List.of(), false, "", "raw");
        caseService.complete(c, verdict, "# report");
        assertEquals(CaseStatus.FALSE_POSITIVE, c.getStatus());
    }

    /** Phase 2: CONFIRMED below the confidence gate must escalate, not auto-resolve. */
    @Test
    void confirmedVerdictBelowConfidenceGateEscalates() {
        CaseEntity c = caseWithStatus(CaseStatus.INVESTIGATING);
        VerdictReport verdict = new VerdictReport(
                Verdict.CONFIRMED, 0.3, "maybe db issue", "unclear",
                "weak evidence", List.of(), false, "", "raw");
        caseService.complete(c, verdict, "# report");
        assertEquals(CaseStatus.ESCALATED, c.getStatus());
    }

    /** Phase 2: likely-FP below the confidence gate also escalates (never auto-close on weak evidence). */
    @Test
    void likelyFalsePositiveBelowConfidenceGateEscalates() {
        CaseEntity c = caseWithStatus(CaseStatus.INVESTIGATING);
        VerdictReport verdict = new VerdictReport(
                Verdict.LIKELY_FALSE_POSITIVE, 0.2, null, "maybe FP",
                "weak evidence", List.of(), false, "", "raw");
        caseService.complete(c, verdict, "# report");
        assertEquals(CaseStatus.ESCALATED, c.getStatus());
    }

    /** Phase 2: exact gate boundary — confidence == minConfidence is accepted. */
    @Test
    void confirmedVerdictAtGateBoundaryIsReported() {
        CaseEntity c = caseWithStatus(CaseStatus.INVESTIGATING);
        VerdictReport verdict = new VerdictReport(
                Verdict.CONFIRMED, 0.6, "db pool exhausted", "summary",
                "evidence", List.of("scale db"), false, "", "raw");
        caseService.complete(c, verdict, "# report");
        assertEquals(CaseStatus.REPORTED, c.getStatus());
    }
}

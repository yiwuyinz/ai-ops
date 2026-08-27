package com.aops.agent.service;

import com.aops.agent.config.AopsProperties;
import com.aops.agent.domain.AlertEvent;
import com.aops.agent.domain.CaseEntity;
import com.aops.agent.domain.CaseStatus;
import com.aops.agent.domain.EvidenceEntity;
import com.aops.agent.domain.Verdict;
import com.aops.agent.domain.VerdictReport;
import com.aops.agent.repository.CaseRepository;
import com.aops.agent.repository.EvidenceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Case lifecycle: creation, state-machine transitions, evidence persistence,
 * completion with a verdict.
 */
@Service
public class CaseService {

    private static final Logger log = LoggerFactory.getLogger(CaseService.class);

    private static final Map<CaseStatus, Set<CaseStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(CaseStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(CaseStatus.NEW, EnumSet.of(CaseStatus.INVESTIGATING, CaseStatus.ERROR));
        ALLOWED_TRANSITIONS.put(CaseStatus.INVESTIGATING, EnumSet.of(
                CaseStatus.REPORTED, CaseStatus.ESCALATED, CaseStatus.FALSE_POSITIVE, CaseStatus.ERROR));
        ALLOWED_TRANSITIONS.put(CaseStatus.REPORTED, EnumSet.of(
                CaseStatus.CLOSED, CaseStatus.FALSE_POSITIVE, CaseStatus.ESCALATED, CaseStatus.INVESTIGATING));
        ALLOWED_TRANSITIONS.put(CaseStatus.ESCALATED, EnumSet.of(
                CaseStatus.CLOSED, CaseStatus.FALSE_POSITIVE, CaseStatus.INVESTIGATING));
        ALLOWED_TRANSITIONS.put(CaseStatus.FALSE_POSITIVE, EnumSet.of(
                CaseStatus.CLOSED, CaseStatus.INVESTIGATING));
        ALLOWED_TRANSITIONS.put(CaseStatus.ERROR, EnumSet.of(CaseStatus.INVESTIGATING));
        ALLOWED_TRANSITIONS.put(CaseStatus.CLOSED, EnumSet.noneOf(CaseStatus.class));
    }

    private final CaseRepository caseRepository;
    private final EvidenceRepository evidenceRepository;
    private final ObjectMapper mapper;
    private final AopsProperties props;

    public CaseService(CaseRepository caseRepository, EvidenceRepository evidenceRepository,
                       ObjectMapper mapper, AopsProperties props) {
        this.caseRepository = caseRepository;
        this.evidenceRepository = evidenceRepository;
        this.mapper = mapper;
        this.props = props;
    }

    @Transactional
    public CaseEntity createCase(AlertEvent alert) {
        CaseEntity entity = CaseEntity.builder()
                .id(UUID.randomUUID().toString())
                .alertFingerprint(alert.dedupKey())
                .alertName(alert.alertName())
                .alertStatus(alert.status())
                .labelsJson(toJson(alert.labels()))
                .annotationsJson(toJson(alert.annotations()))
                .alertStartedAt(alert.startsAt())
                .alertEndedAt(alert.endsAt())
                .serviceName(alert.serviceName())
                .status(CaseStatus.NEW)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return caseRepository.save(entity);
    }

    public Optional<CaseEntity> findById(String id) {
        return caseRepository.findById(id);
    }

    public Optional<CaseEntity> findByFingerprint(String fingerprint) {
        return caseRepository.findByAlertFingerprint(fingerprint);
    }

    public List<CaseEntity> recent() {
        return caseRepository.findTop50ByOrderByCreatedAtDesc();
    }

    @Transactional
    public void transition(CaseEntity entity, CaseStatus from, CaseStatus to) {
        if (entity.getStatus() != from) {
            throw new IllegalStateException(
                    "Illegal transition for case " + entity.getId() + ": expected " + from
                            + " but status is " + entity.getStatus());
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("Illegal transition " + from + " -> " + to + " for case " + entity.getId());
        }
        entity.setStatus(to);
        entity.setUpdatedAt(Instant.now());
        caseRepository.save(entity);
        log.info("Case {} transitioned {} -> {}", entity.getId(), from, to);
    }

    /** Transition without checking the current state (used after load). */
    @Transactional
    public void transitionUnchecked(CaseEntity entity, CaseStatus to) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(entity.getStatus(), Set.of()).contains(to)) {
            throw new IllegalStateException("Illegal transition " + entity.getStatus() + " -> " + to + " for case " + entity.getId());
        }
        entity.setStatus(to);
        entity.setUpdatedAt(Instant.now());
        caseRepository.save(entity);
        log.info("Case {} transitioned {} -> {}", entity.getId(), entity.getStatus(), to);
    }

    @Transactional
    public void saveEvidence(List<EvidenceEntity> evidence) {
        evidenceRepository.saveAll(evidence);
    }

    public List<EvidenceEntity> evidenceFor(String caseId) {
        return evidenceRepository.findByCaseIdOrderByStepAsc(caseId);
    }

    /**
     * Pure mapping from verdict to final case status (shared by {@link #complete}
     * and the report builder so the report shows the real final status).
     *
     * <p>Deterministic confidence gate (Phase 2): CONFIRMED or likely-FP verdicts
     * below {@code aops.agent.min-confidence} are escalated to a human instead of
     * being auto-closed — an LLM saying "confirmed" at 0.3 confidence must not
     * auto-resolve a case.</p>
     */
    public CaseStatus finalStatusFor(VerdictReport verdict) {
        if (verdict == null) {
            return CaseStatus.ERROR;
        }
        if (verdict.needsHuman() || verdict.verdict() == Verdict.INCONCLUSIVE) {
            return CaseStatus.ESCALATED;
        }
        double minConfidence = props.agent().minConfidence();
        if (verdict.confidence() < minConfidence) {
            log.info("Case verdict {} at confidence {} below gate {} — escalating to human",
                    verdict.verdict(), verdict.confidence(), minConfidence);
            return CaseStatus.ESCALATED;
        }
        return verdict.verdict() == Verdict.LIKELY_FALSE_POSITIVE
                ? CaseStatus.FALSE_POSITIVE
                : CaseStatus.REPORTED;
    }

    @Transactional
    public void complete(CaseEntity entity, VerdictReport verdict, String reportMarkdown) {
        if (verdict != null) {
            entity.setVerdict(verdict.verdict());
            entity.setConfidence(verdict.confidence());
            entity.setRootCause(verdict.rootCause());
            entity.setSummary(verdict.summary());
            entity.setNeedsHuman(verdict.needsHuman());
            entity.setSuggestedActionsJson(toJson(verdict.suggestedActions()));
        }
        entity.setReportMarkdown(reportMarkdown);
        entity.setInvestigatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        CaseStatus finalStatus = finalStatusFor(verdict);
        transitionUnchecked(entity, finalStatus);
        log.info("Case {} completed with verdict={} confidence={} status={}",
                entity.getId(), entity.getVerdict(), entity.getConfidence(), finalStatus);
    }

    @Transactional
    public void markError(CaseEntity entity, String message) {
        entity.setErrorMessage(message);
        entity.setNeedsHuman(true);
        entity.setUpdatedAt(Instant.now());
        transitionUnchecked(entity, CaseStatus.ERROR);
    }

    @Transactional
    public void save(CaseEntity entity) {
        entity.setUpdatedAt(Instant.now());
        caseRepository.save(entity);
    }

    private String toJson(Map<String, String> map) {
        if (map == null) {
            return "{}";
        }
        try {
            return mapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String toJson(List<String> list) {
        if (list == null) {
            return "[]";
        }
        try {
            return mapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}

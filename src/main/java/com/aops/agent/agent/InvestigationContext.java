package com.aops.agent.agent;

import com.aops.agent.domain.AlertEvent;
import com.aops.agent.domain.EvidenceEntity;
import com.aops.agent.service.TopologyService;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Per-investigation state: the case being investigated, the original alert and
 * the evidence chain collected so far. Bound to the investigating thread via
 * {@link InvestigationContextHolder}.
 */
@Getter
public class InvestigationContext {

    private final String caseId;
    private final AlertEvent alert;
    private final TopologyService.ServiceAsset serviceAsset;
    private final Instant investigationStart;
    private final List<EvidenceEntity> evidence = new ArrayList<>();
    private int step = 0;

    public InvestigationContext(String caseId, AlertEvent alert, TopologyService.ServiceAsset serviceAsset) {
        this.caseId = caseId;
        this.alert = alert;
        this.serviceAsset = serviceAsset;
        this.investigationStart = Instant.now();
    }

    /** Record one tool call into the evidence chain (in-memory; flushed at the end). */
    public synchronized void recordEvidence(String toolName, String parameters, String resultExcerpt) {
        String excerpt = resultExcerpt == null ? "" : resultExcerpt;
        if (excerpt.length() > 2000) {
            excerpt = excerpt.substring(0, 2000) + "... [truncated]";
        }
        evidence.add(EvidenceEntity.builder()
                .id(UUID.randomUUID().toString())
                .caseId(caseId)
                .step(++step)
                .toolName(toolName)
                .parameters(parameters)
                .resultExcerpt(excerpt)
                .createdAt(Instant.now())
                .build());
    }
}

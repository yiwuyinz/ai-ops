package com.aops.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One tool call made during an investigation — the evidence chain.
 */
@Entity
@Table(name = "aops_evidence", indexes = @Index(name = "idx_evidence_case", columnList = "caseId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvidenceEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String caseId;

    /** Sequence number within the investigation. */
    private int step;

    @Column(nullable = false)
    private String toolName;

    @Column(columnDefinition = "TEXT")
    private String parameters;

    /** Truncated tool output for audit / human handoff. */
    @Column(columnDefinition = "TEXT")
    private String resultExcerpt;

    @Column(nullable = false)
    private Instant createdAt;
}

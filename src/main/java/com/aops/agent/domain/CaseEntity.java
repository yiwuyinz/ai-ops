package com.aops.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An investigation case: everything known about one alert investigation.
 */
@Entity
@Table(name = "aops_case")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseEntity {

    @Id
    private String id;

    /** Stable dedup key: alertname + fingerprint + instance. */
    @Column(nullable = false, unique = true)
    private String alertFingerprint;

    @Column(nullable = false)
    private String alertName;

    private String alertStatus;

    @Column(columnDefinition = "TEXT")
    private String labelsJson;

    @Column(columnDefinition = "TEXT")
    private String annotationsJson;

    /** Alert start/end times — the investigation time window. */
    private Instant alertStartedAt;

    private Instant alertEndedAt;

    /** Derived service name from alert labels (service / job / namespace). */
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status;

    @Enumerated(EnumType.STRING)
    private Verdict verdict;

    private Double confidence;

    @Column(columnDefinition = "TEXT")
    private String rootCause;

    @Column(columnDefinition = "TEXT")
    private String summary;

    /** Agent's suggested remediation actions (JSON array of strings). */
    @Column(columnDefinition = "TEXT")
    private String suggestedActionsJson;

    @Column(columnDefinition = "TEXT")
    private String reportMarkdown;

    private boolean needsHuman;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant investigatedAt;
}

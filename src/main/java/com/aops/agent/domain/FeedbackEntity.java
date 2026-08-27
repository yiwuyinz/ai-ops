package com.aops.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Human feedback on a case (the flywheel that drives false-positive tuning).
 */
@Entity
@Table(name = "aops_feedback", indexes = @Index(name = "idx_feedback_case", columnList = "caseId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeedbackOutcome outcome;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(nullable = false)
    private Instant createdAt;
}

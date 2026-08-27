package com.aops.agent.domain;

/**
 * Lifecycle of an investigation case.
 * NEW -> INVESTIGATING -> REPORTED | ESCALATED | FALSE_POSITIVE | ERROR
 * Feedback afterwards: REPORTED -> CLOSED / FALSE_POSITIVE / CONFIRMED_AND_CLOSED
 */
public enum CaseStatus {
    NEW,
    INVESTIGATING,
    REPORTED,
    ESCALATED,
    CLOSED,
    FALSE_POSITIVE,
    ERROR
}

package com.aops.agent.notifier;

import com.aops.agent.domain.CaseReport;

/**
 * Where completed investigation reports go (Slack / console / future channels).
 * Channel-agnostic so the HITL surface can be swapped without touching the core.
 */
public interface Notifier {

    void send(CaseReport report);
}

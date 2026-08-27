package com.aops.agent.notifier;

import com.aops.agent.domain.CaseReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Default notifier: structured log lines (and the full markdown report).
 */
@Component
public class ConsoleNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(ConsoleNotifier.class);

    @Override
    public void send(CaseReport report) {
        log.info("""
                ===== Investigation report =====
                case: {}  alert: {}  service: {}
                status: {}  verdict: {}  confidence: {}%
                needsHuman: {} {}
                rootCause: {}
                summary: {}
                suggestedActions: {}
                evidence: {} tool call(s)
                ---------------------------------
                {}
                =================================
                """,
                report.caseId(),
                report.alertName(),
                report.serviceName(),
                report.status(),
                report.verdict(),
                report.confidence() == null ? "n/a" : String.format(Locale.ROOT, "%.0f", report.confidence() * 100),
                report.needsHuman(),
                report.needsHuman() && report.reasonForEscalation() != null ? "(" + report.reasonForEscalation() + ")" : "",
                nvl(report.rootCause()),
                nvl(report.summary()),
                report.suggestedActions() == null || report.suggestedActions().isEmpty()
                        ? "(none)" : report.suggestedActions().toString(),
                report.evidenceCount(),
                nvl(report.reportMarkdown()));
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}

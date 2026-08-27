package com.aops.agent.notifier;

import com.aops.agent.domain.CaseReport;
import com.aops.agent.config.AopsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Slack notifier via incoming webhook (aops.notifier.type=slack).
 * Posts a compact summary; the full report is available via the case API.
 */
@Component
@Primary
@ConditionalOnProperty(name = "aops.notifier.type", havingValue = "slack")
public class SlackNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);
    private static final int MAX_TEXT = 3900;

    private final RestClient restClient;

    public SlackNotifier(AopsProperties props) {
        this.restClient = RestClient.builder().baseUrl(props.notifier().slackWebhookUrl()).build();
    }

    @Override
    public void send(CaseReport report) {
        String confidence = report.confidence() == null
                ? "n/a"
                : String.format(Locale.ROOT, "%.0f%%", report.confidence() * 100);
        String actions = report.suggestedActions() == null || report.suggestedActions().isEmpty()
                ? ""
                : "*Suggested actions:*\n" + report.suggestedActions().stream()
                        .map(a -> "• " + a)
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");
        String text = """
                *Investigation report* — %s (%s)
                Status: %s | Verdict: *%s* | Confidence: %s
                Root cause: %s
                Summary: %s
                %s
                %s
                Evidence: %d tool call(s) | case id: `%s`
                """
                .formatted(
                        report.alertName(),
                        nvl(report.serviceName()),
                        report.status(),
                        report.verdict(),
                        confidence,
                        nvl(report.rootCause()),
                        nvl(report.summary()),
                        report.needsHuman() ? "⚠️ *Needs human attention*: " + nvl(report.reasonForEscalation()) : "",
                        actions,
                        report.evidenceCount(),
                        report.caseId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text);
        try {
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Slack notification sent for case {}", report.caseId());
        } catch (Exception e) {
            log.error("Failed to send Slack notification for case {}: {}", report.caseId(), e.getMessage());
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}

package com.aops.agent.web;

import com.aops.agent.domain.AlertEvent;
import com.aops.agent.service.AlertIntakeService;
import com.aops.agent.web.dto.AlertManagerWebhookPayload;
import com.aops.agent.web.dto.SimpleAlertRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Alert intake endpoints. /api/alerts accepts the standard AlertManager webhook
 * payload; /api/alerts/simple accepts one alert as JSON (for manual testing and
 * non-AlertManager sources).
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertWebhookController {

    private static final Logger log = LoggerFactory.getLogger(AlertWebhookController.class);

    private final AlertIntakeService intakeService;

    public AlertWebhookController(AlertIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @PostMapping
    public Map<String, Object> alertManagerWebhook(@RequestBody AlertManagerWebhookPayload payload) {
        int accepted = 0;
        int duplicates = 0;
        if (payload.alerts() != null) {
            for (AlertManagerWebhookPayload.AlertItem item : payload.alerts()) {
                if ("resolved".equalsIgnoreCase(item.status())) {
                    continue; // MVP: only investigate firing alerts
                }
                Map<String, String> labels = item.labels() == null ? Map.of() : item.labels();
                AlertEvent alert = new AlertEvent(
                        item.fingerprint(),
                        item.status(),
                        labels.getOrDefault("alertname", "UnknownAlert"),
                        labels,
                        item.annotations() == null ? Map.of() : item.annotations(),
                        parseInstant(item.startsAt()),
                        parseInstant(item.endsAt()),
                        item.generatorURL(),
                        AlertEvent.deriveService(labels));
                if (intakeService.ingest(alert) == null) {
                    duplicates++;
                } else {
                    accepted++;
                }
            }
        }
        log.info("AlertManager webhook: accepted={} duplicates={}", accepted, duplicates);
        return response(accepted, duplicates);
    }

    @PostMapping("/simple")
    public Map<String, Object> simpleAlert(@RequestBody SimpleAlertRequest request) {
        Map<String, String> labels = request.labels() == null ? Map.of() : request.labels();
        Map<String, String> effectiveLabels = new HashMap<>(labels);
        effectiveLabels.putIfAbsent("alertname", request.alertName() == null ? "UnknownAlert" : request.alertName());

        AlertEvent alert = new AlertEvent(
                request.fingerprint(),
                request.status() == null ? "firing" : request.status(),
                effectiveLabels.get("alertname"),
                effectiveLabels,
                request.annotations() == null ? Map.of() : request.annotations(),
                parseInstant(request.startsAt()),
                null,
                null,
                AlertEvent.deriveService(effectiveLabels));

        int accepted = intakeService.ingest(alert) == null ? 0 : 1;
        int duplicates = accepted == 1 ? 0 : 1;
        log.info("Simple alert webhook: accepted={} duplicates={}", accepted, duplicates);
        return response(accepted, duplicates);
    }

    private static Map<String, Object> response(int accepted, int duplicates) {
        Map<String, Object> body = new HashMap<>();
        body.put("accepted", accepted);
        body.put("duplicates", duplicates);
        return body;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

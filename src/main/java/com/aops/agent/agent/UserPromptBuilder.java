package com.aops.agent.agent;

import com.aops.agent.domain.AlertEvent;
import com.aops.agent.service.DeployWindowService;
import com.aops.agent.service.RunbookService;
import com.aops.agent.service.TopologyService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Builds the user prompt: the alert itself + deterministic context injection
 * (topology, runbooks, deploy windows). This is the "which logs to query"
 * narrowing: the agent receives the candidate sources instead of guessing.
 */
@Component
public class UserPromptBuilder {

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private final TopologyService topologyService;
    private final RunbookService runbookService;
    private final DeployWindowService deployWindowService;

    public UserPromptBuilder(TopologyService topologyService,
                             RunbookService runbookService,
                             DeployWindowService deployWindowService) {
        this.topologyService = topologyService;
        this.runbookService = runbookService;
        this.deployWindowService = deployWindowService;
    }

    public String build(AlertEvent alert) {
        Instant now = Instant.now();
        Instant start = alert.startsAt() != null ? alert.startsAt() : now.minus(Duration.ofMinutes(30));
        Instant end = alert.endsAt() != null ? alert.endsAt() : now;

        Optional<TopologyService.ServiceAsset> asset = topologyService.findByAlert(alert);

        StringBuilder sb = new StringBuilder();
        sb.append("# Alert\n");
        sb.append("- name: ").append(alert.alertName()).append("\n");
        sb.append("- status: ").append(alert.status()).append("\n");
        sb.append("- service: ").append(alert.serviceName()).append("\n");
        sb.append("- labels: ").append(alert.labels()).append("\n");
        sb.append("- annotations: ").append(alert.annotations()).append("\n");
        sb.append("- startedAt: ").append(TS.format(start)).append("\n");
        sb.append("- endsAt: ").append(alert.endsAt() == null ? "(still firing)" : TS.format(end)).append("\n");
        sb.append("- recommended investigation window: ").append(TS.format(start))
                .append(" to ").append(TS.format(end)).append("\n\n");

        if (asset.isPresent()) {
            TopologyService.ServiceAsset a = asset.get();
            sb.append("# Topology context for ").append(a.name()).append("\n");
            sb.append("- namespace: ").append(nvl(a.namespace())).append("\n");
            sb.append("- logSelector: ").append(nvl(a.logSelector()))
                    .append(" (use this or a refinement of it as the base of your LogQL queries)\n");
            sb.append("- logQueryHints: ").append(nvl(a.logQueryHints())).append("\n");
            sb.append("- metricNamespaces: ").append(nvl(a.metricNamespaces())).append("\n");
            sb.append("- runbookRefs: ").append(nvl(a.runbookRefs()))
                    .append(" (fetch with fetch_runbook)\n");
            sb.append("- owner: ").append(nvl(a.owner())).append("\n");
            sb.append("- dependsOn: ").append(nvl(a.dependsOn())).append("\n");
            sb.append("- notes: ").append(nvl(a.notes())).append("\n\n");
        } else {
            sb.append("# Topology context\n- WARNING: service '").append(alert.serviceName())
                    .append("' is NOT in the asset topology. Use generic tools and clearly flag this to a human.\n\n");
        }

        sb.append("# Deploy windows\n")
                .append(deployWindowService.recentDeployments(alert.serviceName(), Duration.ofHours(6)))
                .append("\n\n");

        var runbooks = runbookService.findByAlert(alert.alertName());
        if (!runbooks.isEmpty()) {
            sb.append("# Runbooks matching this alert\n");
            for (RunbookService.Runbook r : runbooks) {
                sb.append("- ").append(r.name()).append(": ").append(r.title()).append("\n");
            }
            sb.append("Fetch the most relevant runbook with fetch_runbook and follow its steps.\n\n");
        }

        sb.append("Investigate this alert now. Use the tools to gather evidence, then produce "
                + "your analysis and the required JSON verdict.");
        return sb.toString();
    }

    private static String nvl(Object o) {
        return o == null ? "" : o.toString();
    }
}

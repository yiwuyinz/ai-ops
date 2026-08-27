package com.aops.agent.service;

import com.aops.agent.config.AopsProperties;
import com.aops.agent.domain.AlertEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Asset topology: the deterministic map from service names to their log sources,
 * metric namespaces, runbooks and owners. This is what turns "which logs do I
 * query?" from a search problem into a lookup problem for the agent.
 */
@Service
public class TopologyService {

    private static final Logger log = LoggerFactory.getLogger(TopologyService.class);

    public record ServiceAsset(
            String name,
            String namespace,
            String logSelector,
            List<String> logQueryHints,
            List<String> metricNamespaces,
            List<String> runbookRefs,
            String owner,
            List<String> dependsOn,
            String notes
    ) {
    }

    private record TopologyFile(List<ServiceAsset> services) {
    }

    private final Map<String, ServiceAsset> byName = new HashMap<>();

    public TopologyService(AopsProperties props, ObjectMapper mapper) {
        load(props.topology().file(), mapper);
    }

    @PostConstruct
    void logLoaded() {
        log.info("Topology loaded: {} service assets", byName.size());
    }

    private void load(String filePath, ObjectMapper mapper) {
        try {
            Path path = Path.of(filePath).toAbsolutePath();
            if (!Files.exists(path)) {
                log.warn("Topology file not found: {} (agent will rely on tool lookups only)", path);
                return;
            }
            TopologyFile file = mapper.readValue(Files.readAllBytes(path), TopologyFile.class);
            if (file.services() != null) {
                for (ServiceAsset s : file.services()) {
                    byName.put(s.name(), s);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load topology file {}: {}", filePath, e.getMessage());
        }
    }

    public Optional<ServiceAsset> findByService(String service) {
        if (service == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(service));
    }

    public Optional<ServiceAsset> findByAlert(AlertEvent alert) {
        return findByService(alert.serviceName());
    }

    public List<ServiceAsset> all() {
        return List.copyOf(byName.values());
    }
}

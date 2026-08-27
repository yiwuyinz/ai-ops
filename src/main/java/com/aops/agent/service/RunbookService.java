package com.aops.agent.service;

import com.aops.agent.config.AopsProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Runbook catalog: markdown files with YAML frontmatter, loaded from a directory.
 * The agent fetches these via the fetch_runbook tool and follows the steps.
 */
@Service
public class RunbookService {

    private static final Logger log = LoggerFactory.getLogger(RunbookService.class);

    public record Runbook(String name, String title, List<String> applicableAlerts, String content) {
    }

    private final Map<String, Runbook> byName = new LinkedHashMap<>();
    private final Yaml yaml = new Yaml();

    public RunbookService(AopsProperties props) {
        load(props.runbook().directory());
    }

    @PostConstruct
    void logLoaded() {
        log.info("Runbook catalog loaded: {} runbooks", byName.size());
    }

    private void load(String directory) {
        Path dir = Path.of(directory).toAbsolutePath();
        if (!Files.isDirectory(dir)) {
            log.warn("Runbook directory not found: {} (empty catalog)", dir);
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".md"))
                    .forEach(this::loadRunbook);
        } catch (IOException e) {
            log.warn("Failed to scan runbook directory {}: {}", dir, e.getMessage());
        }
    }

    private void loadRunbook(Path path) {
        try {
            String text = Files.readString(path);
            String frontmatter = null;
            String content = text;
            if (text.startsWith("---")) {
                int end = text.indexOf("\n---", 3);
                if (end > 0) {
                    frontmatter = text.substring(3, end);
                    content = text.substring(end + 4).strip();
                }
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            if (frontmatter != null) {
                Object parsed = yaml.load(frontmatter);
                if (parsed instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        meta.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
            }
            String fileName = path.getFileName().toString().replace(".md", "");
            String name = String.valueOf(meta.getOrDefault("name", fileName));
            String title = String.valueOf(meta.getOrDefault("title", name));
            @SuppressWarnings("unchecked")
            List<String> applicable = (List<String>) meta.getOrDefault("applicableAlerts", List.of());
            byName.put(name, new Runbook(name, title, applicable == null ? List.of() : applicable, content));
        } catch (Exception e) {
            log.warn("Failed to parse runbook {}: {}", path, e.getMessage());
        }
    }

    public Optional<Runbook> findByName(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<Runbook> findByAlert(String alertName) {
        List<Runbook> matches = new ArrayList<>();
        for (Runbook r : byName.values()) {
            if (r.applicableAlerts().contains(alertName)) {
                matches.add(r);
            }
        }
        return matches;
    }

    public List<Runbook> all() {
        return List.copyOf(byName.values());
    }
}

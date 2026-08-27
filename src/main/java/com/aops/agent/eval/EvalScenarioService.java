package com.aops.agent.eval;

import com.aops.agent.config.AopsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Loads evaluation scenarios from a directory of JSON files
 * ({@code aops.eval.scenarios-dir}, default {@code evals/scenarios}).
 */
@Service
public class EvalScenarioService {

    private static final Logger log = LoggerFactory.getLogger(EvalScenarioService.class);

    private final Map<String, EvalScenario> byId = new LinkedHashMap<>();

    public EvalScenarioService(AopsProperties props, ObjectMapper mapper) {
        load(props.eval().scenariosDir(), mapper);
    }

    @PostConstruct
    void logLoaded() {
        log.info("Eval scenarios loaded: {}", byId.size());
    }

    private void load(String directory, ObjectMapper mapper) {
        Path dir = Path.of(directory).toAbsolutePath();
        if (!Files.isDirectory(dir)) {
            log.warn("Eval scenarios directory not found: {} (empty scenario catalog)", dir);
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            EvalScenario scenario = mapper.readValue(Files.readAllBytes(p), EvalScenario.class);
                            if (scenario.id() == null || scenario.id().isBlank()) {
                                log.warn("Eval scenario {} has no id — skipping", p.getFileName());
                                return;
                            }
                            byId.put(scenario.id(), scenario);
                        } catch (IOException e) {
                            log.warn("Failed to parse eval scenario {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to scan eval scenarios directory {}: {}", dir, e.getMessage());
        }
    }

    public List<EvalScenario> all() {
        return List.copyOf(byId.values());
    }

    public Optional<EvalScenario> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}

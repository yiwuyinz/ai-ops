package com.aops.agent.service;

import com.aops.agent.config.AopsProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Alert dedup gateway: picks the backend by configuration.
 */
@Service
public class DedupService {

    private final Deduplicator deduplicator;
    private final int windowMinutes;

    public DedupService(List<Deduplicator> deduplicators, AopsProperties props) {
        String mode = props.dedup().mode();
        this.deduplicator = deduplicators.stream()
                .filter(d -> backendMatches(d, mode))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No deduplicator for mode: " + mode));
        this.windowMinutes = props.dedup().windowMinutes();
    }

    private boolean backendMatches(Deduplicator d, String mode) {
        return ("redis".equals(mode) && d instanceof RedisDeduplicator)
                || (!"redis".equals(mode) && d instanceof InMemoryDeduplicator);
    }

    /**
     * @return true if this alert key was already seen within the dedup window.
     */
    public boolean isDuplicate(String key) {
        return !deduplicator.markSeen(key, Duration.ofMinutes(windowMinutes));
    }

    public int windowMinutes() {
        return windowMinutes;
    }
}

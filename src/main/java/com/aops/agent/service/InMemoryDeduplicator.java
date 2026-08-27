package com.aops.agent.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory dedup (default; works without Redis). TTL is enforced lazily.
 */
@Component
@ConditionalOnProperty(name = "aops.dedup.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryDeduplicator implements Deduplicator {

    private final Map<String, Long> expiryByKey = new ConcurrentHashMap<>();

    @Override
    public boolean markSeen(String key, Duration ttl) {
        long now = System.currentTimeMillis();
        expiryByKey.entrySet().removeIf(e -> e.getValue() <= now);
        long expiry = now + ttl.toMillis();
        Long previous = expiryByKey.putIfAbsent(key, expiry);
        return previous == null;
    }
}

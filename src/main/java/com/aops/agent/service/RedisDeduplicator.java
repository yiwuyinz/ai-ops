package com.aops.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed dedup (aops.dedup.mode=redis). Falls back to in-memory if Redis
 * is unreachable so alert intake never fails on cache outage.
 */
@Component
@ConditionalOnProperty(name = "aops.dedup.mode", havingValue = "redis")
public class RedisDeduplicator implements Deduplicator {

    private static final Logger log = LoggerFactory.getLogger(RedisDeduplicator.class);
    private static final String PREFIX = "aops:dedup:";

    private final StringRedisTemplate redis;
    private final InMemoryDeduplicator fallback = new InMemoryDeduplicator();

    public RedisDeduplicator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean markSeen(String key, Duration ttl) {
        try {
            Boolean first = redis.opsForValue().setIfAbsent(PREFIX + key, "1", ttl);
            return Boolean.TRUE.equals(first);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, falling back to in-memory dedup: {}", e.getMessage());
            return fallback.markSeen(key, ttl);
        }
    }
}

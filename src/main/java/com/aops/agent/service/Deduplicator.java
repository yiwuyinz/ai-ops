package com.aops.agent.service;

import java.time.Duration;

/**
 * Dedup backend: mark an alert key as seen; returns false if already seen within TTL.
 */
public interface Deduplicator {

    /** @return true if this key was newly registered (first sighting). */
    boolean markSeen(String key, Duration ttl);
}

package com.aops.agent.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryDeduplicatorTest {

    private final InMemoryDeduplicator dedup = new InMemoryDeduplicator();

    @Test
    void firstSightingIsNewSecondIsDuplicate() {
        assertTrue(dedup.markSeen("alert-1", Duration.ofMinutes(60)));
        assertFalse(dedup.markSeen("alert-1", Duration.ofMinutes(60)));
        assertTrue(dedup.markSeen("alert-2", Duration.ofMinutes(60)));
    }

    @Test
    void expiredKeysAreReSeen() throws InterruptedException {
        assertTrue(dedup.markSeen("alert-x", Duration.ofMillis(50)));
        Thread.sleep(80);
        assertTrue(dedup.markSeen("alert-x", Duration.ofMillis(50)));
    }
}

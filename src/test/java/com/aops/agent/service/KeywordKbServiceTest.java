package com.aops.agent.service;

import com.aops.agent.TestProps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordKbServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void findsDocumentsByKeyword() throws Exception {
        Files.writeString(tempDir.resolve("redis-notes.md"), """
                # Redis connection issues
                When the payment service cannot reach Redis, check for connection pool
                exhaustion and network policy blocks.
                """);
        Files.writeString(tempDir.resolve("deploy-notes.md"), """
                # Deploy best practices
                Always verify pods are ready before cutting traffic.
                """);

        KeywordKbService kb = new KeywordKbService(TestProps.withKbDir(tempDir.toString()));

        assertEquals(2, kb.documentCount());
        List<KbService.KbResult> results = kb.search("redis connection", 5);
        assertTrue(!results.isEmpty(), "expected at least one match");
        assertEquals("redis-notes", results.get(0).title());
    }
}

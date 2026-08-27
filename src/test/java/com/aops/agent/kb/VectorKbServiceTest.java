package com.aops.agent.kb;

import com.aops.agent.TestProps;
import com.aops.agent.service.KeywordKbService;
import com.aops.agent.service.RunbookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hybrid retrieval tests: semantic-only matches (cross-lingual query) must be
 * found via vectors even when the keyword layer finds nothing.
 */
class VectorKbServiceTest {

    @TempDir
    Path kbDir;

    @TempDir
    Path runbookDir;

    private VectorKbService newService() throws Exception {
        Files.writeString(kbDir.resolve("timeout-notes.md"), """
                # Timeout handling
                When the database times out, check the connection pool and increase it.
                """);
        Files.writeString(kbDir.resolve("deploy-notes.md"), """
                # Deploy best practices
                Always verify pods are ready before cutting traffic.
                """);

        KeywordKbService keyword = new KeywordKbService(TestProps.withKbDir(kbDir.toString()));
        RunbookService runbooks = new RunbookService(TestProps.withRunbookDir(runbookDir.toString()));
        VectorKbService kb = new VectorKbService(keyword, runbooks,
                TestProps.withHybridKb(kbDir.toString(), runbookDir.toString(), FakeEmbeddingProvider.DIM, 500),
                new FakeEmbeddingProvider(), new InMemoryVectorStore());
        kb.reindex();
        return kb;
    }

    @Test
    void semanticOnlyQueryIsFoundViaVectors() throws Exception {
        VectorKbService kb = newService();

        // Chinese query "超时" matches the English "timeout" doc semantically,
        // while the keyword layer alone finds nothing.
        var keywordOnly = new KeywordKbService(TestProps.withKbDir(kbDir.toString())).search("超时", 5);
        assertTrue(keywordOnly.isEmpty(), "keyword layer must not match cross-lingual query");

        List<com.aops.agent.service.KbService.KbResult> results = kb.search("超时", 5);
        assertFalse(results.isEmpty(), "hybrid search must find the semantic match");
        assertEquals("timeout-notes", results.get(0).title());
    }

    @Test
    void keywordQueryStillWorksAndRankedFirst() throws Exception {
        VectorKbService kb = newService();

        List<com.aops.agent.service.KbService.KbResult> results = kb.search("database", 5);
        assertFalse(results.isEmpty());
        assertEquals("timeout-notes", results.get(0).title());
    }

    @Test
    void runbooksAreIndexedToo() throws Exception {
        Files.writeString(runbookDir.resolve("db-recovery.md"), """
                ---
                name: db-recovery
                title: DB recovery
                ---
                Restart the database and verify the connection.
                """);
        VectorKbService kb = newService();

        List<com.aops.agent.service.KbService.KbResult> results = kb.search("连接", 5);
        assertFalse(results.isEmpty(), "runbook content must be searchable semantically");
        assertTrue(results.stream().anyMatch(r -> r.title().equals("db-recovery")),
                "expected the runbook chunk to match: " + results);
    }

    @Test
    void modeAndCountsReflectHybridState() throws Exception {
        VectorKbService kb = newService();
        assertEquals("hybrid", kb.mode());
        assertTrue(kb.chunkCount() > 0);
        assertEquals(2, kb.documentCount());
    }

    @Test
    void withoutEmbeddingFallsBackToKeyword() throws Exception {
        Files.writeString(kbDir.resolve("timeout-notes.md"), """
                # Timeout handling
                When the database times out, check the connection pool.
                """);
        KeywordKbService keyword = new KeywordKbService(TestProps.withKbDir(kbDir.toString()));
        RunbookService runbooks = new RunbookService(TestProps.withRunbookDir(runbookDir.toString()));
        // embedding-enabled=false (default props) + no provider/store
        VectorKbService kb = new VectorKbService(keyword, runbooks,
                TestProps.withKbDir(kbDir.toString()), null, null);

        assertEquals("keyword", kb.mode());
        List<com.aops.agent.service.KbService.KbResult> results = kb.search("database", 5);
        assertFalse(results.isEmpty());
        assertEquals("timeout-notes", results.get(0).title());
    }
}

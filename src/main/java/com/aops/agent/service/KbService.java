package com.aops.agent.service;

import java.util.List;

/**
 * Knowledge base search. Two implementations:
 * <ul>
 *   <li>{@link KeywordKbService} — lexical TF scoring (always available);</li>
 *   <li>{@link com.aops.agent.kb.VectorKbService} — hybrid keyword + pgvector
 *       semantic search (RAG mode), falling back to keyword when embedding is
 *       not configured or unavailable.</li>
 * </ul>
 */
public interface KbService {

    record KbResult(String title, String snippet, double score) {
    }

    /** Search the knowledge base, returning the top matches. */
    List<KbResult> search(String query, int limit);

    /** Total documents indexed (for /api/info). */
    int documentCount();

    /** Retrieval mode: "keyword" | "hybrid" | "keyword-fallback". */
    default String mode() {
        return "keyword";
    }

    /** Vector chunks currently indexed (0 when not in RAG mode). */
    default int chunkCount() {
        return 0;
    }
}


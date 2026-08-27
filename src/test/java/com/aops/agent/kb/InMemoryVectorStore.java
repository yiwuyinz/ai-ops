package com.aops.agent.kb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Naive in-memory vector store (cosine similarity in Java) — used by unit tests
 * so the hybrid-search logic is testable without PostgreSQL/pgvector.
 */
public class InMemoryVectorStore implements VectorStore {

    private record Entry(String source, String title, int chunkIndex, String content, float[] embedding) {
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    @Override
    public void upsert(String id, String source, String title, int chunkIndex, String content, float[] embedding) {
        entries.put(id, new Entry(source, title, chunkIndex, content, embedding));
    }

    @Override
    public List<Hit> search(float[] embedding, int limit) {
        return entries.values().stream()
                .map(e -> new Hit(e.source(), e.title(), e.content(), cosine(embedding, e.embedding())))
                .sorted((a, b) -> Double.compare(b.similarity(), a.similarity()))
                .limit(limit)
                .toList();
    }

    @Override
    public void clear() {
        entries.clear();
    }

    @Override
    public int size() {
        return entries.size();
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}

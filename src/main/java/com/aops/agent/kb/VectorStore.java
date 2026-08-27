package com.aops.agent.kb;

import java.util.List;

/**
 * Vector store for RAG chunks. Runtime uses pgvector (PostgreSQL); tests use an
 * in-memory implementation.
 */
public interface VectorStore {

    record Hit(String source, String title, String content, double similarity) {
    }

    void upsert(String id, String source, String title, int chunkIndex, String content, float[] embedding);

    /** Cosine similarity search, most similar first. */
    List<Hit> search(float[] embedding, int limit);

    void clear();

    int size();
}

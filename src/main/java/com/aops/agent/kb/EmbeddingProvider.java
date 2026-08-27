package com.aops.agent.kb;

/**
 * Text embedding provider — the semantic layer of the RAG pipeline.
 * Implementations: OpenAI-compatible HTTP APIs (SiliconFlow, dashscope, ...).
 */
public interface EmbeddingProvider {

    /** Embed a single text into a float vector. */
    float[] embed(String text);
}

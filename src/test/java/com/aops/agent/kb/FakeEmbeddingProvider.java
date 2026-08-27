package com.aops.agent.kb;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic fake embedding for tests: cross-lingual concepts map to the
 * same vector (e.g. "timeout" and "超时"), so semantic-only matches are
 * reproducible without a real embeddings API.
 */
class FakeEmbeddingProvider implements EmbeddingProvider {

    static final int DIM = 8;

    private final Map<String, float[]> concepts = new HashMap<>();

    FakeEmbeddingProvider() {
        concepts.put("timeout", unit(0));
        concepts.put("超时", unit(0));
        concepts.put("refused", unit(1));
        concepts.put("拒绝", unit(1));
        concepts.put("connection", unit(2));
        concepts.put("连接", unit(2));
        concepts.put("deploy", unit(3));
        concepts.put("部署", unit(3));
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[DIM];
        String lower = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, float[]> e : concepts.entrySet()) {
            if (lower.contains(e.getKey())) {
                for (int i = 0; i < DIM; i++) {
                    v[i] += e.getValue()[i];
                }
            }
        }
        normalize(v);
        return v;
    }

    private static float[] unit(int index) {
        float[] v = new float[DIM];
        v[index] = 1.0f;
        return v;
    }

    private static void normalize(float[] v) {
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        if (norm == 0) {
            return;
        }
        double scale = Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) {
            v[i] /= scale;
        }
    }
}

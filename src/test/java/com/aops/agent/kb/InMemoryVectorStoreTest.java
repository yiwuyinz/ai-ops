package com.aops.agent.kb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryVectorStoreTest {

    @Test
    void returnsNearestNeighborsFirst() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert("a", "kb", "doc-a", 0, "content a", new float[]{1, 0, 0, 0});
        store.upsert("b", "kb", "doc-b", 0, "content b", new float[]{0, 1, 0, 0});

        List<VectorStore.Hit> hits = store.search(new float[]{1, 0, 0, 0}, 5);

        assertEquals(2, hits.size());
        assertEquals("doc-a", hits.get(0).title());
        assertTrue(hits.get(0).similarity() > hits.get(1).similarity());
    }

    @Test
    void clearAndSize() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.upsert("a", "kb", "doc-a", 0, "c", new float[]{1, 0, 0, 0});
        assertEquals(1, store.size());
        store.clear();
        assertEquals(0, store.size());
    }
}

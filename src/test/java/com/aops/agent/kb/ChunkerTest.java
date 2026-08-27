package com.aops.agent.kb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkerTest {

    @Test
    void shortContentStaysOneChunk() {
        List<String> chunks = Chunker.chunk("# Title\nshort text", 700, 100);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("Title"));
    }

    @Test
    void longContentIsSplitWithSizeBound() {
        String longText = "# Section A\n\n" + "word ".repeat(400); // ~2000 chars
        List<String> chunks = Chunker.chunk(longText, 300, 50);
        assertTrue(chunks.size() > 1, "expected multiple chunks");
        for (String c : chunks) {
            assertTrue(c.length() <= 300, "chunk too large: " + c.length());
        }
    }

    @Test
    void headingsArePreservedInChunks() {
        String content = """
                # First
                paragraph one

                ## Second
                paragraph two

                ## Third
                paragraph three
                """;
        List<String> chunks = Chunker.chunk(content, 700, 100);
        // headings delimit sections: one chunk per heading
        assertEquals(3, chunks.size());
        assertTrue(chunks.get(0).contains("# First"));
        assertTrue(chunks.get(1).contains("## Second"));
        assertTrue(chunks.get(2).contains("## Third"));
    }
}

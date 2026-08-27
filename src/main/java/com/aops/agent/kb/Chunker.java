package com.aops.agent.kb;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown-aware chunking: split on headings first, then on paragraphs, keeping
 * chunks under a max size with a small overlap so boundary context is preserved.
 */
public final class Chunker {

    private Chunker() {
    }

    public static List<String> chunk(String content, int maxChunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        for (String section : splitSections(content)) {
            chunks.addAll(chunkSection(section, maxChunkSize, overlap));
        }
        return chunks;
    }

    /** Split on markdown headings (## / ### ...), keeping the heading with its section. */
    private static List<String> splitSections(String content) {
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : content.split("\n")) {
            if (line.matches("^#{1,6}\\s+.*") && !current.isEmpty()) {
                sections.add(current.toString().strip());
                current = new StringBuilder();
            }
            current.append(line).append('\n');
        }
        if (!current.isEmpty()) {
            sections.add(current.toString().strip());
        }
        return sections;
    }

    /** Split one section into chunks by paragraphs, with overlap. */
    private static List<String> chunkSection(String section, int maxChunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (section.length() <= maxChunkSize) {
            chunks.add(section);
            return chunks;
        }

        StringBuilder current = new StringBuilder();
        for (String paragraph : section.split("\n\\s*\n")) {
            if (current.length() + paragraph.length() + 1 <= maxChunkSize) {
                if (!current.isEmpty()) {
                    current.append('\n');
                }
                current.append(paragraph);
            } else {
                if (!current.isEmpty()) {
                    chunks.add(current.toString().strip());
                }
                // carry the tail of the previous chunk for context continuity
                String tail = tailOf(current.toString(), overlap);
                current = new StringBuilder(tail);
                if (!current.isEmpty()) {
                    current.append('\n');
                }
                current.append(paragraph);

                // a single paragraph longer than maxChunkSize: hard-split it
                if (current.length() > maxChunkSize) {
                    String text = current.toString();
                    int from = 0;
                    while (from < text.length()) {
                        int to = Math.min(text.length(), from + maxChunkSize);
                        chunks.add(text.substring(from, to).strip());
                        if (to >= text.length()) {
                            break; // reached the tail — stop (from would not advance)
                        }
                        from = to - overlap;
                        if (from < 0) {
                            from = 0;
                        }
                    }
                    current = new StringBuilder();
                }
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().strip());
        }
        return chunks;
    }

    private static String tailOf(String text, int overlap) {
        if (text == null || text.isEmpty() || overlap <= 0) {
            return "";
        }
        return text.length() <= overlap ? text : text.substring(text.length() - overlap);
    }
}

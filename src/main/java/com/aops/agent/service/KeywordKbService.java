package com.aops.agent.service;

import com.aops.agent.config.AopsProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Keyword-based knowledge base (docs directory of markdown files).
 * Simple TF scoring with title weighting; replaceable by a vector store later.
 */
@Service
public class KeywordKbService implements KbService {

    private static final Logger log = LoggerFactory.getLogger(KeywordKbService.class);
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private record Doc(String title, String content, String[] words) {
    }

    private final List<Doc> docs = new ArrayList<>();

    public KeywordKbService(AopsProperties props) {
        load(props.kb().directory());
    }

    @PostConstruct
    void logLoaded() {
        log.info("Knowledge base loaded: {} documents", docs.size());
    }

    private void load(String directory) {
        Path dir = Path.of(directory).toAbsolutePath();
        if (!Files.isDirectory(dir)) {
            log.warn("KB directory not found: {} (empty knowledge base)", dir);
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".md"))
                    .forEach(p -> {
                        try {
                            String text = Files.readString(p);
                            docs.add(new Doc(p.getFileName().toString().replace(".md", ""), text,
                                    tokenize(text)));
                        } catch (IOException e) {
                            log.warn("Failed to read KB doc {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to scan KB directory {}: {}", dir, e.getMessage());
        }
    }

    @Override
    public List<KbResult> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String[] terms = tokenize(query);
        List<KbResult> results = new ArrayList<>();
        for (Doc doc : docs) {
            double score = 0;
            for (String term : terms) {
                int freq = 0;
                for (String w : doc.words()) {
                    if (w.equals(term)) {
                        freq++;
                    }
                }
                double weight = doc.title().toLowerCase(Locale.ROOT).contains(term) ? 2.0 : 1.0;
                score += freq * weight;
            }
            if (score > 0) {
                results.add(new KbResult(doc.title(), snippet(doc.content(), terms[0]), score));
            }
        }
        results.sort(Comparator.comparingDouble(KbResult::score).reversed());
        return results.stream().limit(limit).toList();
    }

    @Override
    public int documentCount() {
        return docs.size();
    }

    private static String[] tokenize(String text) {
        return WORD.matcher(text.toLowerCase(Locale.ROOT)).results()
                .map(m -> m.group())
                .toArray(String[]::new);
    }

    private static String snippet(String content, String term) {
        String lower = content.toLowerCase(Locale.ROOT);
        int idx = term == null ? -1 : lower.indexOf(term);
        if (idx < 0) {
            idx = 0;
        }
        int start = Math.max(0, idx - 80);
        int end = Math.min(content.length(), idx + 160);
        String s = content.substring(start, end).replaceAll("\\s+", " ").strip();
        return (start > 0 ? "..." : "") + s + (end < content.length() ? "..." : "");
    }
}

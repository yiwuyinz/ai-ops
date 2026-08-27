package com.aops.agent.kb;

import com.aops.agent.config.AopsProperties;
import com.aops.agent.service.KbService;
import com.aops.agent.service.KeywordKbService;
import com.aops.agent.service.RunbookService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * RAG knowledge base: hybrid retrieval combining keyword TF scoring with
 * pgvector cosine similarity over embedded chunks (KB docs + runbooks).
 *
 * <p>Graceful degradation: without {@code aops.kb.embedding-enabled=true} (or
 * when the embedding provider / vector store is missing or fails), every call
 * falls back to the keyword implementation.</p>
 */
@Service
@Primary
public class VectorKbService implements KbService {

    private static final Logger log = LoggerFactory.getLogger(VectorKbService.class);

    private final KeywordKbService keyword;
    private final RunbookService runbookService;
    private final AopsProperties props;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;

    private volatile boolean hybridReady = false;
    private volatile boolean warnLogged = false;

    public VectorKbService(KeywordKbService keyword,
                           RunbookService runbookService,
                           AopsProperties props,
                           @Autowired(required = false) EmbeddingProvider embeddingProvider,
                           @Autowired(required = false) VectorStore vectorStore) {
        this.keyword = keyword;
        this.runbookService = runbookService;
        this.props = props;
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    void init() {
        if (!props.kb().embeddingEnabled() || embeddingProvider == null || vectorStore == null) {
            log.info("Knowledge base: keyword mode (embedding disabled or not configured)");
            return;
        }
        if (props.kb().reindexOnStartup()) {
            try {
                int chunks = reindex();
                hybridReady = true;
                log.info("Knowledge base: hybrid RAG ready ({} chunks indexed)", chunks);
            } catch (Exception e) {
                log.warn("Vector KB init failed, falling back to keyword search: {}", e.getMessage());
            }
        } else {
            hybridReady = true;
            log.info("Knowledge base: hybrid RAG enabled (startup reindex disabled)");
        }
    }

    /**
     * Chunk and embed every KB document and runbook into the vector store.
     *
     * @return number of chunks indexed
     */
    public synchronized int reindex() {
        if (embeddingProvider == null || vectorStore == null) {
            throw new IllegalStateException(
                    "Embedding not configured: set aops.kb.embedding-enabled=true and an API key");
        }
        vectorStore.clear();
        int chunks = 0;
        for (KbDoc doc : loadKbDocs()) {
            chunks += indexDoc("kb", doc.title(), doc.content());
        }
        for (RunbookService.Runbook r : runbookService.all()) {
            chunks += indexDoc("runbook", r.name(), r.content());
        }
        hybridReady = true;
        log.info("KB reindexed: {} chunks", chunks);
        return chunks;
    }

    private int indexDoc(String source, String title, String content) {
        List<String> chunks = Chunker.chunk(content, props.kb().chunkSize(), props.kb().chunkOverlap());
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            vectorStore.upsert(source + ":" + title + ":" + i, source, title, i, chunk,
                    embeddingProvider.embed(chunk));
        }
        return chunks.size();
    }

    private record KbDoc(String title, String content) {
    }

    private List<KbDoc> loadKbDocs() {
        List<KbDoc> docs = new ArrayList<>();
        Path dir = Path.of(props.kb().directory()).toAbsolutePath();
        if (!Files.isDirectory(dir)) {
            return docs;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".md"))
                    .forEach(p -> {
                        try {
                            docs.add(new KbDoc(p.getFileName().toString().replace(".md", ""),
                                    Files.readString(p)));
                        } catch (IOException e) {
                            log.warn("Failed to read KB doc {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to scan KB directory {}: {}", dir, e.getMessage());
        }
        return docs;
    }

    @Override
    public List<KbResult> search(String query, int limit) {
        if (!hybridReady || embeddingProvider == null || vectorStore == null) {
            return keyword.search(query, limit);
        }
        try {
            float[] queryEmbedding = embeddingProvider.embed(query);
            List<VectorStore.Hit> vectorHits = vectorStore.search(queryEmbedding, Math.max(limit * 2, 5));
            List<KbResult> keywordResults = keyword.search(query, Math.max(limit * 2, 5));

            // merge by title: combined = vectorWeight * similarity + (1 - vectorWeight) * normalizedKeywordScore
            Map<String, Double> kwScore = new HashMap<>();
            double maxKw = 0;
            for (KbResult r : keywordResults) {
                kwScore.put(r.title(), r.score());
                maxKw = Math.max(maxKw, r.score());
            }

            Map<String, KbResult> merged = new LinkedHashMap<>();
            for (VectorStore.Hit hit : vectorHits) {
                double kwNorm = maxKw > 0 ? kwScore.getOrDefault(hit.title(), 0.0) / maxKw : 0.0;
                double combined = props.kb().vectorWeight() * hit.similarity()
                        + (1 - props.kb().vectorWeight()) * kwNorm;
                merged.put(hit.title(), new KbResult(hit.title(), snippet(hit.content()), combined));
            }
            for (KbResult r : keywordResults) {
                merged.computeIfAbsent(r.title(), t -> r);
            }

            return merged.values().stream()
                    .sorted(Comparator.comparingDouble(KbResult::score).reversed())
                    .limit(limit)
                    .toList();
        } catch (Exception e) {
            if (!warnLogged) {
                warnLogged = true;
                log.warn("Hybrid search failed, falling back to keyword: {}", e.getMessage());
            }
            return keyword.search(query, limit);
        }
    }

    private static String snippet(String content) {
        if (content == null) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").strip();
        return flat.length() <= 200 ? flat : flat.substring(0, 200) + "...";
    }

    @Override
    public int documentCount() {
        return keyword.documentCount();
    }

    @Override
    public String mode() {
        if (hybridReady) {
            return "hybrid";
        }
        return props.kb().embeddingEnabled() ? "keyword-fallback" : "keyword";
    }

    @Override
    public int chunkCount() {
        return vectorStore == null ? 0 : vectorStore.size();
    }
}

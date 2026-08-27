package com.aops.agent.kb;

import com.aops.agent.config.AopsProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * pgvector-backed vector store (PostgreSQL {@code vector} extension).
 * Enabled by {@code aops.kb.embedding-enabled=true}.
 *
 * <p>Table: {@code kb_chunks(id TEXT PK, source TEXT, title TEXT, chunk_index INT,
 * content TEXT, embedding vector(DIM))}. Distance operator {@code <=>} is cosine.</p>
 */
@Component
@ConditionalOnProperty(name = "aops.kb.embedding-enabled", havingValue = "true")
public class PgVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStore.class);

    private final JdbcTemplate jdbc;
    private final int dimension;
    private volatile boolean available = true;

    public PgVectorStore(JdbcTemplate jdbc, AopsProperties props) {
        this.jdbc = jdbc;
        this.dimension = props.kb().embeddingDimension();
    }

    @PostConstruct
    void init() {
        try {
            jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS kb_chunks (
                        id TEXT PRIMARY KEY,
                        source TEXT NOT NULL,
                        title TEXT NOT NULL,
                        chunk_index INT NOT NULL,
                        content TEXT NOT NULL,
                        embedding vector(%d)
                    )
                    """.formatted(dimension));
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_kb_chunks_embedding ON kb_chunks USING hnsw (embedding vector_cosine_ops)");
            log.info("pgvector store ready (dimension={})", dimension);
        } catch (Exception e) {
            available = false;
            log.warn("pgvector unavailable (falling back to keyword search): {}", e.getMessage());
        }
    }

    @Override
    public void upsert(String id, String source, String title, int chunkIndex, String content, float[] embedding) {
        if (!available) {
            return;
        }
        String vectorLiteral = toVectorLiteral(embedding);
        jdbc.update("""
                        INSERT INTO kb_chunks (id, source, title, chunk_index, content, embedding)
                        VALUES (?, ?, ?, ?, ?, ?::vector)
                        ON CONFLICT (id) DO UPDATE
                          SET content = EXCLUDED.content, embedding = EXCLUDED.embedding
                        """,
                id, source, title, chunkIndex, content, vectorLiteral);
    }

    @Override
    public List<Hit> search(float[] embedding, int limit) {
        if (!available) {
            return List.of();
        }
        String vectorLiteral = toVectorLiteral(embedding);
        return jdbc.query("""
                        SELECT source, title, content, 1 - (embedding <=> ?::vector) AS similarity
                        FROM kb_chunks
                        ORDER BY embedding <=> ?::vector
                        LIMIT ?
                        """,
                this::mapHit, vectorLiteral, vectorLiteral, limit);
    }

    @Override
    public void clear() {
        if (available) {
            jdbc.update("DELETE FROM kb_chunks");
        }
    }

    @Override
    public int size() {
        if (!available) {
            return 0;
        }
        Integer count = jdbc.queryForObject("SELECT count(*) FROM kb_chunks", Integer.class);
        return count == null ? 0 : count;
    }

    private Hit mapHit(ResultSet rs, int rowNum) throws SQLException {
        return new Hit(rs.getString("source"), rs.getString("title"),
                rs.getString("content"), rs.getDouble("similarity"));
    }

    /** pgvector accepts float arrays as "[0.1,0.2,...]" literals. */
    private static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}

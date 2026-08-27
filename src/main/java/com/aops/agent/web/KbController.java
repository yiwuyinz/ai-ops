package com.aops.agent.web;

import com.aops.agent.kb.VectorKbService;
import com.aops.agent.service.KbService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * KB management endpoints (RAG reindex).
 */
@RestController
@RequestMapping("/api/kb")
public class KbController {

    private final KbService kbService;

    public KbController(KbService kbService) {
        this.kbService = kbService;
    }

    /** Re-chunk + re-embed all KB docs and runbooks into the vector store. */
    @PostMapping("/reindex")
    public Map<String, Object> reindex() {
        if (!(kbService instanceof VectorKbService vectorKbService)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "RAG mode is disabled — set aops.kb.embedding-enabled=true and an API key");
        }
        int chunks = vectorKbService.reindex();
        return Map.of("mode", kbService.mode(), "reindexedChunks", chunks, "documents", kbService.documentCount());
    }
}

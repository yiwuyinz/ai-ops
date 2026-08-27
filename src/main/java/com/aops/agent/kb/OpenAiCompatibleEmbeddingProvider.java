package com.aops.agent.kb;

import com.aops.agent.config.AopsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI-compatible embeddings client — works with SiliconFlow (BAAI/bge-m3),
 * Alibaba dashscope (text-embedding-v3), etc. Enabled by
 * {@code aops.kb.embedding-enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "aops.kb.embedding-enabled", havingValue = "true")
public class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingProvider.class);

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String model;
    private final String apiKey;

    public OpenAiCompatibleEmbeddingProvider(AopsProperties props, ObjectMapper mapper) {
        this.restClient = RestClient.builder().baseUrl(props.kb().embeddingBaseUrl()).build();
        this.mapper = mapper;
        this.model = props.kb().embeddingModel();
        this.apiKey = props.kb().embeddingApiKey();
    }

    @Override
    public float[] embed(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", text);

        String response = restClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = mapper.readTree(response);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new IllegalStateException("No embedding in response: " + safeExcerpt(response));
            }
            JsonNode embedding = data.get(0).path("embedding");
            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = (float) embedding.get(i).asDouble();
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse embedding response: {}", e.getMessage());
            throw new RuntimeException("Embedding request failed", e);
        }
    }

    private static String safeExcerpt(String s) {
        return s == null ? "<empty>" : (s.length() > 300 ? s.substring(0, 300) + "..." : s);
    }
}

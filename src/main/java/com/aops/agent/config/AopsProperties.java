package com.aops.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Root configuration for the agent, bound from the {@code aops.*} namespace.
 * Immutable records -> constructor binding.
 */
@ConfigurationProperties(prefix = "aops")
public record AopsProperties(
        Agent agent,
        Tools tools,
        Topology topology,
        Runbook runbook,
        Kb kb,
        Dedup dedup,
        Notifier notifier,
        Async async,
        Eval eval
) {

    public record Agent(
            String mainModel,
            String baseUrl,
            String apiKey,
            String fastModel,
            double temperature,
            int timeoutSeconds,
            int maxSteps,
            int maxToolOutputChars,
            boolean enabled,
            double minConfidence,
            int llmRetries,
            /** Investigation mode: "single" (one agent, all tools) or "supervisor" (specialists + synthesis). */
            String mode
    ) {
        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        public boolean supervisorMode() {
            return "supervisor".equalsIgnoreCase(mode);
        }
    }

    public record Tools(Loki loki, Prometheus prometheus, Alertmanager alertmanager) {
        public record Loki(String baseUrl, int defaultLimit) {}
        public record Prometheus(String baseUrl) {}
        public record Alertmanager(String baseUrl) {}
    }

    public record Topology(String file) {}

    public record Runbook(String directory) {}

    /**
     * Knowledge base config. Embedding fields enable the RAG (pgvector hybrid)
     * mode; without them the keyword search is used as a fallback.
     */
    public record Kb(
            String directory,
            boolean embeddingEnabled,
            String embeddingBaseUrl,
            String embeddingApiKey,
            String embeddingModel,
            int embeddingDimension,
            int chunkSize,
            int chunkOverlap,
            double vectorWeight,
            boolean reindexOnStartup
    ) {
        public boolean embeddingConfigured() {
            return embeddingApiKey != null && !embeddingApiKey.isBlank();
        }
    }

    public record Dedup(String mode, int windowMinutes) {}

    public record Notifier(String type, String slackWebhookUrl) {}

    public record Async(int corePoolSize, int maxPoolSize, int queueCapacity) {}

    public record Eval(String scenariosDir) {}
}

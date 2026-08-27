package com.aops.agent;

import com.aops.agent.config.AopsProperties;

/**
 * Test helper: default {@link AopsProperties} with sane values.
 */
public final class TestProps {

    private TestProps() {
    }

    public static AopsProperties.Kb defaultKb() {
        return new AopsProperties.Kb("config/kb", false, "https://api.siliconflow.cn/v1", "test-embed-key",
                "BAAI/bge-m3", 1024, 700, 100, 0.6, true);
    }

    public static AopsProperties defaultProps() {
        return new AopsProperties(
                new AopsProperties.Agent(
                        "deepseek-chat", "https://api.deepseek.com", "test-key",
                        "deepseek-chat", 0.2, 180, 8, 6000, true, 0.6, 2, "single"),
                new AopsProperties.Tools(
                        new AopsProperties.Tools.Loki("http://localhost:3100", 500),
                        new AopsProperties.Tools.Prometheus("http://localhost:9090"),
                        new AopsProperties.Tools.Alertmanager("http://localhost:9093")),
                new AopsProperties.Topology("config/topology.json"),
                new AopsProperties.Runbook("config/runbooks"),
                defaultKb(),
                new AopsProperties.Dedup("memory", 60),
                new AopsProperties.Notifier("console", ""),
                new AopsProperties.Async(2, 4, 100),
                new AopsProperties.Eval("evals/scenarios"));
    }

    public static AopsProperties withKbDir(String kbDir) {
        AopsProperties base = defaultProps();
        return new AopsProperties(base.agent(), base.tools(), base.topology(), base.runbook(),
                new AopsProperties.Kb(kbDir, base.kb().embeddingEnabled(), base.kb().embeddingBaseUrl(),
                        base.kb().embeddingApiKey(), base.kb().embeddingModel(), base.kb().embeddingDimension(),
                        base.kb().chunkSize(), base.kb().chunkOverlap(), base.kb().vectorWeight(),
                        base.kb().reindexOnStartup()),
                base.dedup(), base.notifier(), base.async(), base.eval());
    }

    /** KB with RAG enabled — for hybrid search tests. */
    public static AopsProperties withHybridKb(String kbDir, String runbookDir, int dimension, int chunkSize) {
        AopsProperties base = defaultProps();
        AopsProperties.Kb kb = new AopsProperties.Kb(kbDir, true, base.kb().embeddingBaseUrl(),
                base.kb().embeddingApiKey(), base.kb().embeddingModel(), dimension, chunkSize, 50, 0.6, true);
        return new AopsProperties(base.agent(), base.tools(), base.topology(),
                new AopsProperties.Runbook(runbookDir), kb,
                base.dedup(), base.notifier(), base.async(), base.eval());
    }

    public static AopsProperties withRunbookDir(String runbookDir) {
        AopsProperties base = defaultProps();
        return new AopsProperties(base.agent(), base.tools(), base.topology(),
                new AopsProperties.Runbook(runbookDir), base.kb(), base.dedup(), base.notifier(), base.async(),
                base.eval());
    }

    public static AopsProperties withEvalDir(String evalDir) {
        AopsProperties base = defaultProps();
        return new AopsProperties(base.agent(), base.tools(), base.topology(), base.runbook(), base.kb(),
                base.dedup(), base.notifier(), base.async(), new AopsProperties.Eval(evalDir));
    }

    public static AopsProperties withMinConfidence(double minConfidence) {
        AopsProperties base = defaultProps();
        AopsProperties.Agent agent = new AopsProperties.Agent(
                base.agent().mainModel(), base.agent().baseUrl(), base.agent().apiKey(),
                base.agent().fastModel(), base.agent().temperature(), base.agent().timeoutSeconds(),
                base.agent().maxSteps(), base.agent().maxToolOutputChars(), base.agent().enabled(),
                minConfidence, base.agent().llmRetries(), base.agent().mode());
        return new AopsProperties(agent, base.tools(), base.topology(), base.runbook(), base.kb(),
                base.dedup(), base.notifier(), base.async(), base.eval());
    }
}

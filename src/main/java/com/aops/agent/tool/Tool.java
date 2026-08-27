package com.aops.agent.tool;

import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

/**
 * A single tool the agent can call — the HolmesGPT "tool" concept.
 * Tools are read-only by design (safe default); write tools require explicit opt-in later.
 */
public interface Tool {

    /** Unique tool name, e.g. "search_logs". */
    String name();

    /** Description shown to the LLM; MUST state when to use this tool. */
    String description();

    /** JSON schema for the tool arguments. */
    JsonObjectSchema parameters();

    /**
     * Execute the tool. Implementations must return detailed, self-explanatory text
     * (including exact queries, time ranges and underlying error messages) so the LLM
     * can self-correct — see the "error messages" pattern from HolmesGPT.
     */
    String execute(String argumentsJson) throws ToolException;
}

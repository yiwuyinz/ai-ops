package com.aops.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lenient JSON argument parsing for tools: missing/invalid fields fall back to
 * defaults, never throw.
 */
public class ToolArgs {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonNode node;

    private ToolArgs(JsonNode node) {
        this.node = node;
    }

    public static ToolArgs of(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return new ToolArgs(MAPPER.createObjectNode());
        }
        try {
            return new ToolArgs(MAPPER.readTree(argumentsJson));
        } catch (Exception e) {
            return new ToolArgs(MAPPER.createObjectNode());
        }
    }

    public String str(String field, String defaultValue) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) {
            return defaultValue;
        }
        String s = v.asText();
        return s.isBlank() ? defaultValue : s;
    }

    public int integer(String field, int defaultValue) {
        JsonNode v = node.get(field);
        if (v == null || !v.isNumber()) {
            return defaultValue;
        }
        return v.asInt(defaultValue);
    }

    public boolean bool(String field, boolean defaultValue) {
        JsonNode v = node.get(field);
        if (v == null || !v.isBoolean()) {
            return defaultValue;
        }
        return v.asBoolean(defaultValue);
    }
}

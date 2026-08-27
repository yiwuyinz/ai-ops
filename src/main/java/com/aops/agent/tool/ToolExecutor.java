package com.aops.agent.tool;

import com.aops.agent.agent.InvestigationContext;
import com.aops.agent.agent.InvestigationContextHolder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes tools for the LLM: resolves by name, truncates oversized output,
 * records the evidence chain, and converts failures into error text the LLM
 * can react to (HolmesGPT self-correction pattern).
 */
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry registry;

    public String execute(String toolName, String argumentsJson, int maxOutputChars) {
        InvestigationContext ctx = InvestigationContextHolder.get();

        Tool tool = registry.get(toolName);
        if (tool == null) {
            String msg = "ERROR: unknown tool '" + toolName + "'. Available tools: "
                    + registry.all().stream().map(Tool::name).toList();
            record(ctx, toolName, argumentsJson, msg);
            return msg;
        }

        long start = System.currentTimeMillis();
        try {
            String result = tool.execute(argumentsJson);
            String truncated = truncate(result, maxOutputChars);
            if (ctx != null) {
                ctx.recordEvidence(toolName, argumentsJson, truncated);
            }
            log.info("tool {} executed in {}ms ({} chars)", toolName,
                    System.currentTimeMillis() - start, truncated.length());
            return truncated;
        } catch (Exception e) {
            String msg = "ERROR calling tool '" + toolName + "': " + e.getMessage()
                    + "\nArguments were: " + argumentsJson
                    + "\nPlease fix your arguments or try a different approach.";
            record(ctx, toolName, argumentsJson, msg);
            log.warn("tool {} failed: {}", toolName, e.getMessage());
            return msg;
        }
    }

    private void record(InvestigationContext ctx, String toolName, String args, String output) {
        if (ctx != null) {
            ctx.recordEvidence(toolName, args, output);
        }
    }

    public static String truncate(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars) + "\n... [output truncated, " + (s.length() - maxChars) + " chars omitted]";
    }
}

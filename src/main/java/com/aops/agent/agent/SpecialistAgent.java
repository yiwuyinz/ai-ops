package com.aops.agent.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;

import java.time.Instant;
import java.util.List;

/**
 * One specialist agent: a focused system prompt + a NARROW tool subset + a
 * small step budget. Runs on its own thread (context is thread-local), records
 * its tool calls into the shared evidence chain, and returns a concise findings
 * summary for the supervisor to synthesize.
 */
public class SpecialistAgent {

    private final String name;
    private final String systemPrompt;
    private final List<ToolSpecification> tools;
    private final int maxSteps;
    private final ChatModel model;
    private final AgentLoop agentLoop;
    private final int maxToolOutputChars;
    private final int llmRetries;
    private final int timeoutSeconds;

    public SpecialistAgent(String name,
                           String systemPrompt,
                           List<ToolSpecification> tools,
                           int maxSteps,
                           ChatModel model,
                           AgentLoop agentLoop,
                           int maxToolOutputChars,
                           int llmRetries,
                           int timeoutSeconds) {
        this.name = name;
        this.systemPrompt = systemPrompt;
        this.tools = tools;
        this.maxSteps = maxSteps;
        this.model = model;
        this.agentLoop = agentLoop;
        this.maxToolOutputChars = maxToolOutputChars;
        this.llmRetries = llmRetries;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String name() {
        return name;
    }

    public String run(InvestigationContext ctx, String userPrompt, Instant deadline) {
        InvestigationContextHolder.set(ctx);
        try {
            return agentLoop.run(model, systemPrompt, userPrompt, tools,
                    maxSteps, maxToolOutputChars, llmRetries, timeoutSeconds, deadline);
        } finally {
            InvestigationContextHolder.clear();
        }
    }
}

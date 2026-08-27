package com.aops.agent.agent;

import com.aops.agent.tool.ToolExecutor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The agentic loop shared by the single-agent mode and every specialist agent:
 * LLM decides tool calls; we execute them and feed results back, until the LLM
 * stops calling tools, the step budget is exhausted, or the wall-clock deadline
 * passes. Transient LLM errors are retried with backoff.
 */
@Component
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private final ToolExecutor toolExecutor;

    public AgentLoop(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    /**
     * @return the final LLM text (analysis + verdict JSON in the standard format)
     */
    public String run(ChatModel model,
                      String systemPrompt,
                      String userPrompt,
                      List<ToolSpecification> toolSpecifications,
                      int maxSteps,
                      int maxToolOutputChars,
                      int llmRetries,
                      int timeoutSeconds,
                      Instant deadline) {
        List<ChatMessage> messages = new ArrayList<>(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)));

        String lastText = null;
        for (int step = 0; step < maxSteps; step++) {
            if (deadline != null && Instant.now().isAfter(deadline)) {
                log.warn("Agent loop timed out after {}s ({} steps) — stopping", timeoutSeconds, step);
                lastText = (lastText == null ? "" : lastText)
                        + "\n[investigation timed out after " + timeoutSeconds
                        + "s; findings may be incomplete]";
                break;
            }
            ChatResponse response = chatWithRetry(model, messages, toolSpecifications, llmRetries);
            AiMessage aiMessage = response.aiMessage();
            messages.add(aiMessage);
            lastText = aiMessage.text();

            List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
            if (requests == null || requests.isEmpty()) {
                log.debug("Agent finished after {} steps", step + 1);
                break;
            }
            log.info("Agent requested {} tool call(s) at step {} ({}ms)",
                    requests.size(), step + 1, System.currentTimeMillis() % 1000);
            for (ToolExecutionRequest request : requests) {
                String result = toolExecutor.execute(request.name(), request.arguments(), maxToolOutputChars);
                messages.add(ToolExecutionResultMessage.from(request.id(), request.name(), result));
            }
        }
        return lastText == null ? "(no output from LLM)" : lastText;
    }

    private ChatResponse chatWithRetry(ChatModel model,
                                       List<ChatMessage> messages,
                                       List<ToolSpecification> toolSpecifications,
                                       int llmRetries) {
        int maxAttempts = Math.max(1, llmRetries + 1);
        for (int attempt = 1; ; attempt++) {
            try {
                ChatRequest chatRequest = ChatRequest.builder()
                        .messages(messages)
                        .toolSpecifications(toolSpecifications)
                        .build();
                return model.chat(chatRequest);
            } catch (Exception e) {
                if (attempt >= maxAttempts) {
                    throw e;
                }
                log.warn("LLM call failed (attempt {}/{}): {} — retrying", attempt, maxAttempts, e.getMessage());
                try {
                    Thread.sleep(2000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }
}

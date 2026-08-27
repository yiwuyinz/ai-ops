package com.aops.agent.tool;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * summarize_output: shrink a large tool result with the fast model before it
 * goes back into the context window (HolmesGPT "fast model summarization").
 */
@Component
public class SummarizeOutputTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(SummarizeOutputTool.class);

    private final ChatModel fastChatModel;

    public SummarizeOutputTool(ChatModel fastChatModel) {
        this.fastChatModel = fastChatModel;
    }

    @Override
    public String name() {
        return "summarize_output";
    }

    @Override
    public String description() {
        return """
                Summarize a large block of text (e.g. a big log or metric dump) with a fast model.
                Use when a previous tool result was too large to reason about. Keep error messages,
                timestamps, numbers and service names verbatim where relevant.
                Parameters: text (required).
                """;
    }

    @Override
    public JsonObjectSchema parameters() {
        return JsonObjectSchema.builder()
                .addStringProperty("text")
                .required("text")
                .build();
    }

    @Override
    public String execute(String argumentsJson) {
        ToolArgs args = ToolArgs.of(argumentsJson);
        String text = args.str("text", "");
        if (text.isBlank()) {
            throw new ToolException("summarize_output requires a non-empty 'text'.");
        }
        try {
            String prompt = """
                    Summarize the following technical output for an SRE who is investigating an alert.
                    Keep error messages, exception types, timestamps, hostnames and numbers verbatim
                    where relevant. Be concise (bullet points). Output:

                    %s
                    """.formatted(text);
            return fastChatModel.chat(prompt);
        } catch (Exception e) {
            log.warn("summarize_output failed, returning raw text: {}", e.getMessage());
            return ToolExecutor.truncate(text, 4000);
        }
    }
}

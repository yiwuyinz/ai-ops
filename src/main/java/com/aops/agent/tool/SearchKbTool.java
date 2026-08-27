package com.aops.agent.tool;

import com.aops.agent.service.KbService;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * search_kb: semantic-ish search over the operations knowledge base.
 */
@Component
public class SearchKbTool implements Tool {

    private final KbService kbService;

    public SearchKbTool(KbService kbService) {
        this.kbService = kbService;
    }

    @Override
    public String name() {
        return "search_kb";
    }

    @Override
    public String description() {
        return """
                Search the operations knowledge base (past incidents, known issues, on-call notes)
                using hybrid keyword + semantic retrieval. Semantic search understands meaning, so
                phrasing the query differently still matches (e.g. query "connection refused" or
                "无法连接" both find docs about connection failures). Use when the alert looks
                familiar or when runbooks do not cover it.
                Parameters: query (required, natural language or keywords), limit (default 5).
                """;
    }

    @Override
    public JsonObjectSchema parameters() {
        return JsonObjectSchema.builder()
                .addStringProperty("query")
                .addIntegerProperty("limit")
                .required("query")
                .build();
    }

    @Override
    public String execute(String argumentsJson) {
        ToolArgs args = ToolArgs.of(argumentsJson);
        String query = args.str("query", "");
        if (query.isBlank()) {
            throw new ToolException("search_kb requires a non-empty 'query'.");
        }
        int limit = Math.max(1, Math.min(10, args.integer("limit", 5)));
        List<KbService.KbResult> results = kbService.search(query, limit);
        if (results.isEmpty()) {
            return "No knowledge base documents matched query \"" + query + "\".";
        }
        StringBuilder sb = new StringBuilder("Knowledge base matches for \"" + query + "\":\n");
        for (KbService.KbResult r : results) {
            sb.append("## ").append(r.title()).append(" (score ").append(String.format("%.2f", r.score()))
                    .append(")\n").append(r.snippet()).append("\n\n");
        }
        return sb.toString();
    }
}

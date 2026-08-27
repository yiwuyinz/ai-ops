package com.aops.agent.agent;

import com.aops.agent.domain.Verdict;
import com.aops.agent.domain.VerdictReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extracts the structured verdict from the agent's free-form output.
 * Lenient: tolerates missing fields and casing variations, never throws.
 *
 * <p>Extraction strategy: scan balanced JSON objects from the END of the text
 * (the prompt instructs the model to put the verdict JSON last) and try each
 * candidate until one parses. A naive "first {@code \{} to last {@code \}}"
 * substring breaks when the analysis prose itself contains braces, e.g.
 * {@code `up{job="demo-app"}`} — which is exactly how the first real run failed.</p>
 */
@Component
public class VerdictExtractor {

    private static final int MAX_CANDIDATES = 10;

    private final ObjectMapper mapper;

    public VerdictExtractor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public VerdictReport extract(String rawText) {
        for (String json : extractJsonCandidates(rawText)) {
            VerdictReport report = tryParse(rawText, json);
            if (report != null) {
                return report;
            }
        }
        return VerdictReport.fallback(rawText, "Agent output contained no structured JSON verdict.");
    }

    private VerdictReport tryParse(String rawText, String json) {
        try {
            JsonNode node = mapper.readTree(json);
            Verdict verdict = parseVerdict(node.path("verdict").asText(""));
            double confidence = node.path("confidence").asDouble(-1);
            if (confidence < 0 || confidence > 1) {
                confidence = 0;
            }
            boolean needsHuman = node.path("needsHuman").asBoolean(false);
            List<String> actions = new ArrayList<>();
            for (JsonNode a : node.path("suggestedActions")) {
                if (a.isTextual()) {
                    actions.add(a.asText());
                }
            }
            return new VerdictReport(
                    verdict,
                    confidence,
                    text(node, "rootCause"),
                    text(node, "summary"),
                    text(node, "evidenceSummary"),
                    actions,
                    needsHuman,
                    text(node, "reasonForEscalation"),
                    rawText
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Collect balanced JSON objects, newest (last in text) first, up to
     * {@link #MAX_CANDIDATES}. Each candidate is the substring between a
     * matching {@code {} / {}} pair found by scanning backwards from the end.
     */
    private List<String> extractJsonCandidates(String text) {
        List<String> candidates = new ArrayList<>();
        if (text == null) {
            return candidates;
        }
        int end = text.lastIndexOf('}');
        while (end >= 0 && candidates.size() < MAX_CANDIDATES) {
            int start = matchingOpenBrace(text, end);
            if (start < 0) {
                break;
            }
            candidates.add(text.substring(start, end + 1));
            end = text.lastIndexOf('}', start - 1);
        }
        return candidates;
    }

    /** Find the {@code {} that balances the {@code }} at closeIndex. */
    private int matchingOpenBrace(String text, int closeIndex) {
        int depth = 0;
        for (int i = closeIndex; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '}') {
                depth++;
            } else if (c == '{') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private Verdict parseVerdict(String value) {
        if (value == null || value.isBlank()) {
            return Verdict.INCONCLUSIVE;
        }
        String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_').trim();
        try {
            return Verdict.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return Verdict.INCONCLUSIVE;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String s = value.asText();
        return s.isBlank() ? null : s;
    }
}

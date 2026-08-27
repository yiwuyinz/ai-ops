package com.aops.agent.agent;

import com.aops.agent.domain.Verdict;
import com.aops.agent.domain.VerdictReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerdictExtractorTest {

    private final VerdictExtractor extractor = new VerdictExtractor(new ObjectMapper());

    @Test
    void parsesValidJson() {
        String text = "Analysis text...\n" + """
                {"verdict":"CONFIRMED","confidence":0.85,"rootCause":"db pool exhausted",
                "summary":"found 500 errors","evidenceSummary":"5xx + pool metric",
                "suggestedActions":["scale db"],"needsHuman":false,"reasonForEscalation":""}
                """;
        VerdictReport report = extractor.extract(text);
        assertEquals(Verdict.CONFIRMED, report.verdict());
        assertEquals(0.85, report.confidence());
        assertEquals("db pool exhausted", report.rootCause());
        assertFalse(report.needsHuman());
        assertEquals(1, report.suggestedActions().size());
    }

    @Test
    void missingJsonFallsBackToInconclusiveEscalated() {
        VerdictReport report = extractor.extract("I could not determine the cause.");
        assertEquals(Verdict.INCONCLUSIVE, report.verdict());
        assertTrue(report.needsHuman());
    }

    @Test
    void invalidVerdictValueDefaultsToInconclusive() {
        String text = "{\"verdict\":\"MAYBE\",\"confidence\":0.5,\"needsHuman\":false}";
        VerdictReport report = extractor.extract(text);
        assertEquals(Verdict.INCONCLUSIVE, report.verdict());
    }

    @Test
    void toleratesSnakeCaseVerdict() {
        String text = "{\"verdict\":\"LIKELY_FALSE_POSITIVE\",\"confidence\":0.7,\"needsHuman\":false}";
        VerdictReport report = extractor.extract(text);
        assertEquals(Verdict.LIKELY_FALSE_POSITIVE, report.verdict());
        assertEquals(0.7, report.confidence());
    }

    /**
     * Regression from the first real LLM run: the analysis prose contains inline
     * braces (e.g. `up{job="demo-app"}`), which broke the naive "first { to last }"
     * extraction and downgraded a correct CONFIRMED verdict to INCONCLUSIVE.
     */
    @Test
    void parsesVerdictWhenProseContainsInlineBraces() {
        String text = """
                The evidence is clear: `up{job="demo-app"}` = 0 for 3 hours, no logs at all.
                {"verdict":"CONFIRMED","confidence":0.9,"rootCause":"demo-app is down","summary":"genuine incident",
                "evidenceSummary":"up=0 across 180 minutes","suggestedActions":["restart the service"],
                "needsHuman":true,"reasonForEscalation":"recovery requires write access"}
                """;
        VerdictReport report = extractor.extract(text);
        assertEquals(Verdict.CONFIRMED, report.verdict());
        assertEquals(0.9, report.confidence());
        assertTrue(report.needsHuman());
        assertEquals("demo-app is down", report.rootCause());
        assertEquals(1, report.suggestedActions().size());
    }

    /** A malformed fragment before the real JSON must not poison extraction. */
    @Test
    void skipsMalformedJsonCandidates() {
        String text = """
                Some prose with an invalid fragment {"not json}
                {"verdict":"LIKELY_FALSE_POSITIVE","confidence":0.7,"needsHuman":false}
                """;
        VerdictReport report = extractor.extract(text);
        assertEquals(Verdict.LIKELY_FALSE_POSITIVE, report.verdict());
        assertEquals(0.7, report.confidence());
    }
}

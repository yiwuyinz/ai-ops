package com.aops.agent.eval;

import java.time.Instant;
import java.util.List;

/**
 * One evaluation run: all scenarios executed against the real pipeline.
 */
public record EvalRun(
        String runId,
        Instant startedAt,
        Instant finishedAt,
        List<EvalResult> results,
        /** True when the run was skipped because the agent is not in LLM mode. */
        boolean skipped
) {

    public int passed() {
        return (int) results.stream().filter(EvalResult::passed).count();
    }

    public int total() {
        return results.size();
    }
}

package com.aops.agent.eval;

import com.aops.agent.TestProps;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalScenarioServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsScenariosFromDirectory() throws Exception {
        Files.writeString(tempDir.resolve("one.json"), """
                {
                  "id": "scenario-1",
                  "name": "First scenario",
                  "description": "desc",
                  "alert": {"alertName": "TestAlert", "labels": {"service": "demo"}},
                  "expectedVerdict": "CONFIRMED",
                  "requiredTools": ["query_metric"]
                }
                """);
        Files.writeString(tempDir.resolve("broken.json"), "{ not valid json");

        EvalScenarioService service = new EvalScenarioService(TestProps.withEvalDir(tempDir.toString()), new ObjectMapper());

        List<EvalScenario> scenarios = service.all();
        assertEquals(1, scenarios.size());
        assertEquals("scenario-1", scenarios.get(0).id());
        assertEquals("CONFIRMED", scenarios.get(0).expectedVerdict());
        assertTrue(service.findById("scenario-1").isPresent());
    }

    @Test
    void missingDirectoryYieldsEmptyCatalog() {
        EvalScenarioService service = new EvalScenarioService(
                TestProps.withEvalDir("does-not-exist-dir"), new ObjectMapper());
        assertTrue(service.all().isEmpty());
    }

    /** Question-mode (HolmesGPT QA translation) binding. */
    @Test
    void loadsQuestionModeScenario() throws Exception {
        Files.writeString(tempDir.resolve("qa.json"), """
                {
                  "id": "hlg-97",
                  "name": "Ask for clarification",
                  "description": "desc",
                  "userPrompt": "Get me logs for the last 30 minutes",
                  "expectedVerdict": "any",
                  "requiredTools": ["search_logs"],
                  "mustContain": ["which service"]
                }
                """);

        EvalScenarioService service = new EvalScenarioService(TestProps.withEvalDir(tempDir.toString()), new ObjectMapper());

        EvalScenario scenario = service.findById("hlg-97").orElseThrow();
        assertTrue(scenario.isQuestionMode());
        assertEquals("Get me logs for the last 30 minutes", scenario.userPrompt());
        assertEquals(List.of("search_logs"), scenario.requiredTools());
        assertTrue(scenario.expectsAnyVerdict());
    }
}

package com.aops.agent.web;

import com.aops.agent.config.AopsProperties;
import com.aops.agent.service.KbService;
import com.aops.agent.service.RunbookService;
import com.aops.agent.service.TopologyService;
import com.aops.agent.tool.ToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime info endpoint (tools manifest, data sizes, agent mode).
 */
@RestController
public class InfoController {

    private static final String VERSION = "0.1.0";

    private final ToolRegistry toolRegistry;
    private final TopologyService topologyService;
    private final RunbookService runbookService;
    private final KbService kbService;
    private final AopsProperties props;

    public InfoController(ToolRegistry toolRegistry,
                          TopologyService topologyService,
                          RunbookService runbookService,
                          KbService kbService,
                          AopsProperties props) {
        this.toolRegistry = toolRegistry;
        this.topologyService = topologyService;
        this.runbookService = runbookService;
        this.kbService = kbService;
        this.props = props;
    }

    @GetMapping("/api/info")
    public Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", VERSION);
        body.put("agentEnabled", props.agent().enabled());
        body.put("llmConfigured", props.agent().hasApiKey());
        body.put("model", props.agent().mainModel());
        body.put("fastModel", props.agent().fastModel());
        body.put("maxSteps", props.agent().maxSteps());
        body.put("dedupMode", props.dedup().mode());
        body.put("dedupWindowMinutes", props.dedup().windowMinutes());
        body.put("notifierType", props.notifier().type());
        body.put("topologySize", topologyService.all().size());
        body.put("runbookCount", runbookService.all().size());
        body.put("kbDocumentCount", kbService.documentCount());
        body.put("kbMode", kbService.mode());
        body.put("kbChunkCount", kbService.chunkCount());
        body.put("tools", toolRegistry.manifest());
        return body;
    }
}

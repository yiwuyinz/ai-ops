package com.aops.agent.web;

import com.aops.agent.agent.InvestigationRunner;
import com.aops.agent.domain.CaseEntity;
import com.aops.agent.domain.CaseStatus;
import com.aops.agent.domain.EvidenceEntity;
import com.aops.agent.domain.FeedbackEntity;
import com.aops.agent.service.CaseService;
import com.aops.agent.service.FeedbackService;
import com.aops.agent.web.dto.CaseView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Case query endpoints (list / detail / re-investigate).
 */
@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final CaseService caseService;
    private final FeedbackService feedbackService;
    private final InvestigationRunner investigationRunner;
    private final ObjectMapper mapper;

    public CaseController(CaseService caseService,
                          FeedbackService feedbackService,
                          InvestigationRunner investigationRunner,
                          ObjectMapper mapper) {
        this.caseService = caseService;
        this.feedbackService = feedbackService;
        this.investigationRunner = investigationRunner;
        this.mapper = mapper;
    }

    @GetMapping
    public List<CaseView> list() {
        return caseService.recent().stream()
                .map(c -> new CaseView(
                        c.getId(), c.getAlertName(), c.getServiceName(), c.getStatus(),
                        c.getVerdict(), c.getConfidence(), c.isNeedsHuman(),
                        c.getCreatedAt(), c.getInvestigatedAt()))
                .toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        CaseEntity c = caseService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found: " + id));

        List<EvidenceEntity> evidence = caseService.evidenceFor(id);
        List<FeedbackEntity> feedback = feedbackService.byCase(id);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", c.getId());
        // Original alert info (as received from the alert source)
        body.put("alertName", c.getAlertName());
        body.put("alertStatus", c.getAlertStatus());
        body.put("alertStartedAt", c.getAlertStartedAt());
        body.put("alertEndedAt", c.getAlertEndedAt());
        body.put("labels", c.getLabelsJson());
        body.put("annotations", c.getAnnotationsJson());
        // Investigation result
        body.put("serviceName", c.getServiceName());
        body.put("status", c.getStatus());
        body.put("verdict", c.getVerdict());
        body.put("confidence", c.getConfidence());
        body.put("needsHuman", c.isNeedsHuman());
        body.put("rootCause", c.getRootCause());
        body.put("summary", c.getSummary());
        body.put("suggestedActions", parseActions(c.getSuggestedActionsJson()));
        body.put("errorMessage", c.getErrorMessage());
        body.put("createdAt", c.getCreatedAt());
        body.put("investigatedAt", c.getInvestigatedAt());
        body.put("reportMarkdown", c.getReportMarkdown());
        body.put("evidence", evidence);
        body.put("feedback", feedback);
        return body;
    }

    @PostMapping("/{id}/reinvestigate")
    public ResponseEntity<Map<String, String>> reinvestigate(@PathVariable String id) {
        CaseEntity c = caseService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found: " + id));
        if (c.getStatus() == CaseStatus.INVESTIGATING) {
            return ResponseEntity.ok(Map.of("message", "Case is already being investigated."));
        }
        caseService.transitionUnchecked(c, CaseStatus.INVESTIGATING);
        investigationRunner.investigate(c.getId());
        return ResponseEntity.accepted().body(Map.of("message", "Re-investigation started", "caseId", c.getId()));
    }

    private List<String> parseActions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}

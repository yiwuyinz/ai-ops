package com.aops.agent.agent;

import com.aops.agent.domain.CaseEntity;
import com.aops.agent.domain.EvidenceEntity;
import com.aops.agent.domain.VerdictReport;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Renders the final markdown report: verdict + human-readable summary + the
 * full evidence chain (for audit and human handoff).
 */
@Component
public class ReportBuilder {

    public String build(CaseEntity entity, VerdictReport verdict, List<EvidenceEntity> evidence,
                        String rawText, com.aops.agent.domain.CaseStatus finalStatus) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 调查报告 — ").append(entity.getAlertName()).append("\n\n");
        sb.append("- Case ID: `").append(entity.getId()).append("`\n");
        sb.append("- 服务: ").append(nvl(entity.getServiceName())).append("\n");
        sb.append("- 状态: ").append(finalStatus).append("\n");

        if (verdict != null) {
            sb.append("- 裁决: **").append(verdict.verdict()).append("**\n");
            sb.append(String.format(Locale.ROOT, "- 置信度: %.0f%%%n", verdict.confidence() * 100));
            if (notBlank(verdict.rootCause())) {
                sb.append("- 根因: ").append(verdict.rootCause()).append("\n");
            }
            if (notBlank(verdict.summary())) {
                sb.append("- 摘要: ").append(verdict.summary()).append("\n");
            }
            if (notBlank(verdict.evidenceSummary())) {
                sb.append("- 证据摘要: ").append(verdict.evidenceSummary()).append("\n");
            }
            if (verdict.suggestedActions() != null && !verdict.suggestedActions().isEmpty()) {
                sb.append("- 建议动作:\n");
                for (String action : verdict.suggestedActions()) {
                    sb.append("  - ").append(action).append("\n");
                }
            }
            sb.append("- 需要人工: ").append(verdict.needsHuman() ? "**是**" : "否").append("\n");
            if (notBlank(verdict.reasonForEscalation())) {
                sb.append("- 升级原因: ").append(verdict.reasonForEscalation()).append("\n");
            }
        } else {
            sb.append("- 状态说明: 调查失败，请人工介入。\n");
        }

        sb.append("\n## 证据链\n");
        if (evidence == null || evidence.isEmpty()) {
            sb.append("（无工具调用）\n");
        } else {
            for (EvidenceEntity e : evidence) {
                sb.append(e.getStep()).append(". **").append(e.getToolName()).append("** `")
                        .append(e.getParameters()).append("`\n```\n")
                        .append(e.getResultExcerpt()).append("\n```\n");
            }
        }

        if (rawText != null && !rawText.isBlank()) {
            sb.append("\n## Agent 原始输出\n```\n").append(rawText).append("\n```\n");
        }
        return sb.toString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}

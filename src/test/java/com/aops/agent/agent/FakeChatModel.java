package com.aops.agent.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic fake ChatModel for orchestration tests:
 * <ul>
 *   <li>routes by the system prompt (LOG/METRIC/KNOWLEDGE specialist, SUPERVISOR)
 *       and returns canned findings — safe for parallel specialists;</li>
 *   <li>falls back to a scripted response queue for generic loop tests.</li>
 * </ul>
 */
class FakeChatModel implements ChatModel {

    enum Role { LOG, METRIC, KB, SUPERVISOR, OTHER }

    private final Map<Role, AtomicInteger> calls = new ConcurrentHashMap<>();
    private final Queue<AiMessage> script = new ConcurrentLinkedQueue<>();
    private volatile ChatRequest lastRequest;

    void script(AiMessage... messages) {
        for (AiMessage m : messages) {
            script.add(m);
        }
    }

    int calls(Role role) {
        return calls.computeIfAbsent(role, r -> new AtomicInteger()).get();
    }

    ChatRequest lastRequest() {
        return lastRequest;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        this.lastRequest = request;
        Role role = detect(request);
        calls.computeIfAbsent(role, r -> new AtomicInteger()).incrementAndGet();
        switch (role) {
            case LOG:
                return text("LOG FINDINGS: payment-api times out calling db-primary; "
                        + "db-primary logs show slow queries (8.2s) and query timeouts");
            case METRIC:
                return text("METRIC FINDINGS: payment-api latency ~5s, error rate 12%; "
                        + "db-primary query duration elevated");
            case KB:
                return text("KB FINDINGS: runbook 'payment-timeout' applies; "
                        + "topology shows payment-api depends on db-primary; no recent deploys");
            case SUPERVISOR:
                return text("Analysis: specialists show payment-api timing out on db-primary.\n"
                        + "{\"verdict\":\"CONFIRMED\",\"confidence\":0.85,\"rootCause\":\"db-primary slow queries\","
                        + "\"summary\":\"payment api times out on slow downstream db\","
                        + "\"evidenceSummary\":\"db-primary 8.2s queries; payment-api 503s\","
                        + "\"suggestedActions\":[\"investigate db-primary\"],"
                        + "\"needsHuman\":true,\"reasonForEscalation\":\"recovery needs write access\"}");
            default:
                AiMessage m = script.poll();
                return m != null
                        ? ChatResponse.builder().aiMessage(m).build()
                        : text("(no scripted response)");
        }
    }

    private ChatResponse text(String content) {
        return ChatResponse.builder().aiMessage(AiMessage.from(content)).build();
    }

    private Role detect(ChatRequest request) {
        for (ChatMessage m : request.messages()) {
            if (m instanceof SystemMessage sm && sm.text() != null) {
                if (sm.text().contains("LOG ANALYSIS specialist")) {
                    return Role.LOG;
                }
                if (sm.text().contains("METRICS specialist")) {
                    return Role.METRIC;
                }
                if (sm.text().contains("KNOWLEDGE specialist")) {
                    return Role.KB;
                }
                if (sm.text().contains("SUPERVISOR")) {
                    return Role.SUPERVISOR;
                }
            }
        }
        return Role.OTHER;
    }
}

package com.aops.agent.agent;

/**
 * Thread-bound holder for the active {@link InvestigationContext} so tools can
 * record evidence without the framework passing context around.
 */
public final class InvestigationContextHolder {

    private static final ThreadLocal<InvestigationContext> HOLDER = new ThreadLocal<>();

    private InvestigationContextHolder() {
    }

    public static void set(InvestigationContext ctx) {
        HOLDER.set(ctx);
    }

    public static InvestigationContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}

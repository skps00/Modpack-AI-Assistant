package com.skps9.packai.logic;

/**
 * One-shot no-tools LLM turn after tool-loop budget exhaustion when the model never
 * returned displayable prose. Pure decision state for {@link AskToolLoop} and headless checks.
 */
public final class AskFinalRound {
    public enum Reason {
        ROUNDS_EXHAUSTED("rounds_exhausted"),
        WALL_EXHAUSTED("wall_exhausted");

        private final String traceToken;

        Reason(String traceToken) {
            this.traceToken = traceToken;
        }

        public String traceToken() {
            return traceToken;
        }
    }

    private boolean producedProse;
    private boolean budgetExhausted;
    private Reason budgetReason;
    private boolean finalRoundAttempted;
    /** Headless negative control: skip the no-prose gate (scenario 2 must fail). */
    boolean testIgnoreNoProseGate;

    public void noteLlmContent(String content) {
        if (!AskToolLoop.proseOrBlank(content).isBlank()) {
            producedProse = true;
        }
    }

    public void markBudgetExhausted(Reason reason) {
        if (reason == null) {
            return;
        }
        budgetExhausted = true;
        budgetReason = reason;
    }

    public boolean producedProse() {
        return producedProse;
    }

    public boolean finalRoundAttempted() {
        return finalRoundAttempted;
    }

    public Reason budgetReason() {
        return budgetReason;
    }

    /**
     * True at most once when the tool loop ended on budget and the model never produced prose.
     */
    public boolean shouldFinalRound() {
        if (!budgetExhausted || finalRoundAttempted) {
            return false;
        }
        if (!testIgnoreNoProseGate && producedProse) {
            return false;
        }
        return true;
    }

    public void markFinalRoundAttempted() {
        finalRoundAttempted = true;
    }

    static Reason reasonFor(AskLoopState state, int toolHops) {
        if (state != null && state.wallExpired()) {
            return Reason.WALL_EXHAUSTED;
        }
        if (state != null && toolHops >= state.maxLlmRounds()) {
            return Reason.ROUNDS_EXHAUSTED;
        }
        if (state != null && !state.canLlm()) {
            return Reason.ROUNDS_EXHAUSTED;
        }
        return Reason.ROUNDS_EXHAUSTED;
    }
}

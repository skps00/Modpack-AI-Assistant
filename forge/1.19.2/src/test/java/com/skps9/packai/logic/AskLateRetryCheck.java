package com.skps9.packai.logic;

/** Late no-tools retry at llmEmpty gate. Run with -ea. */
public final class AskLateRetryCheck {
    private AskLateRetryCheck() {}

    /**
     * Truncated DSML from 20-q NFWC run ({@code 2878131}+): scrub leaves junk, not visibly empty.
     */
    private static final String REAL_TRUNCATED_DSML = ""
            + "<\uFF5C\uFF5CDSML\uFF5C\uFF5C calls>\n"
            + "<\uFF5C\uFF5CDSML\uFF5C\uFF5C invoke name=\"item_search\">\n"
            + "<\uFF5C||DSM";

    /** Headless negative control: skip {@code isToolSyntaxOnly} on llmEmpty gate (mirrors AskEngine). */
    static boolean testIgnoreToolSyntaxOnlyInLlmEmpty;

    public static void main(String[] args) {
        emptyTriggersOnce();
        secondEmptyDoesNotRetry();
        proseRetryAccepted();
        dsmlRetryRejected();
        negativeControlWithoutToolSyntaxGate();
        pureDsmlCountsAsLlmEmpty();
        proseWithDsmlNotLlmEmpty();
        negativeControlWithoutToolSyntaxOnlyInLlmEmpty();
        finalRoundDsmlLeavesLateRetryOpen();
        System.out.println("AskLateRetryCheck OK");
    }

    /** Same predicate as {@link com.skps9.packai.logic.AskEngine} llmEmpty before late retry. */
    private static boolean llmEmptyAtGate(String llmAnswer) {
        if (llmAnswer == null || llmAnswer.isBlank()) {
            return true;
        }
        String proseScrubbed = AskReplyScrub.scrubPromptEcho(llmAnswer);
        if (AskReplyScrub.isVisiblyEmpty(proseScrubbed)) {
            return true;
        }
        return !testIgnoreToolSyntaxOnlyInLlmEmpty && AskReplyScrub.isToolSyntaxOnly(llmAnswer);
    }

    private static void emptyTriggersOnce() {
        AskFinalRound fr = new AskFinalRound();
        assert AskLateRetry.shouldAttempt(true, fr) : "first empty must attempt";
        fr.markFinalRoundAttempted();
        assert !AskLateRetry.shouldAttempt(true, fr) : "must not re-enter";
        System.out.println("emptyTriggersOnce OK");
    }

    private static void secondEmptyDoesNotRetry() {
        AskFinalRound fr = new AskFinalRound();
        fr.markFinalRoundAttempted();
        assert !AskLateRetry.shouldAttempt(true, fr) : "already attempted";
        assert !AskLateRetry.shouldAttempt(false, fr) : "not empty";
        System.out.println("secondEmptyDoesNotRetry OK");
    }

    private static void proseRetryAccepted() {
        String retry = "Craft with sticks and planks.";
        String scrubbed = AskReplyScrub.scrubPromptEcho(retry);
        assert AskLateRetry.acceptRetry(retry, scrubbed) : "prose retry";
        System.out.println("proseRetryAccepted OK");
    }

    private static void dsmlRetryRejected() {
        String retry = AskDsmlLeakCheck.NFWC_LEAK;
        String scrubbed = AskReplyScrub.scrubPromptEcho(retry);
        assert !AskLateRetry.acceptRetry(retry, scrubbed) : "DSML-only must stay empty";
        System.out.println("dsmlRetryRejected OK");
    }

    /** Without tool-syntax gate, DSML retry would incorrectly count as accepted. */
    private static void negativeControlWithoutToolSyntaxGate() {
        AskLateRetry.testIgnoreToolSyntaxGate = true;
        try {
            String retry = AskDsmlLeakCheck.NFWC_LEAK;
            String scrubbed = AskReplyScrub.scrubPromptEcho(retry);
            if (AskLateRetry.acceptRetry(retry, scrubbed)) {
                throw new AssertionError("negative control FAILED: gate removed but DSML accepted");
            }
            System.out.println("negativeControlWithoutToolSyntaxGate OK");
        } finally {
            AskLateRetry.testIgnoreToolSyntaxGate = false;
        }
    }

    private static void pureDsmlCountsAsLlmEmpty() {
        assert AskReplyScrub.isToolSyntaxOnly(AskDsmlLeakCheck.NFWC_LEAK) : "trace DSML is tool-only";
        assert llmEmptyAtGate(AskDsmlLeakCheck.NFWC_LEAK) : "pure DSML from trace must be llmEmpty";
        System.out.println("pureDsmlCountsAsLlmEmpty OK");
    }

    private static void proseWithDsmlNotLlmEmpty() {
        String mixed = "Here is the answer: <\uFF5C\uFF5CDSML\uFF5C\uFF5C>";
        assert !llmEmptyAtGate(mixed) : "prose with DSML fragment must not be llmEmpty";
        System.out.println("proseWithDsmlNotLlmEmpty OK");
    }

    /** Without tool-syntax-only on llmEmpty gate, pure DSML would not trigger late retry. */
    private static void negativeControlWithoutToolSyntaxOnlyInLlmEmpty() {
        assert !AskReplyScrub.isVisiblyEmpty(AskReplyScrub.scrubPromptEcho(REAL_TRUNCATED_DSML))
                : "truncated trace scrub must stay non-visibly-empty";
        testIgnoreToolSyntaxOnlyInLlmEmpty = true;
        try {
            if (llmEmptyAtGate(REAL_TRUNCATED_DSML)) {
                throw new AssertionError(
                        "negative control FAILED: gate removed but pure DSML still llmEmpty");
            }
            System.out.println("negativeControlWithoutToolSyntaxOnlyInLlmEmpty OK");
        } finally {
            testIgnoreToolSyntaxOnlyInLlmEmpty = false;
        }
    }

    /** Mirrors {@link AskToolLoop#finishOnToolBudget} after a DSML-only final no-tools reply. */
    private static void finalRoundDsmlLeavesLateRetryOpen() {
        AskFinalRound fr = new AskFinalRound();
        fr.markBudgetExhausted(AskFinalRound.Reason.ROUNDS_EXHAUSTED);
        assert AskLateRetry.shouldAttempt(true, fr) : "before final round";
        AskToolLoop.finalizeFinalNoToolsAttempt(fr, AskDsmlLeakCheck.NFWC_LEAK);
        assert !fr.finalRoundAttempted() : "DSML final round must not mark attempted";
        assert AskLateRetry.shouldAttempt(true, fr) : "late retry after rejected final round";
        System.out.println("finalRoundDsmlLeavesLateRetryOpen OK");
    }
}

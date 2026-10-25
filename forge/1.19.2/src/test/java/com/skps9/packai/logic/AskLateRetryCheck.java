package com.skps9.packai.logic;

/** Late no-tools retry at llmEmpty gate. Run with -ea. */
public final class AskLateRetryCheck {
    private AskLateRetryCheck() {}

    public static void main(String[] args) {
        emptyTriggersOnce();
        secondEmptyDoesNotRetry();
        proseRetryAccepted();
        dsmlRetryRejected();
        negativeControlWithoutToolSyntaxGate();
        System.out.println("AskLateRetryCheck OK");
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
}

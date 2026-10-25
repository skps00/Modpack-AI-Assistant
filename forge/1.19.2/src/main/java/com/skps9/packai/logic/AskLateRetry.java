package com.skps9.packai.logic;

/** One-shot no-tools retry when pipeline already decided the answer is empty ({@link AskEngine}). */
public final class AskLateRetry {
    private AskLateRetry() {}

    /** Headless negative control: skip tool-syntax rejection on retry body. */
    static boolean testIgnoreToolSyntaxGate;

    public static boolean shouldAttempt(boolean llmEmpty, AskFinalRound finalRound) {
        return llmEmpty && finalRound != null && !finalRound.finalRoundAttempted();
    }

    public static boolean acceptRetry(String retryRaw, String retryScrubbed) {
        if (retryScrubbed == null || retryScrubbed.isBlank()) {
            return false;
        }
        if (AskReplyScrub.isVisiblyEmpty(retryScrubbed)) {
            return false;
        }
        if (!testIgnoreToolSyntaxGate && retryRaw != null && AskReplyScrub.isToolSyntaxOnly(retryRaw)) {
            return false;
        }
        return true;
    }
}

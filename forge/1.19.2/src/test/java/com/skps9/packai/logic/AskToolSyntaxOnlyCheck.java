package com.skps9.packai.logic;

/**
 * Pure DSML / {@code [[tools]]} replies must count as empty for prefallback. Run with -ea.
 */
public final class AskToolSyntaxOnlyCheck {
    private AskToolSyntaxOnlyCheck() {}

    public static void main(String[] args) {
        pureDsmlFromTrace();
        truncatedDsmlTail();
        toolsJsonMarker();
        proseWithDsmlFragment();
        normalEnglish();
        blankInput();
        negativeControlNoLetterDigitGate();
        System.out.println("AskToolSyntaxOnlyCheck OK");
    }

    private static void pureDsmlFromTrace() {
        assert AskReplyScrub.isToolSyntaxOnly(AskDsmlLeakCheck.NFWC_LEAK)
                : "NFWC fullwidth DSML must be tool-syntax-only";
        System.out.println("pureDsmlFromTrace OK");
    }

    private static void truncatedDsmlTail() {
        String truncated = AskDsmlLeakCheck.NFWC_LEAK + "\n</\uFF5C\uFF5CDS";
        assert AskReplyScrub.isToolSyntaxOnly(truncated) : "truncated DSML close tag";
        System.out.println("truncatedDsmlTail OK");
    }

    private static void toolsJsonMarker() {
        assert AskReplyScrub.isToolSyntaxOnly("[[tools]]{\"name\":\"item_search\"}")
                : "[[tools]] JSON block";
        System.out.println("toolsJsonMarker OK");
    }

    private static void proseWithDsmlFragment() {
        String mixed = "Here is the answer: <\uFF5C\uFF5CDSML\uFF5C\uFF5C>";
        assert !AskReplyScrub.isToolSyntaxOnly(mixed) : "prose must not be tool-only";
        System.out.println("proseWithDsmlFragment OK");
    }

    private static void normalEnglish() {
        assert !AskReplyScrub.isToolSyntaxOnly("Craft with sticks and planks on a crafting table.")
                : "normal prose";
        System.out.println("normalEnglish OK");
    }

    private static void blankInput() {
        assert AskReplyScrub.isToolSyntaxOnly("");
        assert AskReplyScrub.isToolSyntaxOnly("   \n\t  ");
        System.out.println("blankInput OK");
    }

    /** Without the letter/digit gate, pure DSML must not classify as tool-only. */
    private static void negativeControlNoLetterDigitGate() {
        AskReplyScrub.testIgnoreNoLetterOrDigitGate = true;
        try {
            if (AskReplyScrub.isToolSyntaxOnly(AskDsmlLeakCheck.NFWC_LEAK)) {
                throw new AssertionError("negative control FAILED: gate removed but isToolSyntaxOnly still true");
            }
            System.out.println("negativeControlNoLetterDigitGate OK");
        } finally {
            AskReplyScrub.testIgnoreNoLetterOrDigitGate = false;
        }
    }
}

package com.skps9.packai.logic;

/** Final no-tools round after tool budget + zero prose. Run with -ea. */
public final class AskFinalRoundCheck {
    private AskFinalRoundCheck() {}

    public static void main(String[] args) {
        toolOnlyBudgetOnce();
        proseBlocksFinalRound();
        modelStoppedNoBudget();
        negativeControlWithoutNoProseGate();
        System.out.println("AskFinalRoundCheck OK");
    }

    /** 3 tool-only hops, budget exhausted → true once, then false. */
    private static void toolOnlyBudgetOnce() {
        AskFinalRound fr = new AskFinalRound();
        for (int i = 0; i < 3; i++) {
            fr.noteLlmContent("");
        }
        fr.markBudgetExhausted(AskFinalRound.Reason.ROUNDS_EXHAUSTED);
        assert fr.shouldFinalRound() : "first budget exhaust with no prose";
        fr.markFinalRoundAttempted();
        assert !fr.shouldFinalRound() : "must not re-enter";
        System.out.println("toolOnlyBudgetOnce OK");
    }

    private static void proseBlocksFinalRound() {
        AskFinalRound fr = new AskFinalRound();
        fr.noteLlmContent("");
        fr.noteLlmContent("Step one: use the anvil.");
        fr.markBudgetExhausted(AskFinalRound.Reason.ROUNDS_EXHAUSTED);
        assert !fr.shouldFinalRound() : "prose in an earlier hop blocks final round";
        System.out.println("proseBlocksFinalRound OK");
    }

    private static void modelStoppedNoBudget() {
        AskFinalRound fr = new AskFinalRound();
        fr.noteLlmContent("Done — craft with sticks and planks.");
        assert !fr.shouldFinalRound() : "normal stop without budget flag";
        System.out.println("modelStoppedNoBudget OK");
    }

    /**
     * Negative control: without the no-prose gate, scenario 2 would incorrectly allow final round.
     */
    private static void negativeControlWithoutNoProseGate() {
        AskFinalRound fr = new AskFinalRound();
        fr.testIgnoreNoProseGate = true;
        fr.noteLlmContent("Already answered.");
        fr.markBudgetExhausted(AskFinalRound.Reason.ROUNDS_EXHAUSTED);
        if (!fr.shouldFinalRound()) {
            throw new AssertionError("negative control FAILED: gate removed but shouldFinalRound still false");
        }
        System.out.println("negativeControlWithoutNoProseGate OK");
    }
}

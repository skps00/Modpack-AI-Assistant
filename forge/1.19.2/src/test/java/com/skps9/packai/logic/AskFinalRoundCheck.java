package com.skps9.packai.logic;

/** Final no-tools round after tool budget + zero prose. Run with -ea. */
public final class AskFinalRoundCheck {
    private AskFinalRoundCheck() {}

    public static void main(String[] args) {
        toolOnlyBudgetOnce();
        proseBlocksFinalRound();
        modelStoppedNoBudget();
        negativeControlWithoutNoProseGate();
        endedEmptyLoopOnce();
        proseBlocksEndedEmptyFinalRound();
        negativeControlWithoutEndedEmptyGate();
        negativeControlEndedEmptyWithProse();
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

    /** Loop ended empty without budget → true once, then false. */
    private static void endedEmptyLoopOnce() {
        AskFinalRound fr = new AskFinalRound();
        fr.markEndedEmpty();
        assert fr.shouldFinalRound() : "empty loop end with no prose";
        fr.markFinalRoundAttempted();
        assert !fr.shouldFinalRound() : "must not re-enter";
        System.out.println("endedEmptyLoopOnce OK");
    }

    private static void proseBlocksEndedEmptyFinalRound() {
        AskFinalRound fr = new AskFinalRound();
        fr.noteLlmContent("Here is how to craft it.");
        fr.markEndedEmpty();
        assert !fr.shouldFinalRound() : "prose blocks empty-end final round";
        System.out.println("proseBlocksEndedEmptyFinalRound OK");
    }

    /** Without ended-empty gate, empty-end path would still require no prose. */
    private static void negativeControlWithoutEndedEmptyGate() {
        AskFinalRound fr = new AskFinalRound();
        fr.testIgnoreEndedEmptyGate = true;
        fr.markEndedEmpty();
        if (!fr.shouldFinalRound()) {
            throw new AssertionError(
                    "negative control FAILED: empty gate removed but shouldFinalRound still false");
        }
        System.out.println("negativeControlWithoutEndedEmptyGate OK");
    }

    /** With prose, removing only the empty gate must not allow final round. */
    private static void negativeControlEndedEmptyWithProse() {
        AskFinalRound fr = new AskFinalRound();
        fr.testIgnoreEndedEmptyGate = true;
        fr.markEndedEmpty();
        fr.noteLlmContent("Already answered.");
        if (fr.shouldFinalRound()) {
            throw new AssertionError("negative control FAILED: prose present but shouldFinalRound true");
        }
        System.out.println("negativeControlEndedEmptyWithProse OK");
    }
}

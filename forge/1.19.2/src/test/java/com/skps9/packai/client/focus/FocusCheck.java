package com.skps9.packai.client.focus;

/**
 * Pure focus logic: miss→NONE, tracked gone/too-far, nearer-wins.
 * Run with -ea ({@code runFocusCheck}).
 */
public final class FocusCheck {
    private FocusCheck() {}

    public static void main(String[] args) {
        missMeansNone();
        trackedGone();
        trackedTooFar();
        entityFartherBlockWins();
        entityCloserEntityWins();
        System.out.println("FocusCheck OK");
    }

    /** MISS / no hits ⇒ NONE (mirror probe step 3 empty). */
    static void missMeansNone() {
        assert FocusTarget.none().kind() == FocusTarget.Kind.NONE;
        assert FocusProbe.pickWinner(false, 0, 4.5, false, 0) == FocusTarget.Kind.NONE;
        // Block beyond pick reach counts as no block target
        assert FocusProbe.pickWinner(true, 100.0, 4.5, false, 0) == FocusTarget.Kind.NONE;
        System.out.println("missMeansNone OK");
    }

    /** Entity missing ⇒ GONE. */
    static void trackedGone() {
        assert FocusProbe.trackedState(false, 3.0, 16.0) == FocusTarget.State.GONE;
        FocusTarget g = FocusTarget.gone("Zombie", 42, 5.0);
        assert g.state() == FocusTarget.State.GONE;
        assert g.kind() == FocusTarget.Kind.ENTITY;
        assert g.entityId() == 42;
        System.out.println("trackedGone OK");
    }

    /** Present but beyond range ⇒ TOO_FAR. */
    static void trackedTooFar() {
        assert FocusProbe.trackedState(true, 20.0, 16.0) == FocusTarget.State.TOO_FAR;
        assert FocusProbe.trackedState(true, 16.0, 16.0) == FocusTarget.State.OK;
        assert FocusProbe.trackedState(true, 15.9, 16.0) == FocusTarget.State.OK;
        FocusTarget t = FocusTarget.tooFar("Cow", 7, 22.0, 10f, 20f);
        assert t.state() == FocusTarget.State.TOO_FAR;
        assert t.distance() == 22.0;
        System.out.println("trackedTooFar OK");
    }

    /** Entity farther than block ⇒ BLOCK wins (vanilla nearer-wins). */
    static void entityFartherBlockWins() {
        // block at 3²=9, entity at 4²=16, pick=4.5 → BLOCK
        assert FocusProbe.pickWinner(true, 9.0, 4.5, true, 16.0) == FocusTarget.Kind.BLOCK;
        System.out.println("entityFartherBlockWins OK");
    }

    /** Entity closer than block ⇒ ENTITY wins. */
    static void entityCloserEntityWins() {
        assert FocusProbe.pickWinner(true, 16.0, 4.5, true, 9.0) == FocusTarget.Kind.ENTITY;
        // No block hit → entity wins if present
        assert FocusProbe.pickWinner(false, 0, 4.5, true, 25.0) == FocusTarget.Kind.ENTITY;
        System.out.println("entityCloserEntityWins OK");
    }
}

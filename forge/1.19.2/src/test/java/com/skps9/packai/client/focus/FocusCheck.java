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
        panelTwoTickCache();
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
        assert FocusProbe.trackedState(false, 0.0, 64.0) == FocusTarget.State.GONE;
        assert FocusProbe.trackedState(false, 100.0, 4.0) == FocusTarget.State.GONE;
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
        assert FocusProbe.trackedState(true, 4.0, 4.0) == FocusTarget.State.OK;
        assert FocusProbe.trackedState(true, 4.01, 4.0) == FocusTarget.State.TOO_FAR;
        assert FocusProbe.trackedState(true, 64.0, 64.0) == FocusTarget.State.OK;
        assert FocusProbe.trackedState(true, 64.01, 64.0) == FocusTarget.State.TOO_FAR;
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

    /** Panel cache: probe every 2 ticks; tracked id wins; one line key per state. */
    static void panelTwoTickCache() {
        assert !FocusProbe.panelProbeDue(0);
        assert !FocusProbe.panelProbeDue(1);
        assert FocusProbe.panelProbeDue(2);
        assert !FocusProbe.panelProbeDue(3);
        assert FocusProbe.panelProbeDue(4);
        assert FocusProbe.panelProbeDue(100);

        FocusTarget none = FocusTarget.none();
        FocusTarget tooFar = FocusTarget.tooFar("Cow", 7, 20.0, 10f, 20f);
        FocusTarget gone = FocusTarget.gone("Cow", 7, 4.0);
        FocusTarget shown = FocusProbe.panelShown(tooFar, none, tooFar, false);
        assert shown.state() == FocusTarget.State.TOO_FAR;
        assert "packai.focus.too_far".equals(FocusProbe.panelLineKey(shown));

        shown = FocusProbe.panelShown(gone, none, gone, false);
        assert shown.state() == FocusTarget.State.GONE;
        assert "packai.focus.entity_gone".equals(FocusProbe.panelLineKey(shown));
        assert "packai.focus.none".equals(FocusProbe.panelLineKey(none));
        assert "packai.focus.none".equals(FocusProbe.panelLineKey(null));

        // Held reselect keeps the previous target when there is no tracked entity.
        shown = FocusProbe.panelShown(tooFar, none, null, true);
        assert shown.state() == FocusTarget.State.TOO_FAR;
        // No hold and no resolve → fresh probe (none), not the stale entity.
        shown = FocusProbe.panelShown(tooFar, none, null, false);
        assert shown.kind() == FocusTarget.Kind.NONE;

        assert FocusProbe.panelSigChanged(none, tooFar);
        assert FocusProbe.panelSigChanged(tooFar, gone);
        assert !FocusProbe.panelSigChanged(tooFar, tooFar);
        assert !FocusProbe.panelSigChanged(none, none);
        System.out.println("panelTwoTickCache OK");
    }
}

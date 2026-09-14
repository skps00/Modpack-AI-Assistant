package com.skps9.packai.client.gui;

import java.util.List;
import java.util.Set;

/**
 * Headless: InvPick single-select helper. Run with -ea.
 */
public final class AskInvPickCheck {
    private AskInvPickCheck() {}

    public static void main(String[] args) {
        Set<String> emptyToA = InvPickScreen.applySinglePick(Set.of(), "A");
        assert emptyToA.equals(Set.of("A")) : emptyToA;

        Set<String> aToB = InvPickScreen.applySinglePick(Set.of("A"), "B");
        assert aToB.equals(Set.of("B")) : aToB;

        Set<String> deselect = InvPickScreen.applySinglePick(Set.of("A"), "A");
        assert deselect.isEmpty() : deselect;

        Set<String> nullToA = InvPickScreen.applySinglePick(null, "A");
        assert nullToA.equals(Set.of("A")) : nullToA;

        List<String> lastOfThree = InvPickScreen.trimPending(List.of("A","B","C"));
        assert lastOfThree.equals(List.of("C")) : lastOfThree;

        List<String> one = InvPickScreen.trimPending(List.of("A"));
        assert one.equals(List.of("A")) : one;

        List<String> empty = InvPickScreen.trimPending(List.of());
        assert empty.isEmpty() : empty;

        List<String> nil = InvPickScreen.trimPending(null);
        assert nil.isEmpty() : nil;

        System.out.println("AskInvPickCheck OK");
    }
}

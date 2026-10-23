package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import com.skps9.packai.client.gui.Fit;
import com.skps9.packai.client.gui.TreeCardLayout;

/** Headless checks for tree card layout and {@link Fit}. */
public final class TreeCardLayoutCheck {
    private TreeCardLayoutCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    private static void eq(int want, int got, String what) {
        if (want != got) {
            fail(what + ": want=" + want + " got=" + got);
        }
    }

    private static void eq(String want, String got, String what) {
        if (!want.equals(got)) {
            fail(what + ": want=\"" + want + "\" got=\"" + got + "\"");
        }
    }

    private static ToIntFunction<String> charWidth() {
        return String::length;
    }

    // R1: adjacent card gap >= MIN_CARD_GAP
    private static void caseAdjacentGap() {
        List<TreeCardLayout.Card> cards = List.of(
                new TreeCardLayout.Card(-1, 0, true, 2),
                new TreeCardLayout.Card(0, 1, false, 1),
                new TreeCardLayout.Card(0, 1, true, 3));
        TreeCardLayout.Result r = TreeCardLayout.layout(cards, 404, 200);
        for (int i = 0; i < r.rects().size() - 1; i++) {
            TreeCardLayout.Rect a = r.rects().get(i);
            TreeCardLayout.Rect b = r.rects().get(i + 1);
            int gap = b.y() - (a.y() + a.h());
            if (gap < TreeCardLayout.MIN_CARD_GAP) {
                fail("R1 gap at " + i + ": " + gap);
            }
        }
    }

    // R2: height(true,3)==77 and layout uses same height
    private static void caseHeightFormula() {
        eq(77, TreeCardLayout.height(true, 3), "R2 height(true,3)");
        List<TreeCardLayout.Card> cards =
                List.of(new TreeCardLayout.Card(-1, 0, true, 3));
        TreeCardLayout.Rect rect = TreeCardLayout.layout(cards, 320, 100).rects().get(0);
        eq(77, rect.h(), "R2 layout card height");
    }

    // R3: Fit.fit — fits / truncates / avail<=0
    private static void caseFit() {
        ToIntFunction<String> w = charWidth();
        eq("abc", Fit.fit("abc", 3, w), "R3 fits");
        eq("ab...", Fit.fit("abcdef", 5, w), "R3 trunc");
        eq("", Fit.fit("x", 0, w), "R3 zero avail");
        eq("", Fit.fit("hello", 2, w), "R3 ellipsis too wide");
    }

    // R4: contentWidth <= innerW for several inner widths
    private static void caseContentWidth() {
        List<TreeCardLayout.Card> cards = new ArrayList<>();
        for (int d = 0; d < 5; d++) {
            cards.add(new TreeCardLayout.Card(d - 1, d, d % 2 == 0, 2));
        }
        for (int innerW : new int[] {404, 320, 240, 200}) {
            TreeCardLayout.Result r = TreeCardLayout.layout(cards, innerW, 120);
            if (r.contentWidth() > innerW) {
                fail("R4 contentWidth " + r.contentWidth() + " > innerW " + innerW);
            }
        }
    }

    // R5: last row bottom == y+h-PAD (content height matches last card bottom - PAD semantics)
    private static void caseLastBottom() {
        List<TreeCardLayout.Card> cards = List.of(
                new TreeCardLayout.Card(-1, 0, false, 1),
                new TreeCardLayout.Card(0, 1, true, 2));
        TreeCardLayout.Result r = TreeCardLayout.layout(cards, 300, 80);
        TreeCardLayout.Rect last = r.rects().get(r.rects().size() - 1);
        eq(last.y() + last.h(), r.contentHeight(), "R5 last card bottom == contentHeight");
    }

    // R6: multi-card scroll metrics
    private static void caseScrollMetrics() {
        List<TreeCardLayout.Card> cards = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            cards.add(new TreeCardLayout.Card(i == 0 ? -1 : 0, i % 4, true, 3));
        }
        int viewH = 80;
        TreeCardLayout.Result r = TreeCardLayout.layout(cards, 360, viewH);
        int sumH = 0;
        for (TreeCardLayout.Card c : cards) {
            sumH += TreeCardLayout.height(c.hasMachine(), c.rows());
        }
        int expectedH = sumH + TreeCardLayout.CARD_GAP * (cards.size() - 1);
        eq(expectedH, r.contentHeight(), "R6 contentHeight formula");
        eq(Math.max(0, expectedH - viewH), r.maxScrollY(), "R6 maxScrollY");
        if (r.maxScrollY() <= 0) {
            fail("R6 expected positive maxScrollY");
        }
    }

    public static void main(String[] args) {
        caseAdjacentGap();
        caseHeightFormula();
        caseFit();
        caseContentWidth();
        caseLastBottom();
        caseScrollMetrics();
        System.out.println("check TreeCardLayoutCheck OK");
    }
}

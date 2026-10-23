package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import com.skps9.packai.client.gui.Fit;
import com.skps9.packai.client.gui.TreeCardLayout;

/** Headless overlap / bounds checks for tree card text geometry. */
public final class TreeOverlapCheck {
    private TreeOverlapCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    private static int w(String s) {
        return s.length() * 6;
    }

    private static ToIntFunction<String> widthFn() {
        return TreeOverlapCheck::w;
    }

    private static int cardWidthForInner(int innerW) {
        int maxIndent = Math.min(TreeCardLayout.INDENT_MAX, Math.max(0, innerW - TreeCardLayout.CARD_W_MIN));
        return Math.min(TreeCardLayout.CARD_W_MAX, innerW - maxIndent);
    }

    private static List<TreeCardLayout.Card> buildCards(int n) {
        int[] depths = {0, 3, 8};
        boolean[] machines = {false, true};
        int[] rowsOpts = {0, 1, 3, 8};
        List<TreeCardLayout.Card> cards = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int depth = depths[i % depths.length];
            boolean hasMachine = machines[i % machines.length];
            int rows = rowsOpts[i % rowsOpts.length];
            int parent = i == 0 ? -1 : 0;
            cards.add(new TreeCardLayout.Card(parent, depth, hasMachine, rows));
        }
        return cards;
    }

    private static void checkNameAbovePad() {
        if (TreeCardLayout.nameY() < TreeCardLayout.PAD) {
            fail("nameY=" + TreeCardLayout.nameY() + " < PAD=" + TreeCardLayout.PAD);
        }
    }

    private static void checkRowSeparation() {
        boolean[] machines = {false, true};
        int[] rowsOpts = {0, 1, 3, 8};
        for (boolean m : machines) {
            for (int rows : rowsOpts) {
                for (int i = 0; i < rows - 1; i++) {
                    int bottom = TreeCardLayout.rowY(m, i) + TreeCardLayout.TEXT_H;
                    int next = TreeCardLayout.rowY(m, i + 1);
                    if (bottom > next) {
                        fail("row overlap hasMachine=" + m + " rows=" + rows + " i=" + i
                                + " bottom=" + bottom + " nextBaseline=" + next);
                    }
                }
            }
        }
    }

    private static void checkMachineTaller() {
        int[] rowsOpts = {0, 1, 3, 8};
        for (int rows : rowsOpts) {
            int with = TreeCardLayout.height(true, rows);
            int without = TreeCardLayout.height(false, rows);
            if (with <= without) {
                fail("height(true," + rows + ")=" + with + " not > height(false," + rows + ")=" + without);
            }
        }
    }

    private static void checkFitNameWidths() {
        String longName = "x".repeat(120);
        int[] innerWs = {140, 180, 240, 320, 404, 800};
        int[] countWs = {0, 12, 30};
        for (int innerW : innerWs) {
            int cardW = cardWidthForInner(innerW);
            for (int countW : countWs) {
                int avail = TreeCardLayout.nameAvail(cardW, countW);
                String result = Fit.fit(longName, avail, widthFn());
                if (w(result) > avail) {
                    fail("Fit name too wide innerW=" + innerW + " cardW=" + cardW + " countW=" + countW
                            + " avail=" + avail + " w(result)=" + w(result));
                }
            }
        }
    }

    private static void scanLayouts() {
        int[] innerWs = {140, 180, 240, 320, 404, 800};
        int[] counts = {1, 2, 5, 40};
        for (int innerW : innerWs) {
            for (int n : counts) {
                List<TreeCardLayout.Card> cards = buildCards(n);
                TreeCardLayout.Result r = TreeCardLayout.layout(cards, innerW, 200);
                List<TreeCardLayout.Rect> rects = r.rects();
                if (rects.size() != n) {
                    fail("innerW=" + innerW + " n=" + n + " rect count " + rects.size());
                }
                for (int i = 0; i < rects.size(); i++) {
                    TreeCardLayout.Rect rect = rects.get(i);
                    TreeCardLayout.Card c = cards.get(i);
                    if (rect.x() < 0) {
                        fail("innerW=" + innerW + " n=" + n + " card=" + i + " x=" + rect.x() + " < 0");
                    }
                    if (rect.x() + rect.w() > innerW) {
                        fail("innerW=" + innerW + " n=" + n + " card=" + i + " right="
                                + (rect.x() + rect.w()) + " > innerW");
                    }
                    int bottom = TreeCardLayout.lastTextBottom(c.hasMachine(), c.rows());
                    if (bottom > rect.h() - 1) {
                        fail("text below card innerW=" + innerW + " n=" + n + " card=" + i
                                + " hasMachine=" + c.hasMachine() + " rows=" + c.rows()
                                + " lastTextBottom=" + bottom + " h-1=" + (rect.h() - 1));
                    }
                }
                for (int i = 0; i < rects.size() - 1; i++) {
                    TreeCardLayout.Rect a = rects.get(i);
                    TreeCardLayout.Rect b = rects.get(i + 1);
                    int gap = b.y() - (a.y() + a.h());
                    if (a.y() + a.h() > b.y()) {
                        fail("cards overlap innerW=" + innerW + " n=" + n + " at " + i);
                    }
                    if (gap < TreeCardLayout.MIN_CARD_GAP) {
                        fail("card gap innerW=" + innerW + " n=" + n + " at " + i + " gap=" + gap);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        checkNameAbovePad();
        checkRowSeparation();
        checkMachineTaller();
        checkFitNameWidths();
        scanLayouts();
        System.out.println("check TreeOverlapCheck OK");
    }
}

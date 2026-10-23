package com.skps9.packai.logic;

import com.skps9.packai.client.gui.RouteSummaryView;

/** Headless checks for {@link RouteSummaryView}. */
public final class RouteSummaryViewCheck {
    private RouteSummaryViewCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    private static void eq(int want, int got, String what) {
        if (want != got) {
            fail(what + ": want=" + want + " got=" + got);
        }
    }

    private static void ge(int got, int min, String what) {
        if (got < min) {
            fail(what + ": got=" + got + " want>=" + min);
        }
    }

    private static void caseColumnsWide() {
        RouteSummaryView.Columns c = RouteSummaryView.columns(10, 404);
        eq(10, c.leftX(), "leftX");
        eq(8, c.rightX() - c.leftX() - c.leftW(), "gap");
        eq(404, c.leftW() + 8 + c.rightW(), "total width");
        if (c.rightW() <= 0) {
            fail("wide layout should be two columns");
        }
    }

    private static void caseColumnsNarrow() {
        RouteSummaryView.Columns c = RouteSummaryView.columns(0, 200);
        eq(0, c.rightW(), "rightW single column");
        eq(200, c.leftW(), "leftW");
        eq(200, c.rightX(), "rightX");
    }

    private static void caseDrawerW() {
        eq(140, RouteSummaryView.drawerW(300), "drawerW(300)");
        eq(240, RouteSummaryView.drawerW(700), "drawerW(700)");
        ge(RouteSummaryView.drawerW(0), 0, "drawerW non-neg");
    }

    private static void caseBarH() {
        eq(22, RouteSummaryView.barH(false), "barH collapsed");
        eq(72, RouteSummaryView.barH(true), "barH expanded");
    }

    private static void caseContentHeightMonotonic() {
        int h0 = RouteSummaryView.contentHeight(0, 0);
        ge(h0, 0, "contentHeight(0,0)");
        int h1 = RouteSummaryView.contentHeight(1, 0);
        int h2 = RouteSummaryView.contentHeight(1, 1);
        int h3 = RouteSummaryView.contentHeight(5, 3);
        if (h1 < h0 || h2 < h1 || h3 < h2) {
            fail("contentHeight not monotonic: " + h0 + "," + h1 + "," + h2 + "," + h3);
        }
        ge(RouteSummaryView.contentHeight(-1, -2), 0, "negative inputs");
    }

    public static void main(String[] args) {
        caseColumnsWide();
        caseColumnsNarrow();
        caseDrawerW();
        caseBarH();
        caseContentHeightMonotonic();
        System.out.println("check RouteSummaryViewCheck OK");
    }
}

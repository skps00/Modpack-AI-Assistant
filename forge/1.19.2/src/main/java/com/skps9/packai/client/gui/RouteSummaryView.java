package com.skps9.packai.client.gui;

/** Layout metrics for route summary panels (pure math, no Minecraft). */
public final class RouteSummaryView {
    public static final int ROW_H = 12;
    public static final int HEADER_H = 14;
    public static final int MIN_W = 120;
    private static final int COLUMN_GAP = 8;

    private RouteSummaryView() {}

    public record Columns(int leftX, int leftW, int rightX, int rightW) {}

    public static Columns columns(int innerX, int innerW) {
        int w = Math.max(0, innerW);
        int x = Math.max(0, innerX);
        if (w < 2 * MIN_W + COLUMN_GAP) {
            return new Columns(x, w, x + w, 0);
        }
        int leftW = (w - COLUMN_GAP) / 2;
        int rightW = w - COLUMN_GAP - leftW;
        int rightX = x + leftW + COLUMN_GAP;
        return new Columns(x, leftW, rightX, rightW);
    }

    public static int contentHeight(int materials, int machines) {
        int m = Math.max(0, materials);
        int mc = Math.max(0, machines);
        int rows = Math.max(m, mc);
        if (m == 0 && mc == 0) {
            rows = 1;
        }
        return HEADER_H + HEADER_H + rows * ROW_H;
    }

    public static int drawerW(int innerW) {
        int raw = Math.max(0, innerW) * 45 / 100;
        return Math.min(240, Math.max(140, raw));
    }

    public static int barH(boolean expanded) {
        return expanded ? 72 : 22;
    }
}

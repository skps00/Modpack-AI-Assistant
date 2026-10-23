package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.List;

/** Geometry for obtain-route tree cards (no Minecraft dependencies). */
public final class TreeCardLayout {
    public static final int PAD = 4;
    public static final int CARD_W_MAX = 320;
    public static final int CARD_W_MIN = 180;
    public static final int INDENT_STEP = 10;
    public static final int INDENT_MAX = 40;
    public static final int CARD_GAP = 6;
    public static final int MIN_CARD_GAP = 1;
    public static final int ICON = 18;
    public static final int TEXT_H = 9;
    public static final int ROW_STEP = 11;

    private TreeCardLayout() {}

    /** Name text baseline y (relative to card top). */
    public static int nameY() {
        return 5;
    }

    /** Machine label baseline y (relative to card top). */
    public static int machineY() {
        return 23;
    }

    /** First requirement / fluid / status row baseline y (relative to card top). */
    public static int rowBaseY(boolean hasMachine) {
        return PAD + ICON + (hasMachine ? ICON : 0);
    }

    /** Row {@code i} text baseline y (relative to card top). */
    public static int rowY(boolean hasMachine, int i) {
        return rowBaseY(hasMachine) + i * ROW_STEP + 1;
    }

    /** Bottom y of last text row (relative to card top, exclusive). */
    public static int lastTextBottom(boolean hasMachine, int rows) {
        if (rows <= 0) {
            return hasMachine ? rowBaseY(true) : rowBaseY(false);
        }
        return rowY(hasMachine, rows - 1) + TEXT_H;
    }

    /** Horizontal space for name (icon and optional count on the right). */
    public static int nameAvail(int cardW, int countW) {
        return cardW - 26 - (countW > 0 ? countW + 4 : 0);
    }

    public record Card(int parentIndex, int depth, boolean hasMachine, int rows) {}

    public record Rect(int x, int y, int w, int h) {}

    public record Result(List<Rect> rects, int contentHeight, int contentWidth, int maxScrollY) {}

    public static int height(boolean hasMachine, int rows) {
        return PAD * 2 + ICON + (hasMachine ? ICON : 0) + ROW_STEP * rows;
    }

    public static Result layout(List<Card> cards, int innerW, int viewH) {
        int n = cards.size();
        if (n == 0) {
            return new Result(List.of(), 0, 0, 0);
        }
        int maxIndent = Math.min(INDENT_MAX, Math.max(0, innerW - CARD_W_MIN));
        int cardW = Math.min(CARD_W_MAX, innerW - maxIndent);
        List<Rect> rects = new ArrayList<>(n);
        int y = 0;
        for (int i = 0; i < n; i++) {
            Card c = cards.get(i);
            int x = Math.min(c.depth() * INDENT_STEP, maxIndent);
            int h = height(c.hasMachine(), c.rows());
            rects.add(new Rect(x, y, cardW, h));
            y += h;
            if (i < n - 1) {
                y += CARD_GAP;
            }
        }
        int contentHeight = y;
        int maxScrollY = Math.max(0, contentHeight - viewH);
        int contentWidth = maxIndent + cardW;
        return new Result(rects, contentHeight, contentWidth, maxScrollY);
    }
}

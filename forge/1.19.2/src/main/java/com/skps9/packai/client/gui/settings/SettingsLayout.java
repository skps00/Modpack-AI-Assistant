package com.skps9.packai.client.gui.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure geometry for Settings V2 (headless-testable). No Minecraft types.
 *
 * Height clauses (logical px, MC auto GUI scale):
 * <ul>
 *   <li>{@link #HEIGHT_MIN} 240 — tight; description is hover overlay</li>
 *   <li>{@link #HEIGHT_MED} 256 — still overlay (saves a row)</li>
 *   <li>{@link #HEIGHT_COMFORT} 270 — docked description panel</li>
 * </ul>
 * Overlay threshold: {@link #DESC_OVERLAY_BELOW_H} (260). Below {@link #HEIGHT_TOO_SMALL}
 * layout returns a documented fallback (empty rows, no crash).
 */
public final class SettingsLayout {
    public static final int HEIGHT_MIN = 240;
    public static final int HEIGHT_MED = 256;
    public static final int HEIGHT_COMFORT = 270;
    /** When height &lt; this, description becomes hover overlay (saves one row). */
    public static final int DESC_OVERLAY_BELOW_H = 260;
    /** Below this, return fallback layout (not a crash). */
    public static final int HEIGHT_TOO_SMALL = 160;
    public static final int WIDTH_TOO_SMALL = 200;

    public static final int ROW_H = 18;
    public static final int CAT_W = 88;
    public static final int PAD = 8;
    public static final int TOP_CHROME = 52;
    public static final int BOTTOM_CHROME = 28;
    public static final int SEARCH_H = 20;
    public static final int DESC_DOCK_H = 56;
    /** Matches {@code SettingsRegistry.UiCategory} length — keep in sync. */
    public static final int CATEGORY_COUNT = 7;

    public static final class Rect {
        public final int x;
        public final int y;
        public final int w;
        public final int h;

        public Rect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean overlaps(Rect o) {
            return x < o.right() && right() > o.x && y < o.bottom() && bottom() > o.y;
        }

        public boolean inside(int screenW, int screenH) {
            return x >= 0 && y >= 0 && right() <= screenW && bottom() <= screenH && w >= 0 && h >= 0;
        }
    }

    public static final class Layout {
        public final Rect categoryCol;
        public final Rect searchBox;
        public final Rect entryList;
        public final Rect descPanel;
        public final boolean descAsOverlay;
        public final boolean fallback;
        public final List<Rect> categoryRows;
        public final int maxVisibleEntryRows;

        Layout(
                Rect categoryCol,
                Rect searchBox,
                Rect entryList,
                Rect descPanel,
                boolean descAsOverlay,
                boolean fallback,
                List<Rect> categoryRows,
                int maxVisibleEntryRows) {
            this.categoryCol = categoryCol;
            this.searchBox = searchBox;
            this.entryList = entryList;
            this.descPanel = descPanel;
            this.descAsOverlay = descAsOverlay;
            this.fallback = fallback;
            this.categoryRows = categoryRows;
            this.maxVisibleEntryRows = maxVisibleEntryRows;
        }
    }

    private SettingsLayout() {}

    /**
     * Full three-column (+ optional docked description) geometry for screen size.
     */
    public static Layout compute(int screenW, int screenH) {
        if (screenW < WIDTH_TOO_SMALL || screenH < HEIGHT_TOO_SMALL) {
            Rect empty = new Rect(0, 0, Math.max(0, screenW), Math.max(0, screenH));
            return new Layout(empty, empty, empty, empty, true, true, List.of(), 0);
        }

        boolean overlay = screenH < DESC_OVERLAY_BELOW_H;
        int left = PAD;
        int top = TOP_CHROME;
        int bottomLimit = screenH - BOTTOM_CHROME;
        int descH = overlay ? 0 : DESC_DOCK_H;
        int contentBottom = bottomLimit - descH - (overlay ? 0 : PAD);

        Rect categoryCol = new Rect(left, top, CAT_W, Math.max(0, contentBottom - top));
        int listLeft = categoryCol.right() + PAD;
        int listW = Math.max(40, screenW - listLeft - PAD);
        Rect searchBox = new Rect(listLeft, top, listW, SEARCH_H);
        int listTop = searchBox.bottom() + 4;
        Rect entryList = new Rect(listLeft, listTop, listW, Math.max(0, contentBottom - listTop));

        Rect descPanel;
        if (overlay) {
            // Desc docks over list band; shrink list so last row stays above panel (Plan A D6).
            descPanel = new Rect(listLeft, Math.max(listTop, contentBottom - DESC_DOCK_H), listW, DESC_DOCK_H);
            int listH = Math.max(0, descPanel.y - 2 - listTop);
            entryList = new Rect(listLeft, listTop, listW, listH);
        } else {
            descPanel = new Rect(listLeft, contentBottom + PAD, listW, DESC_DOCK_H);
        }

        List<Rect> catRows = rowRects(categoryCol.w, categoryCol.h, CATEGORY_COUNT);
        // Offset category rows into categoryCol.
        List<Rect> placed = new ArrayList<>(catRows.size());
        for (Rect r : catRows) {
            placed.add(new Rect(categoryCol.x + r.x, categoryCol.y + r.y, r.w, r.h));
        }

        int maxRows = Math.max(0, entryList.h / ROW_H);
        return new Layout(categoryCol, searchBox, entryList, descPanel, overlay, false, placed, maxRows);
    }

    /**
     * Row rectangles inside a (0,0,w,h) local box. Used for entry list and category strip.
     * When {@code rows} do not fit, returns as many full rows as height allows (never overflow).
     */
    public static List<Rect> rowRects(int w, int h, int rows) {
        if (w <= 0 || h <= 0 || rows <= 0) {
            return List.of();
        }
        int fit = Math.min(rows, Math.max(0, h / ROW_H));
        if (fit == 0) {
            return List.of();
        }
        List<Rect> out = new ArrayList<>(fit);
        for (int i = 0; i < fit; i++) {
            out.add(new Rect(0, i * ROW_H, w, ROW_H));
        }
        return Collections.unmodifiableList(out);
    }

    /** Entry-list row rects in screen space for the first {@code visible} filtered rows. */
    public static List<Rect> entryRowRects(Layout layout, int visible) {
        if (layout == null || layout.fallback || visible <= 0) {
            return List.of();
        }
        int n = Math.min(visible, layout.maxVisibleEntryRows);
        List<Rect> local = rowRects(layout.entryList.w, layout.entryList.h, n);
        List<Rect> out = new ArrayList<>(local.size());
        for (Rect r : local) {
            out.add(new Rect(layout.entryList.x + r.x, layout.entryList.y + r.y, r.w, r.h));
        }
        return out;
    }
}

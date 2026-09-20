package com.skps9.packai.logic;

import java.util.List;

import com.skps9.packai.client.gui.settings.SettingsLayout;
import com.skps9.packai.client.gui.settings.SettingsLayout.Layout;
import com.skps9.packai.client.gui.settings.SettingsLayout.Rect;
import com.skps9.packai.client.gui.settings.SettingsRegistry;

/**
 * Headless: Settings V2 layout geometry + placeholder invariant. Run with -ea.
 * Layout math is Forge-free; placeholder check loads SettingsRegistry (Forge on harness CP).
 */
public final class SettingsLayoutCheck {
    private SettingsLayoutCheck() {}

    public static void main(String[] args) {
        assert SettingsLayout.CATEGORY_COUNT
                == SettingsRegistry.UiCategory.values().length
                : "CATEGORY_COUNT out of sync with UiCategory";
        matrixNoOverlapInBounds();
        heightClauseOverlay();
        tooSmallFallback();
        rowRectsConsistent();
        placeholderInvariant();
        System.out.println("SettingsLayoutCheck OK");
    }

    /** Plan §5: 240 / 256 overlay; 270 docked description. Plan A D6: shrink list under overlay. */
    static void heightClauseOverlay() {
        int w = 427;
        Layout h240 = SettingsLayout.compute(w, SettingsLayout.HEIGHT_MIN);
        assert !h240.fallback;
        assert h240.descAsOverlay : "240 must overlay";
        assert h240.descPanel.h == SettingsLayout.DESC_DOCK_H;
        assert h240.maxVisibleEntryRows == 4 : "240 rows=" + h240.maxVisibleEntryRows;
        assert lastRowBottom(h240) <= h240.descPanel.y - 2
                : "240 last row under desc: bottom=" + lastRowBottom(h240) + " descY=" + h240.descPanel.y;
        assert !h240.entryList.overlaps(h240.descPanel) : "240 list overlaps desc after D6";

        Layout h256 = SettingsLayout.compute(w, SettingsLayout.HEIGHT_MED);
        assert !h256.fallback;
        assert h256.descAsOverlay : "256 must overlay (<260)";
        assert h256.maxVisibleEntryRows == 5 : "256 rows=" + h256.maxVisibleEntryRows;
        assert lastRowBottom(h256) <= h256.descPanel.y - 2
                : "256 last row under desc: bottom=" + lastRowBottom(h256);
        assert !h256.entryList.overlaps(h256.descPanel) : "256 list overlaps desc after D6";
        assert h256.maxVisibleEntryRows >= h240.maxVisibleEntryRows
                : "256 should not lose rows vs 240";

        Layout h270 = SettingsLayout.compute(w, SettingsLayout.HEIGHT_COMFORT);
        assert !h270.fallback;
        assert !h270.descAsOverlay : "270 must dock description";
        assert !h270.descPanel.overlaps(h270.entryList) : "docked desc overlaps list";
        assert h270.descPanel.y >= h270.entryList.bottom();
        assert h270.maxVisibleEntryRows >= 4 : "270 rows=" + h270.maxVisibleEntryRows;

        Layout h276 = SettingsLayout.compute(w, 276);
        assert !h276.fallback;
        assert !h276.descAsOverlay : "276 must dock";
        assert !h276.descPanel.overlaps(h276.entryList);
        assert h276.maxVisibleEntryRows >= h270.maxVisibleEntryRows
                : "276 should not lose rows vs 270";

        assert SettingsLayout.DESC_OVERLAY_BELOW_H <= SettingsLayout.HEIGHT_COMFORT
                : "DESC_OVERLAY_BELOW_H must not rise past comfort (self-referential gate)";
        System.out.println("heightClauseOverlay OK");
    }

    static int lastRowBottom(Layout lay) {
        List<Rect> rows = SettingsLayout.entryRowRects(lay, lay.maxVisibleEntryRows);
        if (rows.isEmpty()) {
            return lay.entryList.y;
        }
        return rows.get(rows.size() - 1).bottom();
    }

    static void matrixNoOverlapInBounds() {
        int[] widths = {320, 427, 455, 480, 640};
        int[] heights = {
            SettingsLayout.HEIGHT_MIN,
            SettingsLayout.HEIGHT_MED,
            SettingsLayout.HEIGHT_COMFORT,
            360
        };
        for (int w : widths) {
            for (int h : heights) {
                Layout lay = SettingsLayout.compute(w, h);
                assert !lay.fallback : "unexpected fallback w=" + w + " h=" + h;
                assert lay.categoryCol.inside(w, h) : lay.categoryCol;
                assert lay.searchBox.inside(w, h) : lay.searchBox;
                assert lay.entryList.inside(w, h) : lay.entryList;
                assert lay.descPanel.inside(w, h) : lay.descPanel;
                assert !lay.categoryCol.overlaps(lay.entryList)
                        : "cat vs list overlap w=" + w + " h=" + h;
                assert !lay.searchBox.overlaps(lay.entryList)
                        : "search vs list overlap w=" + w + " h=" + h;
                if (!lay.descAsOverlay) {
                    assert !lay.descPanel.overlaps(lay.entryList)
                            : "desc vs list overlap w=" + w + " h=" + h;
                    assert h >= SettingsLayout.DESC_OVERLAY_BELOW_H
                            : "docked desc expected only when h>=" + SettingsLayout.DESC_OVERLAY_BELOW_H
                                    + " got " + h;
                } else {
                    assert h < SettingsLayout.DESC_OVERLAY_BELOW_H : "overlay expected when h<260 got " + h;
                }
                int expectedRows = Math.max(0, lay.entryList.h / SettingsLayout.ROW_H);
                assert lay.maxVisibleEntryRows == expectedRows
                        : "rows=" + lay.maxVisibleEntryRows + " expected=" + expectedRows;
                List<Rect> rows = SettingsLayout.entryRowRects(lay, expectedRows + 5);
                assert rows.size() == expectedRows : rows.size();
                for (Rect r : rows) {
                    assert r.inside(w, h) : r;
                    assert r.y >= lay.entryList.y && r.bottom() <= lay.entryList.bottom() : r;
                }
                for (int i = 0; i < rows.size(); i++) {
                    for (int j = i + 1; j < rows.size(); j++) {
                        assert !rows.get(i).overlaps(rows.get(j)) : i + " vs " + j;
                    }
                }
            }
        }
        System.out.println("matrixNoOverlapInBounds OK");
    }

    static void tooSmallFallback() {
        Layout tiny = SettingsLayout.compute(100, 100);
        assert tiny.fallback : "too-small must fallback";
        assert tiny.maxVisibleEntryRows == 0 : tiny.maxVisibleEntryRows;
        assert SettingsLayout.entryRowRects(tiny, 10).isEmpty();
        Layout zero = SettingsLayout.compute(0, 0);
        assert zero.fallback;
        System.out.println("tooSmallFallback OK");
    }

    static void rowRectsConsistent() {
        List<Rect> none = SettingsLayout.rowRects(200, 10, 5);
        assert none.isEmpty() : "h<ROW_H → no rows";
        List<Rect> three = SettingsLayout.rowRects(200, SettingsLayout.ROW_H * 3, 10);
        assert three.size() == 3 : three.size();
        assert three.get(0).y == 0;
        assert three.get(1).y == SettingsLayout.ROW_H;
        assert !three.get(0).overlaps(three.get(1));
        System.out.println("rowRectsConsistent OK");
    }

    static void placeholderInvariant() {
        // Mirror SettingsRegistry.isPlaceholderValue without loading Forge config SPEC.
        assert isPlaceholder("•••");
        assert isPlaceholder("已設定");
        assert isPlaceholder("已设置");
        assert isPlaceholder("Configured");
        assert isPlaceholder("...");
        assert !isPlaceholder("sk-real-key");
        assert !isPlaceholder("");
        assert !isPlaceholder(null);
        System.out.println("placeholderInvariant OK");
    }

    /** Keep in sync with SettingsRegistry.isPlaceholderValue. */
    static boolean isPlaceholder(String raw) {
        return SettingsRegistry.isPlaceholderValue(raw);
    }
}

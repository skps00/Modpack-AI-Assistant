package com.skps9.packai.client.gui;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Headless check for {@link RouteTextWrap} — the wrapping used by the route-library detail pane.
 *
 * <p>Inputs are the real {@code packai.route_library.preview.step} strings that the saved-routes
 * screen renders: the product/machine/step-number prefix is the one visible in the clipped
 * screenshot rows ({@code "#1  [Crafting Table]  Andesite Alloy ← ..."}), and the ingredient list is
 * the pack's own recipe for that product ({@code create:crafting/materials/andesite_alloy} in
 * create-1.19.2-0.5.1.f.jar: 2x andesite + 2x iron nugget).
 *
 * <p>Width function: the real screen passes {@code font::width}. Headless there is no {@code Font},
 * so we use a stand-in with the same shape — 6px per glyph, 4px per space — which is within a pixel
 * of Minecraft's default 9pt ASCII metrics. The pane width comes from the screen's own layout maths:
 * a 1920x1080 window at GUI scale 3 gives {@code this.width == 640}, {@code listWidth == 280},
 * {@code previewWidth == 336}, so step text gets {@code previewWidth - 8 == 328} px.
 *
 * <p>Run: {@code java -cp <classes> com.skps9.packai.client.gui.RouteLibraryWrapCheck}
 */
public final class RouteLibraryWrapCheck {
    /** {@code this.previewWidth - 8} for a 640-wide (GUI-scale-3) window. */
    private static final int STEP_WIDTH = 328;
    /** {@code this.listWidth - 8} for the same window. */
    private static final int LIST_WIDTH = 272;
    /** Minecraft's default font line height. */
    private static final int LINE_H = 9;
    /** {@code RouteLibraryScreen.PREVIEW_ROW_PAD}. */
    private static final int ROW_PAD = 9;

    /** Stand-in for {@code Font::width}: 6px per glyph, 4px per space. */
    private static final ToIntFunction<String> FONT = s -> {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += s.charAt(i) == ' ' ? 4 : 6;
        }
        return w;
    };

    /** Screenshot row 1, reconstructed: the tail the player could not read. */
    private static final String ANDESITE_STEP =
            "#1  [Crafting Table]  Andesite Alloy ← Andesite, Iron Nugget, Andesite, Iron Nugget";
    /** Short step that already fit the pane and must not change. */
    private static final String SHORT_STEP = "#2  [Milling]  Clay Ball ← Clay";
    /** Step long enough that a 3rd line would be needed. */
    private static final String THREE_LINE_STEP = ANDESITE_STEP
            + ", Zinc Ingot, Zinc Ingot, Andesite, Iron Nugget";

    private RouteLibraryWrapCheck() {}

    public static void main(String[] args) {
        checkAndesiteStepWrapsToTwoFullLines();
        checkShortStepUnchanged();
        checkNoLineExceedsWidth();
        checkNegativeControlSingleLineFails();
        checkThreeLineStepGetsEllipsis();
        checkRowHeightFollowsWrappedLines();
        checkListLabelDropsTimestampThenSteps();
        System.out.println("RouteLibraryWrapCheck OK");
    }

    /** (a) the callee that mattered: the Andesite Alloy step becomes 2 lines holding every ingredient. */
    private static void checkAndesiteStepWrapsToTwoFullLines() {
        List<String> lines = RouteTextWrap.wrapLines(ANDESITE_STEP, STEP_WIDTH, FONT, 2);
        assert lines.size() == 2
                : "(a) Andesite step must wrap to 2 lines, got " + lines.size() + " -> " + lines;
        assert containsFullText(lines, ANDESITE_STEP)
                : "(a) the 2 lines must contain the whole step text (no lost ingredients): " + lines;
        for (String ingredient : new String[] {"Andesite Alloy", "Andesite", "Iron Nugget"}) {
            assert normalized(String.join(" ", lines)).contains(ingredient)
                    : "(a) missing ingredient '" + ingredient + "' in " + lines;
        }
        assert !normalized(String.join(" ", lines)).contains(RouteTextWrap.ELLIPSIS)
                : "(a) text that fits 2 lines must not be ellipsised: " + lines;
    }

    /** (b) a short step stays one line, byte for byte. */
    private static void checkShortStepUnchanged() {
        List<String> lines = RouteTextWrap.wrapLines(SHORT_STEP, STEP_WIDTH, FONT, 2);
        assert lines.size() == 1 : "(b) short step must stay 1 line, got " + lines;
        assert SHORT_STEP.equals(lines.get(0)) : "(b) short step must be unchanged, got " + lines;
    }

    /** (c) nothing may ever be drawn past the pane edge. */
    private static void checkNoLineExceedsWidth() {
        String[] all = {ANDESITE_STEP, SHORT_STEP, THREE_LINE_STEP};
        for (String text : all) {
            for (int cap : new int[] {1, 2, 3, Integer.MAX_VALUE}) {
                List<String> lines = RouteTextWrap.wrapLines(text, STEP_WIDTH, FONT, cap);
                assert !lines.isEmpty() : "(c) no lines for " + text;
                assert lines.size() <= Math.max(1, cap) : "(c) cap " + cap + " exceeded: " + lines;
                for (String line : lines) {
                    assert FONT.applyAsInt(line) <= STEP_WIDTH
                            : "(c) line '" + line + "' is " + FONT.applyAsInt(line)
                                    + "px, pane is " + STEP_WIDTH + "px";
                }
            }
        }
        // Degenerate inputs must not crash or emit an over-wide line.
        assert RouteTextWrap.wrapLines(null, STEP_WIDTH, FONT, 2).isEmpty() : "(c) null must be empty";
        assert RouteTextWrap.wrapLines("", STEP_WIDTH, FONT, 2).isEmpty() : "(c) empty must be empty";
        List<String> narrow = RouteTextWrap.wrapLines(ANDESITE_STEP, 12, FONT, 2);
        for (String line : narrow) {
            assert FONT.applyAsInt(line) <= 12 : "(c) narrow pane overflow: '" + line + "'";
        }
    }

    /** (d) negative control: the old one-line behaviour must FAIL (a), or the check proves nothing. */
    private static void checkNegativeControlSingleLineFails() {
        List<String> single = RouteTextWrap.wrapLines(ANDESITE_STEP, STEP_WIDTH, FONT, 1);
        assert single.size() == 1 : "(d) cap=1 must give one line, got " + single;
        assert !containsFullText(single, ANDESITE_STEP)
                : "(d) single line must NOT contain the full text — the check would be toothless: "
                        + single;
        assert single.get(0).endsWith(RouteTextWrap.ELLIPSIS)
                : "(d) single truncated line must end with an ellipsis, got " + single;
        // And the legacy behaviour (plainSubstrByWidth + "...") fails the same way.
        String legacy = Fit.fit(ANDESITE_STEP, STEP_WIDTH, FONT);
        assert !normalized(legacy).equals(normalized(ANDESITE_STEP))
                : "(d) legacy truncation must lose text, got " + legacy;
        assert legacy.endsWith("...") : "(d) legacy truncation must be ellipsised: " + legacy;
        assert !containsFullText(List.of(legacy), ANDESITE_STEP)
                : "(d) legacy truncation must fail (a): " + legacy;
    }

    /** Overflow past 2 lines collapses into line 2 with an ellipsis, still inside the pane. */
    private static void checkThreeLineStepGetsEllipsis() {
        List<String> lines = RouteTextWrap.wrapLines(THREE_LINE_STEP, STEP_WIDTH, FONT, 2);
        assert lines.size() == 2 : "3-line step must be capped at 2 lines, got " + lines;
        assert lines.get(1).endsWith(RouteTextWrap.ELLIPSIS)
                : "capped line must carry the trailing ellipsis, got " + lines;
        assert !containsFullText(lines, THREE_LINE_STEP) : "capped text cannot contain everything";
        for (String line : lines) {
            assert FONT.applyAsInt(line) <= STEP_WIDTH : "capped line overflows: '" + line + "'";
        }
    }

    /** Row height must grow with the wrapped line count, else later rows overlap. */
    private static void checkRowHeightFollowsWrappedLines() {
        int one = RouteTextWrap.rowHeight(1, LINE_H, ROW_PAD);
        int two = RouteTextWrap.rowHeight(2, LINE_H, ROW_PAD);
        assert one == LINE_H + ROW_PAD : "1-line row height, got " + one;
        assert two == 2 * LINE_H + ROW_PAD : "2-line row height, got " + two;
        assert two - one == LINE_H : "each extra line must add exactly one lineHeight";
        assert RouteTextWrap.rowHeight(0, LINE_H, ROW_PAD) == ROW_PAD : "0-line row height";
        assert RouteTextWrap.rowHeight(-3, LINE_H, ROW_PAD) == ROW_PAD : "negative line count";
    }

    /** Left list: full label if it fits, else drop the timestamp, else drop the step count. */
    private static void checkListLabelDropsTimestampThenSteps() {
        // Real row from the saved-route store (ae2:certus_quartz_crystal, 3 steps) — the display
        // name is long enough that the timestamp is what gets clipped in the screenshot.
        String name = "Certus Quartz Crystal";
        String time = "2026-10-22 16:13";
        String full = name + " · " + time + " · 3 steps";
        String noTime = name + " · 3 steps";
        String nameOnly = name;
        List<String> candidates = List.of(full, noTime, nameOnly);

        assert FONT.applyAsInt(full) > LIST_WIDTH : "fixture must overflow the list row (premise)";
        String picked = RouteTextWrap.pickListLabel(candidates, LIST_WIDTH, FONT);
        assert picked.equals(noTime)
                : "must drop the timestamp first and keep the step count, got '" + picked + "'";
        assert !picked.contains(time) : "timestamp still present: " + picked;

        // Too narrow even without the timestamp: drop the step count, then ellipsise the name.
        int veryNarrow = FONT.applyAsInt(noTime) - 1;
        String picked2 = RouteTextWrap.pickListLabel(candidates, veryNarrow, FONT);
        assert picked2.startsWith(name) || picked2.startsWith("Andesite")
                : "name must survive the drop, got '" + picked2 + "'";
        assert !picked2.contains("steps") : "step count must be dropped before the name: " + picked2;

        // Narrower still: name + ellipsis, never a mid-word clip without the marker.
        int tiny = 40;
        String picked3 = RouteTextWrap.pickListLabel(candidates, tiny, FONT);
        assert picked3.endsWith("...") : "over-wide name must be ellipsised, got '" + picked3 + "'";
        assert FONT.applyAsInt(picked3) <= tiny : "ellipsised label overflows: '" + picked3 + "'";

        // Fits outright: unchanged.
        assert RouteTextWrap.pickListLabel(candidates, 400, FONT).equals(full)
                : "label that fits must be untouched";
        assert RouteTextWrap.pickListLabel(List.of(), LIST_WIDTH, FONT).isEmpty() : "empty input";
    }

    private static boolean containsFullText(List<String> lines, String text) {
        return normalized(String.join(" ", lines)).equals(normalized(text));
    }

    /** Collapse runs of whitespace so wrap-point spacing does not affect equality. */
    private static String normalized(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }
}

package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Pure text-layout helpers for {@link RouteLibraryScreen}.
 *
 * <p>Minecraft's GUI font has no automatic wrapping, so long step lines and list rows have to be
 * laid out by hand. Everything here is deliberately free of Minecraft types: the caller passes a
 * "font-like" width function (in game: {@code this.font::width}), which keeps the logic unit
 * testable headlessly in {@code RouteLibraryWrapCheck}.
 */
public final class RouteTextWrap {
    /** Same ellipsis {@link Fit} appends, so the two helpers agree visually. */
    public static final String ELLIPSIS = "...";

    private RouteTextWrap() {}

    /** Wrap without a line cap (every line fits {@code widthPx}). */
    public static List<String> wrapLines(String text, int widthPx, ToIntFunction<String> widthOf) {
        return wrapLines(text, widthPx, widthOf, Integer.MAX_VALUE);
    }

    /**
     * Greedy word wrap that never exceeds {@code widthPx} and never returns more than
     * {@code maxLines} lines.
     *
     * <p>Line breaks happen on spaces when possible; a single word wider than the pane is hard-cut
     * on a code-point boundary. When the text needs more than {@code maxLines} lines the trailing
     * content is collapsed into the last line and shortened with {@link Fit} (so the final line gets
     * a trailing {@code "..."} and still fits). Text that fits in {@code maxLines} lines is returned
     * verbatim — no ellipsis is ever added to text that already fits.
     */
    public static List<String> wrapLines(String text, int widthPx, ToIntFunction<String> widthOf,
            int maxLines) {
        if (text == null || text.isEmpty() || widthPx <= 0 || widthOf == null) {
            return List.of();
        }
        int cap = Math.max(1, maxLines);
        List<String> out = new ArrayList<>();
        int n = text.length();
        int start = 0;
        while (start < n) {
            while (start < n && text.charAt(start) == ' ') {
                start++;
            }
            if (start >= n) {
                break;
            }
            String rest = text.substring(start);
            if (widthOf.applyAsInt(rest) <= widthPx) {
                out.add(rest);
                break;
            }
            int end = maxPrefixEnd(text, start, widthPx, widthOf);
            if (end <= start) {
                // Single glyph wider than the pane: emit it anyway so we always advance.
                end = text.offsetByCodePoints(start, 1);
            }
            int brk = lastSpaceBefore(text, start, end);
            if (brk > start) {
                out.add(stripTrailing(text.substring(start, brk)));
                start = brk;
            } else {
                out.add(text.substring(start, end));
                start = end;
            }
        }
        if (out.size() > cap) {
            String tail = String.join(" ", out.subList(cap - 1, out.size()));
            List<String> cut = new ArrayList<>(out.subList(0, cap - 1));
            cut.add(Fit.fit(tail, widthPx, widthOf));
            return List.copyOf(cut);
        }
        return List.copyOf(out);
    }

    /**
     * Height of one preview row for {@code lineCount} wrapped lines.
     *
     * <p>Rows must be sized from their own wrapped line count: a fixed row height is what let a
     * two-line step overlap the row below it.
     */
    public static int rowHeight(int lineCount, int lineHeightPx, int padPx) {
        return Math.max(0, lineCount) * lineHeightPx + padPx;
    }

    /**
     * Progressive list label: returns the first candidate that fits, else the last candidate
     * shortened with an ellipsis.
     *
     * <p>Callers pass widest-first candidates (name + time + steps, name + steps, name only), so a
     * narrow list drops the timestamp before the step count and always keeps the item name visible.
     */
    public static String pickListLabel(List<String> candidates, int widthPx,
            ToIntFunction<String> widthOf) {
        if (candidates == null || candidates.isEmpty() || widthOf == null) {
            return "";
        }
        for (String candidate : candidates) {
            if (candidate != null && widthOf.applyAsInt(candidate) <= widthPx) {
                return candidate;
            }
        }
        String last = candidates.get(candidates.size() - 1);
        return Fit.fit(last == null ? "" : last, widthPx, widthOf);
    }

    /** End index (exclusive) of the widest prefix of {@code text[start..]} that fits. */
    private static int maxPrefixEnd(String text, int start, int widthPx,
            ToIntFunction<String> widthOf) {
        int cpCount = text.codePointCount(start, text.length());
        int[] cuts = new int[cpCount + 1];
        cuts[0] = start;
        for (int i = 1; i <= cpCount; i++) {
            cuts[i] = text.offsetByCodePoints(start, i);
        }
        int lo = 0;
        int hi = cpCount;
        int best = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (widthOf.applyAsInt(text.substring(start, cuts[mid])) <= widthPx) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return cuts[best];
    }

    private static int lastSpaceBefore(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            if (text.charAt(i) == ' ') {
                return i;
            }
        }
        return -1;
    }

    private static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }
}

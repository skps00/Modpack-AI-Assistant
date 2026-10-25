package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Word-aware wrap for assistant answer text. Breaks on whitespace and punctuation;
 * hard-splits only when a single segment exceeds the pane width.
 */
public final class Wrap {
    private Wrap() {}

    /** Greedy wrap: every returned line fits {@code widthPx} (when {@code widthOf} matches game font). */
    public static List<String> textLines(String s, int widthPx, ToIntFunction<String> widthOf) {
        if (s == null || s.isEmpty() || widthPx <= 0 || widthOf == null) {
            return List.of();
        }
        List<String> segments = segments(s);
        if (segments.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int lineW = 0;
        for (int i = 0; i < segments.size(); i++) {
            String seg = segments.get(i);
            if (seg.isEmpty()) {
                continue;
            }
            boolean wordBoundary = i > 0 && !isPunctuation(seg.charAt(0));
            int gap = wordBoundary && line.length() > 0 ? widthOf.applyAsInt(" ") : 0;
            int segW = widthOf.applyAsInt(seg);
            if (line.length() > 0 && lineW + gap + segW > widthPx) {
                lines.add(line.toString());
                line.setLength(0);
                lineW = 0;
                gap = 0;
            }
            if (segW > widthPx) {
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                    lineW = 0;
                }
                for (String part : hardSplitSegment(seg, widthPx, widthOf)) {
                    lines.add(part);
                }
                continue;
            }
            if (line.length() > 0 && gap > 0) {
                line.append(' ');
                lineW += gap;
            }
            line.append(seg);
            lineW += segW;
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return List.copyOf(lines);
    }

    /**
     * Legacy character-greedy wrap (pre-AK). Negative-control only — splits inside tokens.
     */
    public static List<String> textLinesCharSplit(String s, int widthPx, ToIntFunction<String> widthOf) {
        if (s == null || s.isEmpty() || widthPx <= 0 || widthOf == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        int n = s.length();
        int start = 0;
        while (start < n) {
            int fit = 0;
            while (start + fit < n) {
                int tw = widthOf.applyAsInt(s.substring(start, start + fit + 1));
                if (tw > widthPx && fit > 0) {
                    break;
                }
                if (tw > widthPx && fit == 0) {
                    fit = 1;
                    break;
                }
                fit++;
            }
            if (fit == 0) {
                fit = 1;
            }
            lines.add(s.substring(start, start + fit));
            start += fit;
        }
        return List.copyOf(lines);
    }

    /** Whitespace tokens further split at punctuation wrap points. */
    static List<String> segments(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            while (i < n && isWhitespace(s.charAt(i))) {
                i++;
            }
            if (i >= n) {
                break;
            }
            int start = i;
            while (i < n && !isWhitespace(s.charAt(i))) {
                i++;
            }
            splitWordAtPunctuation(s.substring(start, i), out);
        }
        return out;
    }

    private static void splitWordAtPunctuation(String word, List<String> out) {
        if (word.isEmpty()) {
            return;
        }
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (isPunctuation(c)) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                out.add(String.valueOf(c));
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
    }

    private static List<String> hardSplitSegment(String seg, int widthPx, ToIntFunction<String> widthOf) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int n = seg.length();
        while (start < n) {
            int end = maxPrefixEnd(seg, start, widthPx, widthOf);
            if (end <= start) {
                end = seg.offsetByCodePoints(start, 1);
            }
            parts.add(seg.substring(start, end));
            start = end;
        }
        return parts;
    }

    private static int maxPrefixEnd(String text, int start, int widthPx, ToIntFunction<String> widthOf) {
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

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private static boolean isPunctuation(char c) {
        return ";:,.)]}>\"'".indexOf(c) >= 0 || "([{<\"'".indexOf(c) >= 0;
    }
}

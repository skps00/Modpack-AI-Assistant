package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
/**
 * Final display body vs recipe cards: drop denial lines when cards exist; strip orphan
 * section headings on miss/fallback/clarify bodies.
 */
public final class AskBodyCardConsistency {
    private AskBodyCardConsistency() {}

    /**
     * @param fallbackOrClarify when true, orphan {@code how to get/use} heading lines are removed
     */
    public static String reconcile(String body, String lang, int cardsOut, boolean fallbackOrClarify) {
        if (body == null || body.isEmpty()) {
            return body == null ? "" : body;
        }
        String out = swapObtainUnknownWhenCards(body, lang, cardsOut);
        if (fallbackOrClarify) {
            out = stripOrphanSectionHeadings(out, lang);
        }
        return out;
    }

    /**
     * Harness-only: swaps denial lines even when {@code cardsOut == 0} (negative-control guard).
     */
    static String harnessSwapDenialRegardlessOfCards(String body, String lang) {
        return swapObtainUnknownWhenCards(body, lang, Integer.MAX_VALUE);
    }

    static String swapObtainUnknownWhenCards(String body, String lang, int cardsOut) {
        if (body == null || body.isEmpty() || cardsOut <= 0) {
            return body == null ? "" : body;
        }
        String unknown = ReplyLang.obtainUnknown(lang);
        String below = ReplyLang.cardsBelowOnly(lang);
        if (unknown == null || unknown.isBlank() || below == null || below.isBlank()) {
            return body;
        }
        String unknownTrim = unknown.trim();
        String belowTrim = below.trim();
        StringBuilder out = new StringBuilder(body.length());
        int i = 0;
        int n = body.length();
        boolean changed = false;
        while (i <= n) {
            int lineEnd = i;
            while (lineEnd < n) {
                char c = body.charAt(lineEnd);
                if (c == '\n' || c == '\r') {
                    break;
                }
                lineEnd++;
            }
            String line = body.substring(i, lineEnd);
            String trimmed = line.strip();
            int next = lineBreakEnd(body, lineEnd, n);
            if (isObtainUnknownSentenceLine(trimmed, unknownTrim)) {
                changed = true;
                String indent = leadingWhitespace(line);
                out.append(indent).append(belowTrim);
                if (next <= n) {
                    out.append(body, lineEnd, next);
                }
            } else {
                out.append(line);
                if (next <= n) {
                    out.append(body, lineEnd, next);
                }
            }
            if (next > n) {
                break;
            }
            i = next;
        }
        return changed ? out.toString() : body;
    }

    static boolean isObtainUnknownSentenceLine(String trimmed, String unknownTrim) {
        if (trimmed == null || trimmed.isEmpty() || unknownTrim == null || unknownTrim.isEmpty()) {
            return false;
        }
        if (trimmed.equals(unknownTrim)) {
            return true;
        }
        if (unknownTrim.endsWith(".") && trimmed.equals(unknownTrim)) {
            return true;
        }
        if (trimmed.endsWith(".") && trimmed.equals(unknownTrim)) {
            return true;
        }
        if (unknownTrim.endsWith(".") && trimmed.endsWith(".") && trimmed.equals(unknownTrim)) {
            return true;
        }
        return false;
    }

    static String stripOrphanSectionHeadings(String body, String lang) {
        if (body == null || body.isEmpty()) {
            return body == null ? "" : body;
        }
        List<String> headings = structuralHeadingForms(lang);
        StringBuilder out = new StringBuilder(body.length());
        int i = 0;
        int n = body.length();
        boolean dropped = false;
        while (i <= n) {
            int lineEnd = i;
            while (lineEnd < n) {
                char c = body.charAt(lineEnd);
                if (c == '\n' || c == '\r') {
                    break;
                }
                lineEnd++;
            }
            String line = body.substring(i, lineEnd);
            String trimmed = line.strip();
            int next = lineBreakEnd(body, lineEnd, n);
            if (isStructuralHeadingLine(trimmed, headings)) {
                dropped = true;
                next = skipOneBlankLine(body, next, n);
            } else {
                out.append(line);
                if (next <= n) {
                    out.append(body, lineEnd, next);
                }
            }
            if (next > n) {
                break;
            }
            i = next;
        }
        if (!dropped) {
            return body;
        }
        String trimmed = out.toString().strip();
        return trimmed.isEmpty() ? body.strip() : out.toString().stripTrailing();
    }

    static boolean isStructuralHeadingLine(String trimmed, List<String> headings) {
        if (trimmed == null || trimmed.isEmpty()) {
            return false;
        }
        if (matchesHeading(trimmed, headings)) {
            return true;
        }
        String stripped = trimmed
                .replaceFirst("(?i)^#{1,3}[ \\t]*", "")
                .replaceFirst("^\\d+[.)][ \\t]*", "")
                .strip();
        return matchesHeading(stripped, headings);
    }

    private static boolean matchesHeading(String line, List<String> headings) {
        for (String h : headings) {
            if (h == null || h.isEmpty()) {
                continue;
            }
            if (line.equals(h)) {
                return true;
            }
            if (line.equalsIgnoreCase(h)) {
                return true;
            }
        }
        return false;
    }

    static List<String> structuralHeadingForms(String lang) {
        List<String> out = new ArrayList<>();
        addHeadingVariants(out, ReplyLang.sectionHowToGet(lang));
        addHeadingVariants(out, ReplyLang.sectionHowToUse(lang));
        return List.copyOf(out);
    }

    private static void addHeadingVariants(List<String> out, String section) {
        if (section == null || section.isBlank()) {
            return;
        }
        String s = section.strip();
        out.add(s);
        String bare = s.replaceFirst("^#{1,3}[ \\t]*", "").strip();
        if (!bare.isEmpty()) {
            out.add(bare);
            out.add(bare + ":");
            out.add(bare + "：");
        }
    }

    static int countSentences(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int count = 0;
        boolean in = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                in = true;
            }
            if (in && isSentenceEnd(c)) {
                count++;
                in = false;
            }
        }
        if (in) {
            count++;
        }
        return count;
    }

    private static boolean isSentenceEnd(char c) {
        return c == '.' || c == '!' || c == '?' || c == '。' || c == '！' || c == '？';
    }

    private static String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }

    private static int lineBreakEnd(String body, int lineEnd, int n) {
        if (lineEnd >= n) {
            return n + 1;
        }
        if (body.charAt(lineEnd) == '\r' && lineEnd + 1 < n && body.charAt(lineEnd + 1) == '\n') {
            return lineEnd + 2;
        }
        return lineEnd + 1;
    }

    private static int skipOneBlankLine(String body, int start, int n) {
        int i = start;
        while (i < n && (body.charAt(i) == ' ' || body.charAt(i) == '\t' || body.charAt(i) == '\u3000')) {
            i++;
        }
        if (i >= n) {
            return i;
        }
        if (body.charAt(i) == '\r') {
            if (i + 1 < n && body.charAt(i + 1) == '\n') {
                return i + 2;
            }
            return i + 1;
        }
        if (body.charAt(i) == '\n') {
            return i + 1;
        }
        return start;
    }
}

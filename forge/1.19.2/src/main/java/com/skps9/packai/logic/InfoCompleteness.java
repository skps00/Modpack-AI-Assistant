package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Append already-human gap lines before the sources footer.
 * Empty gaps return the same string reference. No Minecraft types. No config.
 */
public final class InfoCompleteness {
    /** {@code ns:path} tokens. Any hit in the answer means that gap line is covered. */
    static final Pattern NS_PATH = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    /** Colon-free path runs. {@code :} is outside the class, so {@code ns:path} stays on {@link #NS_PATH}. */
    private static final Pattern PATH_RUN = Pattern.compile("[a-z0-9_./-]{3,}");
    /** Quoted display names. Single quotes accepted the same way. */
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]{3,})\"|'([^']{3,})'");
    private static final Set<String> STOP = Set.of(
            "chests", "crafting", "shaped", "blocks", "items", "used", "minecraft", "forge");
    /** {@code [[item:...]]} / {@code [[recipe_card:...]]}. Strip before single-bracket tags. */
    private static final Pattern MARKER_DOUBLE = Pattern.compile("\\[\\[[^\\]]*\\]\\]");
    /** {@code [card:123]} / {@code [Sources]}. Not ordinary prose. */
    private static final Pattern MARKER_SINGLE = Pattern.compile("\\[[^\\]]*\\]");

    private InfoCompleteness() {}

    /**
     * Markers are not "the answer said it". Otherwise a gap line is covered by its own id
     * token inside {@code [[item:id]]} and the gap check is a no-op.
     */
    private static String stripMarkers(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String noDouble = MARKER_DOUBLE.matcher(s).replaceAll("");
        return MARKER_SINGLE.matcher(noDouble).replaceAll("");
    }

    /**
     * @param gapLines already humanized; never built from raw ids here
     * @return {@code answer} unchanged (same reference) when nothing to add
     */
    public static String append(String answer, List<String> gapLines, String langCode) {
        if (gapLines == null || gapLines.isEmpty()) {
            return answer;
        }
        String base = answer == null ? "" : answer;
        String lower = stripMarkers(base).toLowerCase(Locale.ROOT);
        List<String> kept = new ArrayList<>();
        for (String line : gapLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (line.contains("[[recipe_card:") || line.contains("[card:")) {
                continue;
            }
            if (mentioned(lower, line)) {
                continue;
            }
            kept.add(line);
        }
        if (kept.isEmpty()) {
            return answer;
        }
        String header = ReplyLang.infoGapHeader(langCode);
        StringBuilder block = new StringBuilder();
        if (header != null && !header.isBlank()) {
            block.append(header.stripTrailing());
            block.append('\n');
        }
        for (int i = 0; i < kept.size(); i++) {
            if (i > 0) {
                block.append('\n');
            }
            block.append(kept.get(i));
        }
        Matcher footer = ReplySources.HEADER.matcher(base);
        if (!footer.find()) {
            if (base.isEmpty()) {
                return block.toString();
            }
            return base.endsWith("\n") ? base + block : base + "\n" + block;
        }
        int at = footer.start();
        String pre = base.substring(0, at);
        String post = base.substring(at);
        StringBuilder out = new StringBuilder(pre);
        if (!pre.isEmpty() && !pre.endsWith("\n")) {
            out.append('\n');
        }
        out.append(block);
        if (!post.startsWith("\n")) {
            out.append('\n');
        }
        out.append(post);
        return out.toString();
    }

    /**
     * Covered when an {@code ns:path} token is in the answer, or a path segment is.
     * A path run is {@code [a-z0-9_./-]{3,}} that contains {@code /}, {@code _}, or {@code -}
     * and no colon; split on those, drop length &lt; 4 and stop words
     * ({@code chests crafting shaped blocks items used minecraft forge});
     * a leftover segment hits only as a whole word (neighbors not {@code [a-z0-9_]}).
     * Third class: a quoted string, or a non-quoted {@code ->} target through {@code |} or
     * end of line, folded ({@code _}/{@code -} to space) and matched as a whole phrase
     * (neighbors not {@code [a-z0-9]}); 2026-09-20 FTB still listed
     * {@code used in blasting -> "desh ingot"} after the answer already said Desh Ingot.
     * 2026-09-20 FTB: answer already said moon village blacksmith chests, but
     * {@code chests/village/moon/blacksmith} was listed again because it has no colon.
     */
    static boolean mentioned(String answerLower, String line) {
        if (answerLower == null || line == null) {
            return false;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        Matcher ns = NS_PATH.matcher(lower);
        while (ns.find()) {
            if (answerLower.contains(ns.group())) {
                return true;
            }
        }
        Matcher run = PATH_RUN.matcher(lower);
        while (run.find()) {
            String token = run.group();
            if (token.indexOf(':') >= 0
                    || (token.indexOf('/') < 0 && token.indexOf('_') < 0 && token.indexOf('-') < 0)) {
                continue;
            }
            for (String seg : token.split("[/_-]")) {
                if (seg.length() < 4 || STOP.contains(seg)) {
                    continue;
                }
                if (wholeWord(answerLower, seg)) {
                    return true;
                }
            }
        }
        return quotedOrArrow(answerLower, lower);
    }

    /** Quote / non-quoted arrow target. Length &lt; 4 or a stop word does not count. */
    private static boolean quotedOrArrow(String answerLower, String lineLower) {
        String hay = normalizePhrase(answerLower);
        Matcher quoted = QUOTED.matcher(lineLower);
        while (quoted.find()) {
            String frag = quoted.group(1) != null ? quoted.group(1) : quoted.group(2);
            if (phraseHit(hay, frag)) {
                return true;
            }
        }
        int from = 0;
        while (from < lineLower.length()) {
            int arrow = lineLower.indexOf("->", from);
            if (arrow < 0) {
                return false;
            }
            int start = arrow + 2;
            int bar = lineLower.indexOf('|', start);
            int end = bar < 0 ? lineLower.length() : bar;
            String frag = lineLower.substring(start, end).trim();
            from = bar < 0 ? lineLower.length() : bar + 1;
            if (frag.isEmpty() || isQuoted(frag)) {
                continue;
            }
            if (phraseHit(hay, frag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean phraseHit(String hay, String raw) {
        String phrase = normalizePhrase(raw);
        if (phrase.length() < 4 || STOP.contains(phrase)) {
            return false;
        }
        return wholePhrase(hay, phrase);
    }

    /** Lower case, {@code _}/{@code -} to space, collapse repeated spaces. */
    private static String normalizePhrase(String s) {
        return s.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').trim().replaceAll(" +", " ");
    }

    private static boolean isQuoted(String frag) {
        int n = frag.length();
        if (n < 2) {
            return false;
        }
        char open = frag.charAt(0);
        return (open == '"' || open == '\'') && frag.charAt(n - 1) == open;
    }

    /** Neighbors must not be {@code [a-z0-9]}. */
    private static boolean wholePhrase(String hay, String phrase) {
        int from = 0;
        int n = phrase.length();
        while (from <= hay.length() - n) {
            int i = hay.indexOf(phrase, from);
            if (i < 0) {
                return false;
            }
            int end = i + n;
            boolean left = i == 0 || !phraseEdge(hay.charAt(i - 1));
            boolean right = end >= hay.length() || !phraseEdge(hay.charAt(end));
            if (left && right) {
                return true;
            }
            from = i + 1;
        }
        return false;
    }

    private static boolean phraseEdge(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    /** Neighbors must not be {@code [a-z0-9_]}. */
    private static boolean wholeWord(String hay, String word) {
        int from = 0;
        int n = word.length();
        while (from <= hay.length() - n) {
            int i = hay.indexOf(word, from);
            if (i < 0) {
                return false;
            }
            int end = i + n;
            boolean left = i == 0 || !wordChar(hay.charAt(i - 1));
            boolean right = end >= hay.length() || !wordChar(hay.charAt(end));
            if (left && right) {
                return true;
            }
            from = i + 1;
        }
        return false;
    }

    private static boolean wordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
    }
}

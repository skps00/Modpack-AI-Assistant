package com.skps9.packai.logic;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic guard for card references inside the final answer body: a reference may only
 * point at a card that is actually rendered. Out-of-range reference tokens are removed; everything
 * else (ingredient counts, multipliers, decimals) is left byte-for-byte untouched.
 *
 * <p>Accepted shapes: {@code (card 5)}, {@code (cards 3-5)}, {@code card 5}, {@code cards 3-5},
 * {@code [card:9]}. A range is out of range when its highest number exceeds the rendered count.
 */
public final class CardRefValidator {
    private CardRefValidator() {}

    /** Harness-only negative control: makes {@link #stripOutOfRangeCardRefs} a pass-through. */
    static boolean testDisableStrip;

    /** Internal marker for a stripped reference, resolved by {@link #tidySeams(String)}. */
    private static final char SEAM = '\u0001';

    /**
     * Bracketed / parenthesised forms first so the whole token (brackets included) is removed; the
     * bare form is the fallback. The leading guard keeps {@code discard 5} / {@code postcard 5}
     * untouched, and the bare tail guard stops {@code card 1.5} being read as {@code card 1}.
     */
    private static final Pattern CARD_REF = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])"
            + "(?:"
            + "\\(\\s*cards?\\s+(\\d+)(?:\\s*[-\u2013\u2014~]\\s*(\\d+))?\\s*\\)"
            + "|\\[\\s*card\\s*:\\s*(\\d+)(?:\\s*[-\u2013\u2014~]\\s*(\\d+))?\\s*\\]"
            + "|cards?\\s+(\\d+)(?:\\s*[-\u2013\u2014~]\\s*(\\d+))?(?!\\d)(?!\\.\\d)"
            + ")",
            Pattern.CASE_INSENSITIVE);

    /** Leftovers after a token removal, e.g. {@code (see card 5)} -> {@code (see)}. */
    private static final Pattern EMPTY_BRACKETS = Pattern.compile("\\([ \\t]*\\)|\\[[ \\t]*\\]");

    /**
     * Removes card references that no rendered card can answer.
     *
     * @param body answer text (may be multi-line; never mutated)
     * @param cardsOut number of recipe cards rendered below the answer; {@code <= 0} strips every
     *     card reference
     * @return the body with only out-of-range reference tokens removed
     */
    public static String stripOutOfRangeCardRefs(String body, int cardsOut) {
        if (body == null || body.isEmpty()) {
            return body == null ? "" : body;
        }
        if (testDisableStrip) {
            return body;
        }
        Matcher m = CARD_REF.matcher(body);
        StringBuilder sb = new StringBuilder(body.length());
        int last = 0;
        boolean changed = false;
        while (m.find()) {
            if (!isOutOfRange(m, cardsOut)) {
                continue;
            }
            sb.append(body, last, m.start());
            sb.append(SEAM);
            last = m.end();
            changed = true;
        }
        if (!changed) {
            return body;
        }
        sb.append(body, last, body.length());
        return EMPTY_BRACKETS.matcher(tidySeams(sb.toString())).replaceAll("");
    }

    /** True when any number in the reference exceeds {@code cardsOut} (or nothing is rendered). */
    private static boolean isOutOfRange(Matcher m, int cardsOut) {
        long max = -1L;
        for (int g = 1; g <= 5; g += 2) {
            String first = m.group(g);
            if (first == null) {
                continue;
            }
            long lo = parseRef(first);
            String second = m.group(g + 1);
            long hi = second == null ? lo : parseRef(second);
            max = Math.max(max, Math.max(lo, hi));
        }
        if (max < 0L) {
            return false;
        }
        return cardsOut <= 0 || max > (long) cardsOut;
    }

    private static long parseRef(String digits) {
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            // Absurdly long digit run: cannot be a rendered card.
            return Long.MAX_VALUE;
        }
    }

    /**
     * Collapses the seam a removed token left behind: no double spaces, no space before punctuation
     * or a closing bracket, no leading space on a line.
     */
    private static String tidySeams(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != SEAM) {
                out.append(c);
                i++;
                continue;
            }
            int keep = out.length();
            while (keep > 0 && isInlineSpace(out.charAt(keep - 1))) {
                keep--;
            }
            int next = i + 1;
            while (next < s.length() && isInlineSpace(s.charAt(next))) {
                next++;
            }
            char prev = keep > 0 ? out.charAt(keep - 1) : '\n';
            char following = next < s.length() ? s.charAt(next) : '\n';
            out.setLength(keep);
            if (isClosingPunctuation(following) || following == '\n' || isOpening(prev) || prev == '\n') {
                // Drop the surrounding whitespace entirely.
            } else {
                out.append(' ');
            }
            i = next;
        }
        return out.toString();
    }

    private static boolean isInlineSpace(char c) {
        return c == ' ' || c == '\t';
    }

    private static boolean isOpening(char c) {
        return c == '(' || c == '[' || c == '\uFF08' || c == '\u3010';
    }

    private static boolean isClosingPunctuation(char c) {
        switch (c) {
            case '.':
            case ',':
            case ';':
            case ':':
            case '!':
            case '?':
            case ')':
            case ']':
            case '}':
            case '\u3002': // 。
            case '\uFF0C': // ，
            case '\uFF1B': // ；
            case '\uFF1A': // ：
            case '\uFF01': // ！
            case '\uFF1F': // ？
            case '\u3001': // 、
                return true;
            default:
                return false;
        }
    }
}

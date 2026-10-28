package com.skps9.packai.logic;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic target-language guard for the player-visible answer body.
 *
 * <p>Removes foreign-script <em>runs</em> that leaked into an answer (real case: an
 * {@code en_us} reply carrying the Chinese sentence
 * {@code Giselle addon 讓你不用整套也能呼吸} in the middle of a numbered step).
 *
 * <ul>
 *   <li>{@code en*} → drop Han runs (U+4E00–U+9FFF, U+3400–U+4DBF) plus CJK/fullwidth
 *       punctuation (U+FF00–U+FFEF, U+3000–U+303F).</li>
 *   <li>{@code zh*} → drop a Latin-script word of 6+ letters <em>only</em> when the whole
 *       token is sandwiched between Han characters and does not look like a name
 *       (capitalised tokens are kept). Conservative by design: removing nothing is
 *       preferred over gutting a legit mod / machine name.</li>
 *   <li>Other languages → returned untouched.</li>
 * </ul>
 *
 * <p>No double spaces, no dangling separators, no blank lines that only held the removed
 * run. Text with nothing to remove is returned as the same instance, so clean answers are
 * byte-for-byte unchanged. If stripping would blank the whole body, the original is
 * returned instead.
 */
public final class AnswerLanguageGuard {
    private AnswerLanguageGuard() {}

    /** Headless negative control for {@link AnswerLanguageGuardCheck}: bypass stripping. */
    static boolean testStripDisabled;

    /** Han ideographs. */
    private static final String HAN = "\u4e00-\u9fff\u3400-\u4dbf";
    /** Fullwidth forms + CJK punctuation (U+FF00–U+FFEF, U+3000–U+303F). */
    private static final String CJK_AUX = "\uff00-\uffef\u3000-\u303f";

    private static final Pattern CJK_RUN = Pattern.compile("[" + HAN + CJK_AUX + "]+");

    /** Latin-script word of 6+ letters (standalone token checked separately). */
    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z]{6,}");

    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]{2,}");
    private static final Pattern SPACE_BEFORE_PUNCT = Pattern.compile(" +([,.;:!?%\\)\\]\\}])");
    private static final Pattern SPACE_AFTER_OPEN = Pattern.compile("([\\(\\[{]) +");
    private static final Pattern DUP_SEPARATOR = Pattern.compile("([,;:])(?:[ \\t]*\\1)+");
    private static final Pattern LEADING_SEPARATOR = Pattern.compile("^[,;:、，]+[ \\t]*");
    /** Bracket pair left holding nothing after a cut: "()" / "( )" / "[]" / "{}". */
    private static final Pattern EMPTY_BRACKETS =
            Pattern.compile("\\([ \\t]*\\)|\\[[ \\t]*\\]|\\{[ \\t]*\\}");

    /** Marker / numbering characters that carry no prose of their own. */
    private static final String MARKERS = ",.;:!?()[]{}<>-_–—*#+|/\\•·、，。：；！？（）【】「」";

    /**
     * Strips foreign-script runs from {@code body} for reply language {@code langCode}.
     * Returns {@code body} itself when there is nothing to strip or the result would be blank.
     */
    public static String stripForeignRuns(String body, String langCode) {
        if (body == null || body.isEmpty() || testStripDisabled) {
            return body;
        }
        String lang = langCode == null ? "" : langCode.trim().toLowerCase(Locale.ROOT);
        boolean english = lang.startsWith("en");
        boolean chinese = lang.startsWith("zh");
        if (!english && !chinese) {
            return body;
        }
        String[] lines = body.split("\n", -1);
        StringBuilder out = new StringBuilder(body.length());
        boolean changed = false;
        boolean first = true;
        for (String line : lines) {
            String stripped = english ? dropCjkRuns(line) : dropSandwichedLatin(line);
            if (stripped.equals(line)) {
                if (!first) {
                    out.append('\n');
                }
                first = false;
                out.append(line);
                continue;
            }
            changed = true;
            stripped = tidy(line, stripped);
            if (isContentless(stripped)) {
                // Whole line was the foreign run (plus its "2." marker): drop it, not leave a stub.
                continue;
            }
            if (!first) {
                out.append('\n');
            }
            first = false;
            out.append(stripped);
        }
        if (!changed) {
            return body;
        }
        String result = out.toString();
        return result.isBlank() ? body : result;
    }

    /** en*: drop every run of Han / CJK-punctuation / fullwidth characters. */
    private static String dropCjkRuns(String line) {
        Matcher m = CJK_RUN.matcher(line);
        if (!m.find()) {
            return line;
        }
        StringBuilder sb = new StringBuilder(line.length());
        int last = 0;
        m.reset();
        while (m.find()) {
            String run = line.substring(m.start(), m.end());
            if (run.codePointCount(0, run.length()) < 8) {
                sb.append(line, last, m.end());   // keep short CJK runs (item/block names)
                last = m.end();
                continue;
            }
            sb.append(line, last, m.start());
            boolean glueLeft = m.start() > last && isWordChar(line.charAt(m.start() - 1));
            boolean glueRight = m.end() < line.length() && isWordChar(line.charAt(m.end()));
            if (glueLeft && glueRight) {
                // "addon讓你用in" → "addon in" (never fuse the surviving words).
                sb.append(' ');
            }
            last = m.end();
        }
        sb.append(line, last, line.length());
        return sb.toString();
    }

    /**
     * zh*: drop a 6+ letter Latin token only when Han sits on both sides of it and the token
     * is not name-shaped (lowercase start). Everything else is left alone.
     */
    private static String dropSandwichedLatin(String line) {
        Matcher m = LATIN_WORD.matcher(line);
        StringBuilder sb = new StringBuilder(line.length());
        int last = 0;
        boolean changed = false;
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            if (!isWholeToken(line, start, end) || !sandwichedInHan(line, start, end)) {
                continue;
            }
            sb.append(line, last, start);
            last = end;
            changed = true;
        }
        if (!changed) {
            return line;
        }
        sb.append(line, last, line.length());
        return sb.toString();
    }

    private static boolean isWholeToken(String line, int start, int end) {
        return (start == 0 || !isWordChar(line.charAt(start - 1)))
                && (end >= line.length() || !isWordChar(line.charAt(end)));
    }

    private static boolean sandwichedInHan(String line, int start, int end) {
        if (Character.isUpperCase(line.charAt(start))) {
            return false; // proper-noun shape: mod / machine / person name — keep it
        }
        int i = start - 1;
        while (i >= 0 && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i--;
        }
        if (i < 0 || !isHan(line.charAt(i))) {
            return false;
        }
        int j = end;
        while (j < line.length() && (line.charAt(j) == ' ' || line.charAt(j) == '\t')) {
            j++;
        }
        return j < line.length() && isHan(line.charAt(j));
    }

    /** Removes the artefacts a cut leaves behind (double spaces, " ,", " .", dangling separators). */
    private static String tidy(String original, String stripped) {
        int p = 0;
        while (p < stripped.length() && (stripped.charAt(p) == ' ' || stripped.charAt(p) == '\t')) {
            p++;
        }
        String lead = startsWithBlank(original) ? stripped.substring(0, p) : "";
        String rest = stripped.substring(p);
        if (!EMPTY_BRACKETS.matcher(original).find()) {
            // Only pairs the cut emptied itself — pre-existing "()" in the answer is left alone.
            rest = EMPTY_BRACKETS.matcher(rest).replaceAll("");
        }
        rest = MULTI_SPACE.matcher(rest).replaceAll(" ");
        rest = SPACE_BEFORE_PUNCT.matcher(rest).replaceAll("$1");
        rest = SPACE_AFTER_OPEN.matcher(rest).replaceAll("$1");
        rest = DUP_SEPARATOR.matcher(rest).replaceAll("$1");
        if (!startsWithSeparator(original.stripLeading()) && startsWithSeparator(rest)) {
            rest = LEADING_SEPARATOR.matcher(rest).replaceFirst("");
        }
        rest = stripTrailing(rest);
        return lead + rest;
    }

    /** True when nothing but numbering / punctuation / blanks is left on the line. */
    private static boolean isContentless(String line) {
        String t = line.strip();
        int i = 0;
        // Leading list numbering ("2.", "3)") is a marker, not prose.
        while (i < t.length() && Character.isDigit(t.charAt(i))) {
            i++;
        }
        for (; i < t.length(); i++) {
            char c = t.charAt(i);
            if (!Character.isWhitespace(c) && MARKERS.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithBlank(String s) {
        return !s.isEmpty() && (s.charAt(0) == ' ' || s.charAt(0) == '\t');
    }

    private static boolean startsWithSeparator(String s) {
        return !s.isEmpty() && ",;:、，".indexOf(s.charAt(0)) >= 0;
    }

    private static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '\t')) {
            end--;
        }
        return end == s.length() ? s : s.substring(0, end);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean isHan(char c) {
        return (c >= '\u4e00' && c <= '\u9fff') || (c >= '\u3400' && c <= '\u4dbf');
    }
}

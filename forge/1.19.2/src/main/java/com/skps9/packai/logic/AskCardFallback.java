package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Models often finish a how-to-get reply with no {@code [[recipe_card:N]]}, or place all markers
 * in one piled block. Mod fully manages card placement: full trust when interleaved markers cover
 * every non-empty card; partial trust keeps existing interleaved markers and inserts only missing
 * cards; otherwise strip model markers, reinsert output/quest cards after numbered GET method lines
 * ({@code 1. 工作台:}), cluster all USE input cards after the material-use method
 * (not one-per-method — method lines may be tool usage), append remaining cards at the end.
 */
public final class AskCardFallback {
    private static final Pattern METHOD_LINE = Pattern.compile("(?m)^\\s*(\\d+)\\.\\s+([^\\n:：]+)[:：]");
    private static final Pattern CARD_MARKER = Pattern.compile("\\[\\[recipe_card:\\d+\\]\\]");

    private static final String[] SECTION_PREFIXES = {
            "怎么用", "怎样用", "用途", "怎么来", "怎样来", "作为材料"
    };

    private static final String[] SOURCE_HEADERS = {
            "【来源】", "来源", "【配方】", "【用途】"
    };

    private static final List<String> GET_SECTION_PREFIXES = List.of(
            "怎样来", "怎么来", "怎么取得", "怎么获得", "怎樣來", "怎麼來");
    private static final List<String> USE_SECTION_PREFIXES = List.of("怎么用", "用途", "怎麼用", "怎样用", "怎樣用");

    private AskCardFallback() {}

    /**
     * When {@code reply} looks like how-to-get and {@code cards} is non-empty:
     * <ul>
     *   <li>Full trust — interleaved markers cover EVERY non-empty card → leave unchanged.</li>
     *   <li>Partial trust — interleaved but some cards missing (no duplicate markers) → keep
     *       existing markers and insert ONLY the missing cards (output after GET methods, input
     *       clustered at the material-use method).</li>
     *   <li>Otherwise (dup markers / fully missing / piled) — strip all markers and re-cluster:
     *       output/quest after GET methods, USE input after material-use method, append rest.</li>
     * </ul>
     * All markers stay before the source boundary. Non how-to-get replies are left unchanged.
     */
    public static String ensureCards(String reply, List<RecipeCard> cards) {
        if (reply == null || reply.isBlank()) {
            return reply == null ? "" : reply;
        }
        if (cards == null || cards.isEmpty()) {
            return reply;
        }
        if (!looksLikeHowToGet(reply)) {
            return reply;
        }
        List<Integer> outputIndices = collectOutputQuestIndices(cards);
        List<Integer> inputIndices = collectInputIndices(cards);
        java.util.Set<Integer> needed = new java.util.LinkedHashSet<>();
        needed.addAll(outputIndices);
        needed.addAll(inputIndices);
        if (replyContainsInterleavedMarkers(reply)) {
            java.util.Set<Integer> marked = markedCardIndices(reply);
            if (marked.containsAll(needed)) {
                // full trust — model interleaved markers for EVERY non-empty card
                return reply;
            }
            // Partial trust: model interleaved SOME markers correctly but missed others.
            // Keep the reply as-is and insert ONLY missing cards (don't strip + re-cluster,
            // which used to move correctly-placed markers below trailing prose).
            // Smoke 05:21 2026-09-05: model wrote 0/1/2/4, card 3 missing from text.
            // Duplicate markers (raw > distinct) mean the model garbled the reply → fall
            // through to full strip + re-cluster below.
            if (countCardMarkers(reply) == marked.size()) {
                java.util.List<Integer> missingOutput = new java.util.ArrayList<>();
                for (int idx : outputIndices) {
                    if (!marked.contains(idx)) {
                        missingOutput.add(idx);
                    }
                }
                java.util.List<Integer> missingInput = new java.util.ArrayList<>();
                for (int idx : inputIndices) {
                    if (!marked.contains(idx)) {
                        missingInput.add(idx);
                    }
                }
                String result = reply;
                if (!missingOutput.isEmpty()) {
                    String mo = tryInsertAfterMethodsSectioned(result, missingOutput, 0);
                    if (mo != null) {
                        result = mo;
                    }
                }
                if (!missingInput.isEmpty()) {
                    String mi = tryInsertAfterMaterialUseMethod(result, missingInput);
                    if (mi != null) {
                        result = mi;
                    }
                }
                return result;
            }
        }
        String stripped = stripMarkers(reply);
        if (outputIndices.isEmpty() && inputIndices.isEmpty()) {
            return stripped;
        }

        String result = stripped;
        List<Integer> pendingAppend = new ArrayList<>();

        if (!outputIndices.isEmpty()) {
            String mi = tryInsertAfterMethodsSectioned(result, outputIndices, 0); // GET
            if (mi != null) {
                result = mi;
            } else {
                pendingAppend.addAll(outputIndices);
            }
        }
        if (!inputIndices.isEmpty()) {
            // USE 卡聚喺材料 method 而非逐 method 配對（method lines 可能係工具用法）
            String mi = tryInsertAfterMaterialUseMethod(result, inputIndices);
            if (mi != null) {
                result = mi;
            } else {
                pendingAppend.addAll(inputIndices);
            }
        }
        return pendingAppend.isEmpty() ? result : appendAtEnd(result, pendingAppend);
    }

    private static String stripMarkers(String reply) {
        return CARD_MARKER.matcher(reply).replaceAll("");
    }

    /** Raw count of {@code [[recipe_card:N]]} markers (duplicates counted separately). */
    private static int countCardMarkers(String reply) {
        int count = 0;
        Matcher m = CARD_MARKER.matcher(reply);
        while (m.find()) {
            count++;
        }
        return count;
    }

    /**
     * Distinct card indices referenced by per-card interleave markers ([[recipe_card:N]])
     * in the reply. Markers referencing the same index collapse to one element.
     */
    private static java.util.Set<Integer> markedCardIndices(String reply) {
        java.util.Set<Integer> marked = new java.util.LinkedHashSet<>();
        Matcher m = CARD_MARKER.matcher(reply);
        while (m.find()) {
            String token = m.group();
            int colon = token.indexOf(':');
            int close = token.lastIndexOf(']') - 1; // first of the two ]] — substring end-exclusive
            if (colon >= 0 && close > colon + 1) {
                try {
                    marked.add(Integer.parseInt(token.substring(colon + 1, close).trim()));
                } catch (NumberFormatException ignored) {
                    // malformed marker — not a card reference
                }
            }
        }
        return marked;
    }

    /**
     * True only when the reply carries at least two card markers that are interleaved: no marker
     * sits after the first source header (【来源】/来源/【配方】/【用途】), and every adjacent
     * marker pair is separated by at least one content line (method line, bullet material line, or
     * prose); section titles and blank lines do not count. Zero/one markers, piled blocks, or
     * markers trailing after a source header make this false so the fallback re-normalizes them
     * (all cards stay before the source boundary and missing cards are filled in).
     */
    private static boolean replyContainsInterleavedMarkers(String reply) {
        Matcher m = CARD_MARKER.matcher(reply);
        int count = 0;
        int prevEnd = -1;
        int srcIdx = firstSourceHeaderIndex(reply);
        while (m.find()) {
            count++;
            if (srcIdx >= 0 && m.start() > srcIdx) {
                return false; // marker trails after the source boundary — do not trust
            }
            if (prevEnd >= 0 && !separatorHasContent(reply, prevEnd, m.start())) {
                return false; // adjacent markers not separated by content (piled block)
            }
            prevEnd = m.end();
        }
        return count >= 2;
    }

    private static boolean separatorHasContent(String reply, int from, int to) {
        for (String line : reply.substring(from, to).split("\n", -1)) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (CARD_MARKER.matcher(t).find()) {
                continue; // another marker between the pair — not a content separator
            }
            if (isSectionTitle(t)) {
                continue; // section titles do not count as separators (keeps piled-block detection)
            }
            return true; // any content line (method line, bullet material line, prose) separates
        }
        return false;
    }

    /** @return char offset of the first SOURCE_HEADERS line start, or -1 when absent. */
    private static int firstSourceHeaderIndex(String reply) {
        int off = 0;
        for (String line : reply.split("\n", -1)) {
            String t = line.trim();
            for (String header : SOURCE_HEADERS) {
                if (t.startsWith(header)) {
                    return off;
                }
            }
            off += line.length() + 1;
        }
        return -1;
    }

    /** @return 0 = GET section, 1 = USE section, -1 = not a section title */
    private static int sectionTypeOf(String line) {
        if (line == null) {
            return -1;
        }
        String t = line.trim();
        if (!t.contains(":") && !t.contains("：")) {
            return -1;
        }
        for (String p : GET_SECTION_PREFIXES) {
            if (t.startsWith(p)) {
                return 0;
            }
        }
        for (String p : USE_SECTION_PREFIXES) {
            if (t.startsWith(p)) {
                return 1;
            }
        }
        return -1;
    }

    private static int countSectionMethodLines(String reply, int wantedType) {
        return collectSectionMethodSpans(reply, wantedType)[0].size();
    }

    /**
     * Line-by-line scan: track current section type; collect METHOD_LINE spans only when
     * {@code currentSection == wantedType}.
     *
     * @return {@code [methodStarts, methodEnds]}
     */
    @SuppressWarnings("unchecked")
    private static List<Integer>[] collectSectionMethodSpans(String reply, int wantedType) {
        List<Integer> methodStarts = new ArrayList<>();
        List<Integer> methodEnds = new ArrayList<>();
        int currentSection = -1;
        int lineStart = 0;
        while (lineStart <= reply.length()) {
            int nl = reply.indexOf('\n', lineStart);
            int lineEnd = nl == -1 ? reply.length() : nl;
            String line = reply.substring(lineStart, lineEnd);
            int st = sectionTypeOf(line);
            if (st >= 0) {
                currentSection = st;
            } else if (isSectionTitle(line)) {
                currentSection = -1;
            }
            if (currentSection == wantedType) {
                Matcher matcher = METHOD_LINE.matcher(line);
                if (matcher.find()) {
                    methodStarts.add(lineStart + matcher.start());
                    methodEnds.add(lineStart + matcher.end());
                }
            }
            if (nl == -1) {
                break;
            }
            lineStart = nl + 1;
        }
        return new List[] {methodStarts, methodEnds};
    }

    private static List<Integer> collectOutputQuestIndices(List<RecipeCard> cards) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            RecipeCard c = cards.get(i);
            if (c != null && !c.isEmpty() && !c.isInputUse()) {
                indices.add(i);
            }
        }
        return indices;
    }

    private static List<Integer> collectInputIndices(List<RecipeCard> cards) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            RecipeCard c = cards.get(i);
            if (c != null && !c.isEmpty() && c.isInputUse()) {
                indices.add(i);
            }
        }
        return indices;
    }

    private static final String[] MATERIAL_USE_KEYWORDS = {
            "材料", "祭坛", "祭壇", "用途", "当作", "當作"
    };
    private static final String[] MATERIAL_USE_KEYWORDS_EN = {"ingredient", "material"};

    /** Label is material-use if it contains any material keyword (case-insensitive for EN). */
    private static boolean isMaterialUseLabel(String label) {
        if (label == null) {
            return false;
        }
        String t = label.trim();
        for (String kw : MATERIAL_USE_KEYWORDS) {
            if (t.contains(kw)) {
                return true;
            }
        }
        String lower = t.toLowerCase(Locale.ROOT);
        for (String kw : MATERIAL_USE_KEYWORDS_EN) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cluster all USE/input cards after the material-use method block (last matching label),
     * not one card per USE method — method lines may describe tool usage, not craft uses.
     * No material method → last USE method block. No USE method lines → {@code null}.
     */
    private static String tryInsertAfterMaterialUseMethod(String reply, List<Integer> cardIndices) {
        List<Integer> methodStarts = new ArrayList<>();
        List<Integer> methodEnds = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int currentSection = -1;
        int lineStart = 0;
        while (lineStart <= reply.length()) {
            int nl = reply.indexOf('\n', lineStart);
            int lineEnd = nl == -1 ? reply.length() : nl;
            String line = reply.substring(lineStart, lineEnd);
            int st = sectionTypeOf(line);
            if (st >= 0) {
                currentSection = st;
            } else if (isSectionTitle(line)) {
                currentSection = -1;
            }
            if (currentSection == 1) {
                Matcher matcher = METHOD_LINE.matcher(line);
                if (matcher.find()) {
                    methodStarts.add(lineStart + matcher.start());
                    methodEnds.add(lineStart + matcher.end());
                    labels.add(matcher.group(2));
                }
            }
            if (nl == -1) {
                break;
            }
            lineStart = nl + 1;
        }
        if (methodStarts.isEmpty()) {
            return null;
        }
        int targetIdx = -1;
        for (int i = 0; i < labels.size(); i++) {
            if (isMaterialUseLabel(labels.get(i))) {
                targetIdx = i;
            }
        }
        if (targetIdx < 0) {
            targetIdx = methodStarts.size() - 1;
        }
        int blockStart = methodEnds.get(targetIdx);
        int nl = reply.indexOf('\n', blockStart);
        blockStart = nl == -1 ? reply.length() : nl + 1; // after method line's newline
        int nextMethodStart =
                targetIdx + 1 < methodStarts.size() ? methodStarts.get(targetIdx + 1) : reply.length();
        int blockEnd = findBlockEnd(reply, blockStart, nextMethodStart);
        int insertPos = findLastLineEnd(reply, blockStart, blockEnd);
        List<Integer> sorted = new ArrayList<>(cardIndices);
        sorted.sort(Integer::compareTo);
        StringBuilder markers = new StringBuilder();
        for (int c : sorted) {
            markers.append("\n[[recipe_card:").append(c).append("]]");
        }
        StringBuilder sb = new StringBuilder(reply);
        sb.insert(insertPos, markers);
        return sb.toString();
    }

    /**
     * Insert cards after method lines belonging to {@code wantedType} sections only
     * (0 = GET, 1 = USE). Used for GET/output cards; USE/input uses
     * {@link #tryInsertAfterMaterialUseMethod}.
     *
     * @return patched reply, or {@code null} when no matching section method lines
     */
    private static String tryInsertAfterMethodsSectioned(
            String reply, List<Integer> cardIndices, int wantedType) {
        List<Integer>[] spans = collectSectionMethodSpans(reply, wantedType);
        List<Integer> methodStarts = spans[0];
        List<Integer> methodEnds = spans[1];
        if (methodStarts.isEmpty()) {
            return null;
        }
        int count = Math.min(methodStarts.size(), cardIndices.size());
        List<int[]> insertions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int blockStart = methodEnds.get(i);
            int nl = reply.indexOf('\n', blockStart);
            blockStart = nl == -1 ? reply.length() : nl + 1; // after method line's newline
            int nextMethodStart = i + 1 < methodStarts.size() ? methodStarts.get(i + 1) : reply.length();
            int blockEnd = findBlockEnd(reply, blockStart, nextMethodStart);
            int insertPos = findLastLineEnd(reply, blockStart, blockEnd);
            insertions.add(new int[] {insertPos, cardIndices.get(i)});
        }
        if (cardIndices.size() > count) {
            int lastIdx = methodStarts.size() - 1;
            int bsLast = methodEnds.get(lastIdx);
            int nlLast = reply.indexOf('\n', bsLast);
            bsLast = nlLast == -1 ? reply.length() : nlLast + 1; // after last method line's newline
            int beLast = findBlockEnd(reply, bsLast, reply.length());
            int ipLast = findLastLineEnd(reply, bsLast, beLast);
            for (int j = count; j < cardIndices.size(); j++) {
                insertions.add(new int[] {ipLast, cardIndices.get(j)});
            }
        }
        Map<Integer, List<Integer>> byPos = new TreeMap<>(Collections.reverseOrder());
        for (int[] ins : insertions) {
            byPos.computeIfAbsent(ins[0], k -> new ArrayList<>()).add(ins[1]);
        }
        StringBuilder sb = new StringBuilder(reply);
        for (Map.Entry<Integer, List<Integer>> e : byPos.entrySet()) {
            List<Integer> atPos = e.getValue();
            atPos.sort(Integer::compareTo);
            StringBuilder markers = new StringBuilder();
            for (int c : atPos) {
                markers.append("\n[[recipe_card:").append(c).append("]]");
            }
            sb.insert(e.getKey(), markers);
        }
        return sb.toString();
    }

    /**
     * End of a method's description block: stops at a section title, a blank line
     * (paragraph boundary — trailing section footer prose like {@code 本包无额外掉落…}
     * must NOT be swallowed), or the next method line.
     */
    private static int findBlockEnd(String reply, int blockStart, int nextMethodStart) {
        int lineStart = blockStart;
        while (lineStart < nextMethodStart) {
            int lineEnd = reply.indexOf('\n', lineStart);
            int actualLineEnd = lineEnd == -1 || lineEnd >= nextMethodStart ? nextMethodStart : lineEnd;
            String line = reply.substring(lineStart, actualLineEnd);
            if (isSectionTitle(line)) {
                return lineStart;
            }
            if (line.trim().isEmpty()) {
                return lineStart; // blank line — paragraph boundary
            }
            if (lineEnd == -1 || lineEnd >= nextMethodStart) {
                break;
            }
            lineStart = lineEnd + 1;
        }
        return nextMethodStart;
    }

    private static int findLastLineEnd(String reply, int blockStart, int blockEnd) {
        int lastEnd = blockStart;
        int pos = blockStart;
        while (pos < blockEnd) {
            int lineEnd = reply.indexOf('\n', pos);
            int actualLineEnd = lineEnd == -1 || lineEnd >= blockEnd ? blockEnd : lineEnd;
            String line = reply.substring(pos, actualLineEnd);
            if (!line.trim().isEmpty()) {
                lastEnd = actualLineEnd;
            }
            if (lineEnd == -1 || lineEnd >= blockEnd) {
                break;
            }
            pos = lineEnd + 1;
        }
        return lastEnd;
    }

    static boolean isSectionTitle(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        for (String header : SOURCE_HEADERS) {
            if (trimmed.startsWith(header)) {
                return true;
            }
        }
        if (!trimmed.contains(":") && !trimmed.contains("：")) {
            return false;
        }
        for (String prefix : SECTION_PREFIXES) {
            if (trimmed.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String appendAtEnd(String reply, List<Integer> cardIndices) {
        StringBuilder extra = new StringBuilder();
        for (int i : cardIndices) {
            if (extra.length() > 0) {
                extra.append(' ');
            }
            extra.append("[[recipe_card:").append(i).append("]]");
        }
        String base = reply.endsWith("\n") ? reply : reply + "\n";
        return base + extra;
    }

    static boolean looksLikeHowToGet(String reply) {
        if (reply.contains("怎么来") || reply.contains("怎样来") || reply.contains("怎么取得")
                || reply.contains("怎么获得")
                || reply.contains("怎麼來") || reply.contains("怎樣來") || reply.contains("怎麼取得")
                || reply.contains("怎麼獲得")
                || reply.contains("怎么用") || reply.contains("怎样用")
                || reply.contains("怎麼用") || reply.contains("怎樣用")) {
            return true;
        }
        String lower = reply.toLowerCase(Locale.ROOT);
        return lower.contains("how to get") || lower.contains("how to obtain")
                || lower.contains("how to use");
    }
}

package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Models often finish a how-to-get reply with no {@code [[recipe_card:N]]}, or place markers
 * at the wrong position. Mod fully manages card placement: strip any model markers, reinsert
 * output/quest cards after numbered method lines ({@code 1. 工作台:}), append remaining
 * output/quest plus all input cards at the end.
 */
public final class AskCardFallback {
    private static final Pattern METHOD_LINE = Pattern.compile("(?m)^\\s*(\\d+)\\.\\s+([^\\n:：]+)[:：]");
    private static final Pattern CARD_MARKER = Pattern.compile("\\[\\[recipe_card:\\d+\\]\\]");

    private static final String[] SECTION_PREFIXES = {
            "怎么用", "用途", "怎么来", "怎样来", "作为材料"
    };

    private static final List<String> GET_SECTION_PREFIXES = List.of(
            "怎样来", "怎么来", "怎么取得", "怎么获得", "怎樣來", "怎麼來");
    private static final List<String> USE_SECTION_PREFIXES = List.of("怎么用", "用途", "怎麼用");

    private AskCardFallback() {}

    /**
     * When {@code reply} looks like how-to-get and {@code cards} is non-empty, strip any
     * {@code [[recipe_card:N]]} from the model, insert output/quest markers after numbered
     * method explanations, then append any remaining output/quest markers plus all input
     * markers at the end. Non how-to-get replies are left unchanged.
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
        String stripped = stripMarkers(reply);
        List<Integer> outputIndices = collectOutputQuestIndices(cards);
        List<Integer> inputIndices = collectInputIndices(cards);
        if (outputIndices.isEmpty() && inputIndices.isEmpty()) {
            return stripped;
        }

        String result = stripped;
        List<Integer> pendingAppend = new ArrayList<>();

        if (!outputIndices.isEmpty()) {
            String mi = tryInsertAfterMethodsSectioned(result, outputIndices, 0); // GET
            if (mi != null) {
                result = mi;
                int n = Math.min(countSectionMethodLines(stripped, 0), outputIndices.size());
                if (outputIndices.size() > n) {
                    pendingAppend.addAll(outputIndices.subList(n, outputIndices.size()));
                }
            } else {
                pendingAppend.addAll(outputIndices);
            }
        }
        if (!inputIndices.isEmpty()) {
            String mi = tryInsertAfterMethodsSectioned(result, inputIndices, 1); // USE
            if (mi != null) {
                result = mi;
                int n = Math.min(countSectionMethodLines(stripped, 1), inputIndices.size());
                if (inputIndices.size() > n) {
                    pendingAppend.addAll(inputIndices.subList(n, inputIndices.size()));
                }
            } else {
                pendingAppend.addAll(inputIndices);
            }
        }
        return pendingAppend.isEmpty() ? result : appendAtEnd(result, pendingAppend);
    }

    private static String stripMarkers(String reply) {
        return CARD_MARKER.matcher(reply).replaceAll("");
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

    /**
     * Insert cards after method lines belonging to {@code wantedType} sections only
     * (0 = GET, 1 = USE).
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
            int nextMethodStart = i + 1 < methodStarts.size() ? methodStarts.get(i + 1) : reply.length();
            int blockEnd = findBlockEnd(reply, blockStart, nextMethodStart);
            int insertPos = findLastLineEnd(reply, blockStart, blockEnd);
            insertions.add(new int[] {insertPos, cardIndices.get(i)});
        }
        insertions.sort(Comparator.comparingInt((int[] a) -> a[0]).reversed());
        StringBuilder sb = new StringBuilder(reply);
        for (int[] ins : insertions) {
            sb.insert(ins[0], "\n[[recipe_card:" + ins[1] + "]]");
        }
        return sb.toString();
    }

    private static int findBlockEnd(String reply, int blockStart, int nextMethodStart) {
        int lineStart = blockStart;
        while (lineStart < nextMethodStart) {
            int lineEnd = reply.indexOf('\n', lineStart);
            int actualLineEnd = lineEnd == -1 || lineEnd >= nextMethodStart ? nextMethodStart : lineEnd;
            String line = reply.substring(lineStart, actualLineEnd);
            if (isSectionTitle(line)) {
                return lineStart;
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
                || reply.contains("怎么用") || reply.contains("怎麼用")) {
            return true;
        }
        String lower = reply.toLowerCase(Locale.ROOT);
        return lower.contains("how to get") || lower.contains("how to obtain")
                || lower.contains("how to use");
    }
}

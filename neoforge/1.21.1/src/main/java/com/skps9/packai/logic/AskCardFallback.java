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
    private static final Pattern METHOD_LINE = Pattern.compile("(?m)^\\s*(\\d+)\\.\\s+([^\\n:]+):\\s*$");
    private static final Pattern CARD_MARKER = Pattern.compile("\\[\\[recipe_card:\\d+\\]\\]");

    private static final String[] SECTION_PREFIXES = {
            "怎么用", "用途", "怎么来", "怎样来", "作为材料"
    };

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
        if (!outputIndices.isEmpty()) {
            String methodInserted = tryInsertAfterMethods(stripped, outputIndices);
            if (methodInserted != null) {
                int methodCount = countMethodLines(stripped);
                int insertedCount = Math.min(methodCount, outputIndices.size());
                List<Integer> toAppend = new ArrayList<>();
                if (outputIndices.size() > insertedCount) {
                    toAppend.addAll(outputIndices.subList(insertedCount, outputIndices.size()));
                }
                toAppend.addAll(inputIndices);
                return toAppend.isEmpty() ? methodInserted : appendAtEnd(methodInserted, toAppend);
            }
        }
        List<Integer> toAppend = new ArrayList<>(outputIndices);
        toAppend.addAll(inputIndices);
        return appendAtEnd(stripped, toAppend);
    }

    private static String stripMarkers(String reply) {
        return CARD_MARKER.matcher(reply).replaceAll("");
    }

    private static int countMethodLines(String reply) {
        Matcher matcher = METHOD_LINE.matcher(reply);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
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

    /** @return patched reply, or {@code null} when no numbered method lines match */
    private static String tryInsertAfterMethods(String reply, List<Integer> cardIndices) {
        Matcher matcher = METHOD_LINE.matcher(reply);
        List<Integer> methodStarts = new ArrayList<>();
        List<Integer> methodEnds = new ArrayList<>();
        while (matcher.find()) {
            methodStarts.add(matcher.start());
            methodEnds.add(matcher.end());
        }
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
        if (!trimmed.contains(":")) {
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

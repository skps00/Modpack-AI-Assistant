package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Best-effort official-name / loot-token match onto collected {@code otherOutputs} labels.
 * String-only — no invented entity ids, no pack class names.
 */
public final class SummonRecipeLookup {
    public static final String PREFIX = "summon: ";

    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "how", "to", "do", "i", "you", "what", "is", "are",
            "recipe", "recipes", "craft", "crafting", "summon", "summoned", "summoning",
            "entity", "entities", "loot", "drop", "drops", "get", "from", "for", "with",
            "use", "using", "make", "of", "in", "on", "this", "that", "item",
            "怎么", "如何", "召唤", "召喚", "配方", "合成", "制作", "製作",
            "掉落", "获得", "獲得", "什么", "什麼", "的", "从", "從", "用", "这个", "這個");

    private SummonRecipeLookup() {}

    public static boolean isSummonQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String q = question.toLowerCase(Locale.ROOT);
        return q.contains("召唤") || q.contains("召喚") || q.contains("summon");
    }

    public static String factLine(String question, List<String> extraLabels) {
        return factLine(question, extraLabels, "");
    }

    /**
     * @param lootMention focus / loot item display (same token overlap as the question)
     */
    public static String factLine(String question, List<String> extraLabels, String lootMention) {
        String hit = matchLabel(question, extraLabels, lootMention);
        return hit.isEmpty() ? "" : PREFIX + hit;
    }

    public static String matchLabel(String question, List<String> extraLabels, String lootMention) {
        List<String> labels = cleanLabels(extraLabels);
        if (labels.isEmpty()) {
            return "";
        }
        String qRaw = question == null ? "" : question.toLowerCase(Locale.ROOT);
        for (String label : labels) {
            String nl = label.toLowerCase(Locale.ROOT).trim();
            if (AskNameResolve.isPunctuationName(nl) && qRaw.contains(nl)) {
                return label;
            }
        }
        Set<String> qTok = tokens(question);
        for (String label : labels) {
            if (overlaps(qTok, tokens(label)) || containsSignificant(question, label)) {
                return label;
            }
        }
        Set<String> lootTok = tokens(lootMention);
        if (lootTok.isEmpty() && (lootMention == null || lootMention.isBlank())) {
            return "";
        }
        for (String label : labels) {
            if (overlaps(lootTok, tokens(label)) || containsSignificant(lootMention, label)) {
                return label;
            }
        }
        return "";
    }

    static Set<String> tokens(String raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        String n = raw.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        for (String p : n.split("[^\\p{L}\\p{N}]+")) {
            if (p.isBlank() || STOP.contains(p) || !isSignificant(p)) {
                continue;
            }
            out.add(p);
        }
        return out;
    }

    private static List<String> cleanLabels(List<String> extraLabels) {
        List<String> out = new ArrayList<>();
        if (extraLabels == null) {
            return out;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String s : extraLabels) {
            if (s == null || s.isBlank()) {
                continue;
            }
            String t = s.trim();
            if (seen.add(t.toLowerCase(Locale.ROOT))) {
                out.add(t);
            }
        }
        return out;
    }

    private static boolean isSignificant(String p) {
        if (p.length() >= 3) {
            return true;
        }
        return hasHan(p);
    }

    private static boolean overlaps(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        for (String t : a) {
            if (b.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSignificant(String haystack, String label) {
        if (haystack == null || label == null) {
            return false;
        }
        String q = haystack.toLowerCase(Locale.ROOT);
        String core = stripSummonPrefix(label.toLowerCase(Locale.ROOT));
        if (core.length() < 2) {
            return false;
        }
        if (!hasHan(core) && core.length() < 3) {
            return false;
        }
        return q.contains(core);
    }

    private static String stripSummonPrefix(String label) {
        String s = label.trim();
        for (String p : List.of("summoned ", "summon ", "召唤", "召喚")) {
            if (s.startsWith(p)) {
                return s.substring(p.length()).trim();
            }
        }
        return s;
    }

    private static boolean hasHan(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeScript.of(s.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }
}

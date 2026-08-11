package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * D4=B thin merge for Ask REQUIREMENTS + card footnotes.
 * Inputs: ingredient hint lines, {@link RecipeCard#reqNotes()}, unlock gate lines (#1B/#1C).
 * Upgrade later to {@code RecipeRequirements} when ≥3 consumers need the same API.
 */
public final class FormatRequirements {
    private static final int MAX_LINES = 12;
    /** Ingredient-gate noise — keep those on material labels, not REQUIREMENTS. */
    private static final Pattern INGREDIENT_GATE = Pattern.compile(
            "(?i)(refine|kill|proud[_\\s-]?soul)\\s*[≥>=]");

    private FormatRequirements() {}

    /**
     * Merged requirement lines (deduped, unlock-prefixed). Empty if nothing to show.
     *
     * @param ingredientHints optional material constraint lines (may be empty)
     * @param reqNotes        JEI-visible non-slot notes (XP / time / stress / …)
     * @param unlockGates     stage / advancement strings (#1B/#1C); empty for #1A
     */
    public static List<String> lines(
            List<String> ingredientHints,
            List<String> reqNotes,
            List<String> unlockGates,
            String lang
    ) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        addAll(out, seen, ingredientHints, false, lang);
        addAll(out, seen, reqNotes, false, lang);
        addAll(out, seen, unlockGates, true, lang);
        if (out.size() > MAX_LINES) {
            return List.copyOf(out.subList(0, MAX_LINES));
        }
        return List.copyOf(out);
    }

    /** Ask prompt block, or empty string when no lines. */
    public static String askBlock(
            List<String> ingredientHints,
            List<String> reqNotes,
            List<String> unlockGates,
            String lang
    ) {
        List<String> merged = lines(ingredientHints, reqNotes, unlockGates, lang);
        if (merged.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(ReplyLang.requirementsHeader(lang));
        for (String line : merged) {
            sb.append("- ").append(line).append('\n');
        }
        return sb.toString();
    }

    /** Compact footnote lines for recipe card (notes + unlock gates, no header). */
    public static List<String> footnoteLines(List<String> reqNotes) {
        return footnoteLines(reqNotes, List.of());
    }

    public static List<String> footnoteLines(List<String> reqNotes, List<String> unlockGates) {
        return lines(List.of(), reqNotes, unlockGates, ReplyLang.current());
    }

    /** True when text is refine/kill-style gate already shown on ingredient labels. */
    public static boolean isIngredientGateNoise(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        return INGREDIENT_GATE.matcher(raw.trim()).find();
    }

    private static void addAll(
            List<String> out,
            LinkedHashSet<String> seen,
            List<String> src,
            boolean unlock,
            String lang
    ) {
        if (src == null || src.isEmpty()) {
            return;
        }
        String prefix = unlock ? ReplyLang.unlockPrefix(lang) : "";
        for (String raw : src) {
            if (out.size() >= MAX_LINES) {
                return;
            }
            String cleaned = clean(raw);
            if (cleaned.isEmpty() || isIngredientGateNoise(cleaned)) {
                continue;
            }
            String line = unlock ? (prefix + cleaned) : cleaned;
            String key = line.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }
            out.add(line);
        }
    }

    private static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String s = Plainify.stripMcFormat(raw).trim();
        if (s.isEmpty() || s.length() > 96) {
            return "";
        }
        return s;
    }
}

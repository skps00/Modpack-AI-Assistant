package com.skps9.packai.logic;

import java.util.Locale;

/**
 * JEI hint policy for Ask — keep text consistent with recipe cards.
 * Pure helpers (no Minecraft) so unit checks can run without the game classpath.
 */
public final class AskJeiHints {
    private AskJeiHints() {}

    /** Cards present → never claim "no JEI recipes" for a focused empty target. */
    public static boolean shouldAppendNoJeiRecipes(boolean hasCards, boolean focusPresent, boolean targetEmpty) {
        return !hasCards && focusPresent && targetEmpty;
    }

    public static boolean shouldAppendJeiHintEmpty(boolean hasCards, boolean focusPresent, boolean targetEmpty) {
        return !hasCards && !focusPresent && targetEmpty;
    }

    /** True when JEI text claims no recipes / no data (or blank). */
    public static boolean isJeiAbsenceSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return true;
        }
        return looksLikeAbsenceClaim(summary);
    }

    /** True when summary already acknowledges recipe cards (avoid double-prepend). */
    public static boolean alreadyHasCardsHint(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.contains("配方卡") || text.contains("Recipe card");
    }

    /** Detect absence wording in JEI dumps or LLM reply lines. */
    public static boolean looksLikeAbsenceClaim(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String s = text;
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.contains("no showable recipes")
                || lower.contains("no jei recipe data")
                || lower.contains("no known recipe")
                || lower.contains("0 useful recipes")
                || lower.contains("no crafting recipe")
                || lower.contains("does not list")
                || lower.contains("doesn't list")
                || (lower.contains("not listed")
                        && (lower.contains("recipe") || lower.contains("jei") || lower.contains("craft")))
                || s.contains("沒有可顯示的配方")
                || s.contains("没有可显示的配方")
                || s.contains("沒有列出")
                || s.contains("没有列出")
                || s.contains("無 JEI 配方資料")
                || s.contains("无 JEI 配方资料")
                || s.contains("無JEI配方資料")
                || s.contains("无JEI配方资料")
                || s.contains("無已知配方")
                || s.contains("无已知配方")
                || s.contains("無 JEI")
                || s.contains("无 JEI")
                || s.contains("無JEI")
                || s.contains("无JEI")
                || s.contains("有用配方 0 筆")
                || s.contains("有用配方 0 笔")
                || ((s.contains("沒有") || s.contains("没有"))
                        && s.contains("合成配方")
                        && (s.contains("JEI") || s.contains("jei") || s.contains("列出") || s.contains("列")))
                || (s.contains("无配方") || s.contains("無配方"))
                        && (s.contains("JEI") || s.contains("jei") || s.contains("已知") || s.contains("资料")
                                || s.contains("資料") || s.contains("可显示") || s.contains("可顯示"));
    }

    /**
     * When recipe cards exist, never forward a false "no recipe" JEI claim — ground with card hint.
     * Non-empty JEI text still gets the hint prepended so LLM cannot invent "JEI has no craft".
     */
    public static String chooseJeiSummaryText(
            String replyLang, String jeiSummary, boolean hasCards, String firstCardTitle
    ) {
        if (!hasCards) {
            if (jeiSummary != null && !jeiSummary.isBlank()) {
                return jeiSummary;
            }
            return null;
        }
        String hint = ReplyLang.jeiRecipeCardsHint(replyLang, firstCardTitle == null ? "" : firstCardTitle);
        if (isJeiAbsenceSummary(jeiSummary)) {
            return hint;
        }
        if (jeiSummary != null && !jeiSummary.isBlank()) {
            if (alreadyHasCardsHint(jeiSummary)) {
                return jeiSummary;
            }
            return hint + jeiSummary;
        }
        return hint;
    }

    /**
     * Drop / neutralize LLM lines that claim no recipes / no JEI when cards are shown.
     * Preserves {@code [[item:]]} / {@code [[recipe:]]} markers and sources footer.
     */
    public static String scrubAbsenceClaimsWhenCards(String answer, boolean hasCards) {
        if (!hasCards || answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }
        String[] lines = answer.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            boolean keep = true;
            if (!trimmed.isEmpty()
                    && !trimmed.startsWith("[[")
                    && !trimmed.startsWith("{{")
                    && !trimmed.startsWith("【來源】")
                    && !trimmed.startsWith("【来源】")
                    && !trimmed.startsWith("[Sources]")
                    && looksLikeAbsenceClaim(trimmed)) {
                keep = false;
            }
            if (keep) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(line);
            }
        }
        return out.toString()
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}

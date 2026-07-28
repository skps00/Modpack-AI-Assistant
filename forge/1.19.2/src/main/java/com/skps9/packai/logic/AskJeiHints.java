package com.skps9.packai.logic;

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
        String s = summary;
        return s.contains("no showable recipes")
                || s.contains("沒有可顯示的配方")
                || s.contains("没有可显示的配方")
                || s.contains("No JEI recipe data")
                || s.contains("無 JEI 配方資料")
                || s.contains("无 JEI 配方资料")
                || s.contains("0 useful recipes")
                || s.contains("有用配方 0 筆")
                || s.contains("有用配方 0 笔");
    }

    /**
     * When recipe cards exist, never forward a false "no recipe" JEI claim — ground with card hint.
     */
    public static String chooseJeiSummaryText(
            String replyLang, String jeiSummary, boolean hasCards, String firstCardTitle
    ) {
        if (hasCards && isJeiAbsenceSummary(jeiSummary)) {
            return ReplyLang.jeiRecipeCardsHint(replyLang, firstCardTitle == null ? "" : firstCardTitle);
        }
        if (jeiSummary != null && !jeiSummary.isBlank()) {
            return jeiSummary;
        }
        return null;
    }
}

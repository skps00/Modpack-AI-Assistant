package com.skps9.packai.logic;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.skps9.packai.config.PackAiConfig;

/**
 * Default JEI category ordering when the player has not set a custom recipe-category list.
 * Uses generic title keywords only (no mod-id / brand hard-codes).
 */
public final class CraftPriority {
    /**
     * First match wins. Tier = list index (lower = earlier).
     * Keywords are lowercase substrings matched against the normalized category title.
     */
    private static final List<List<String>> TITLE_TIERS = List.of(
            List.of("crafting table", "crafting", "工作台", "合成"),
            List.of("stonecut", "切石"),
            List.of("smelt", "furnace", "blast", "熔爐", "高爐"),
            List.of("campfire", "smoker", "煙燻", "營火"),
            List.of("compost", "堆肥"),
            List.of("processing", "machine", "加工", "機器", "工作站")
    );

    private static final List<String> QUEST_KEYS = List.of(
            "quest", "任務", "reward table", "獎勵表", "任務獎勵", "quest reward"
    );

    private static final List<String> FAST_KEYS = List.of("fast", "高速", "speed");
    private static final List<String> SLOW_KEYS = List.of("slow", "低速");

    private CraftPriority() {}

    /** Lower = recommend first. */
    public static int categoryTier(String categoryTitle) {
        String t = norm(categoryTitle);
        if (isQuestCategory(t)) {
            return switch (PackAiConfig.preferObtain()) {
                case "quest" -> -5;
                case "loot", "balanced" -> 30;
                default -> 90; // craft: quest last
            };
        }
        for (int i = 0; i < TITLE_TIERS.size(); i++) {
            if (anyMatch(t, TITLE_TIERS.get(i))) {
                return i;
            }
        }
        return 30;
    }

    /** JEI categories that look like quest rewards / quest-gated obtain. */
    public static boolean isQuestCategory(String categoryTitle) {
        return anyMatch(norm(categoryTitle), QUEST_KEYS);
    }

    /** Lower = faster (prefer when same category tier). */
    public static int speedTier(String categoryTitle) {
        String t = norm(categoryTitle);
        if (anyMatch(t, FAST_KEYS)) {
            return 0;
        }
        if (anyMatch(t, SLOW_KEYS)) {
            return 10;
        }
        return 5;
    }

    public static Comparator<String> categoryComparator() {
        return Comparator.comparingInt(CraftPriority::categoryTier)
                .thenComparingInt(CraftPriority::speedTier)
                .thenComparing(String::compareTo);
    }

    public static String preferenceHint() {
        return preferenceHint(ReplyLang.current());
    }

    public static String preferenceHint(String replyLang) {
        String lang = replyLang == null || replyLang.isBlank() ? "zh_tw" : replyLang.trim();
        return ReplyLang.craftPreferenceHint(lang, PackAiConfig.preferObtain());
    }

    private static boolean anyMatch(String haystack, List<String> needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}

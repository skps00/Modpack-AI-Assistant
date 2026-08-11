package com.skps9.packai.logic;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.skps9.packai.config.PackAiConfig;

/**
 * Default JEI category ordering when the player has not set a custom recipe-category list.
 * Uses generic title keywords only (no mod-id / brand hard-codes).
 *
 * <p>Ask recipe-card / obtain fill uses {@link #askEaseBand(String)} first (ease-first:
 * core craft → loot → other → quest). {@link RecipeCategoryPrefs#sortKey} is tie-break only.
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

    /** Chest / loot-table style JEI or fact categories (generic title keywords). */
    private static final List<String> LOOT_KEYS = List.of(
            "loot", "chest", "treasure", "戰利", "战利", "寶箱", "宝箱", "掉落", "loot table"
    );

    private static final List<String> FAST_KEYS = List.of("fast", "高速", "speed");
    private static final List<String> SLOW_KEYS = List.of("slow", "低速");

    private CraftPriority() {}

    /** Lower = recommend first. */
    public static int categoryTier(String categoryTitle) {
        String t = norm(categoryTitle);
        String prefer = PackAiConfig.preferObtain();
        if (isQuestCategory(t)) {
            return switch (prefer) {
                case "quest" -> -5;
                case "loot" -> 40;
                case "balanced" -> 35;
                default -> 90; // craft: quest last
            };
        }
        if (isLootCategory(t)) {
            return switch (prefer) {
                case "loot" -> -3;
                case "quest" -> 25;
                case "balanced" -> 5;
                default -> 8; // after core craft 0..5, before unknown 30 / quest 90
            };
        }
        for (int i = 0; i < TITLE_TIERS.size(); i++) {
            if (anyMatch(t, TITLE_TIERS.get(i))) {
                return i;
            }
        }
        return 30;
    }

    /**
     * Ask card / JEI get-section primary order (lower = first).
     * Ease-first for craft/balanced; respects preferObtain=loot|quest bands.
     * User drag order ({@link RecipeCategoryPrefs}) is secondary only.
     */
    public static int askEaseBand(String categoryTitle) {
        String t = norm(categoryTitle);
        String prefer = PackAiConfig.preferObtain();
        if ("quest".equals(prefer)) {
            if (isQuestCategory(t)) {
                return 0;
            }
            if (isCoreCraftCategory(t)) {
                return 1;
            }
            if (isLootCategory(t)) {
                return 2;
            }
            return 3;
        }
        if ("loot".equals(prefer)) {
            if (isLootCategory(t)) {
                return 0;
            }
            if (isCoreCraftCategory(t)) {
                return 1;
            }
            if (isQuestCategory(t)) {
                return 3;
            }
            return 2;
        }
        // craft + balanced: easier obtain before quest-book
        if (isCoreCraftCategory(t)) {
            return 0;
        }
        if (isLootCategory(t)) {
            return 1;
        }
        if (isQuestCategory(t)) {
            return 3;
        }
        return 2;
    }

    /** JEI categories that look like quest rewards / quest-gated obtain. */
    public static boolean isQuestCategory(String categoryTitle) {
        return anyMatch(norm(categoryTitle), QUEST_KEYS);
    }

    /** JEI / title strings that look like loot / chest obtain. */
    public static boolean isLootCategory(String categoryTitle) {
        String t = norm(categoryTitle);
        if (t.isEmpty() || isQuestCategory(t)) {
            return false;
        }
        return anyMatch(t, LOOT_KEYS);
    }

    /**
     * Crafting / stonecut / smelt / campfire-style obtain — independent of
     * {@link PackAiConfig#preferObtain()} (quests stay last for Ask card fill).
     * Used so Analyzer/Quests cannot eat every per-item card slot.
     */
    public static boolean isCoreCraftCategory(String categoryTitle) {
        String t = norm(categoryTitle);
        if (t.isEmpty() || isQuestCategory(t) || isLootCategory(t)) {
            return false;
        }
        // Only early TITLE_TIERS (0..3): crafting, stonecut, smelt, campfire/smoker.
        for (int i = 0; i <= 3 && i < TITLE_TIERS.size(); i++) {
            if (anyMatch(t, TITLE_TIERS.get(i))) {
                return true;
            }
        }
        return false;
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

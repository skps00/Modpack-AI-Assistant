package com.skps9.packai.logic;

import java.util.Comparator;
import java.util.Locale;

/** Prefer crafting-table routes over slow/automation; fast over slow when tied. */
public final class CraftPriority {
    private CraftPriority() {}

    /** Lower = recommend first. */
    public static int categoryTier(String categoryTitle) {
        String t = norm(categoryTitle);
        if (t.contains("crafting table") || t.contains("工作台") || t.contains("crafting")) {
            return 0;
        }
        if (t.contains("stonecut") || t.contains("切石")) {
            return 1;
        }
        if (t.contains("smelt") || t.contains("furnace") || t.contains("熔爐") || t.contains("blast")) {
            return 2;
        }
        if (t.contains("campfire") || t.contains("smoker") || t.contains("煙燻")) {
            return 3;
        }
        if (t.contains("compost") || t.contains("堆肥")) {
            return 4;
        }
        if (t.contains("create") && (t.contains("mix") || t.contains("mixing"))) {
            return 10;
        }
        if (t.contains("create") && t.contains("press")) {
            return 11;
        }
        if (t.contains("processing") || t.contains("加工") || t.contains("machine")) {
            return 15;
        }
        if (t.contains("mekanism") || t.contains("thermal") || t.contains("ender io")) {
            return 20;
        }
        if (t.contains("stir") || t.contains("攪拌") || t.contains("automatic stirrer")) {
            return 50;
        }
        if (t.contains("minecolonie") || t.contains("市民") || t.contains("citizen")) {
            return 60;
        }
        return 30;
    }

    /** Lower = faster (prefer when same category tier). */
    public static int speedTier(String categoryTitle) {
        String t = norm(categoryTitle);
        if (t.contains("fast") || t.contains("高速") || t.contains("speed")) {
            return 0;
        }
        if (t.contains("slow") || t.contains("低速")) {
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
        return ReplyLang.craftPreferenceHint(lang);
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}

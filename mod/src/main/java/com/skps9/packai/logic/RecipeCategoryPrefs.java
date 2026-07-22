package com.skps9.packai.logic;

import java.util.List;
import java.util.Set;

import com.skps9.packai.config.PackAiConfig;

/**
 * User prefs for JEI recipe categories: hide + custom priority order.
 * Empty order → {@link CraftPriority} heuristic; hidden UIDs always skipped.
 */
public final class RecipeCategoryPrefs {
    private RecipeCategoryPrefs() {}

    public static boolean isHidden(String uid) {
        if (uid == null || uid.isBlank()) {
            return false;
        }
        return PackAiConfig.recipeCategoryHidden().contains(uid.trim());
    }

    /**
     * Lower = show first. Custom order uses list index; unknown UIDs fall after
     * ordered ones using {@link CraftPriority#categoryTier(String)}.
     */
    public static int sortKey(String uid, String categoryTitle) {
        List<String> order = PackAiConfig.recipeCategoryOrder();
        if (!order.isEmpty() && uid != null && !uid.isBlank()) {
            int i = order.indexOf(uid.trim());
            if (i >= 0) {
                return i;
            }
            return 10_000 + CraftPriority.categoryTier(categoryTitle);
        }
        return CraftPriority.categoryTier(categoryTitle);
    }

    /** Whether any custom order or hidden list is saved. */
    public static boolean hasCustomPrefs() {
        return PackAiConfig.hasRecipeCategoryPrefs();
    }

    public static Set<String> hiddenSet() {
        return PackAiConfig.recipeCategoryHidden();
    }
}

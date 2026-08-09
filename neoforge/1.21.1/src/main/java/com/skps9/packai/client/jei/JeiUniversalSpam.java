package com.skps9.packai.client.jei;

import java.util.Locale;

import com.skps9.packai.logic.ReplyLang;

import mezz.jei.api.recipe.RecipeType;

/**
 * JEI recipes that repeat for almost every block (facades, frames, covers, camo, …).
 * Matching is by item path / category title patterns — not by mod brand lists.
 */
public final class JeiUniversalSpam {
    private JeiUniversalSpam() {}

    /**
     * Item ids that mean “wrap / skin this block” rather than a unique recipe.
     */
    public static boolean isSpamItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String s = itemId.toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        String path = colon >= 0 ? s.substring(colon + 1) : s;

        if ("facade".equals(path) || path.endsWith("_facade") || path.startsWith("facade/")) {
            return true;
        }
        if (path.startsWith("framed_") || path.startsWith("framed/")) {
            return true;
        }
        if ("cover".equals(path) || path.endsWith("_cover") || path.startsWith("cover_")
                || path.startsWith("cover/")) {
            return true;
        }
        if (path.startsWith("camo_") || path.contains("_camo_") || path.endsWith("_camo")
                || path.startsWith("camo/")) {
            return true;
        }
        if (path.startsWith("disguise") || path.contains("disguise_")) {
            return true;
        }
        return path.startsWith("mimic_") || "mimic".equals(path);
    }

    @SuppressWarnings("rawtypes")
    public static boolean isSpamCategory(RecipeType type, String catTitle) {
        String uid = "";
        try {
            Object u = type.getUid();
            if (u != null) {
                uid = u.toString().toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
            // JEI uid shape varies
        }
        String t = catTitle == null ? "" : catTitle.toLowerCase(Locale.ROOT);
        return containsSpamKeyword(uid) || containsSpamKeyword(t);
    }

    /**
     * Categories that are not placeable machine workstations (quests, info tabs, …).
     * Used to keep Machine brief off quest-book icons and similar false catalysts.
     */
    @SuppressWarnings("rawtypes")
    public static boolean isNonMachineCategory(RecipeType type, String catTitle) {
        String uid = "";
        try {
            Object u = type.getUid();
            if (u != null) {
                uid = u.toString().toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
            // JEI uid shape varies
        }
        String t = catTitle == null ? "" : catTitle.toLowerCase(Locale.ROOT);
        return containsNonMachineKeyword(uid) || containsNonMachineKeyword(t);
    }

    private static boolean containsSpamKeyword(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        return s.contains("facade")
                || s.contains("framed block")
                || s.contains("framedblocks")
                || s.contains("framed_")
                || s.contains("camo")
                || s.contains("cover")
                || s.contains("disguise")
                || s.contains("mimic");
    }

    private static boolean containsNonMachineKeyword(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        // Quests (FTB / Heracles / localized 任務書 tabs) — never Machine.
        if (s.contains("quest") || s.contains("任務") || s.contains("heracles")) {
            return true;
        }
        if (s.contains("ftbquests") || s.contains("ftb_quest")
                || (s.contains("ftb") && s.contains("quest"))) {
            return true;
        }
        // Info / ponder-only tabs — not automation workstations.
        return s.contains("information")
                || s.contains("info_category")
                || s.contains("ponder");
    }

    public static String skipReasonLabel() {
        return skipReasonLabel(ReplyLang.current());
    }

    public static String skipReasonLabel(String replyLang) {
        String lang = replyLang == null || replyLang.isBlank() ? "zh_tw" : replyLang.trim();
        return ReplyLang.spamSkipLabel(lang);
    }
}

package com.skps9.packai.client.jei;

import java.util.Locale;

import com.skps9.packai.logic.ReplyLang;

import mezz.jei.api.recipe.RecipeType;

/**
 * JEI recipes that repeat for almost every block (facades, framed blocks, covers, …).
 */
public final class JeiUniversalSpam {
    private JeiUniversalSpam() {}

    /**
     * Item ids that mean “wrap / skin this block” rather than a unique recipe.
     * Examples: {@code ae2:facade}, {@code integrateddynamics:facade},
     * {@code framedblocks:framed_divided_slab}.
     */
    public static boolean isSpamItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String s = itemId.toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        String ns = colon >= 0 ? s.substring(0, colon) : "";
        String path = colon >= 0 ? s.substring(colon + 1) : s;

        // --- facades (AE2, Integrated Dynamics, …) ---
        if ("facade".equals(path) || path.endsWith("_facade") || path.startsWith("facade/")) {
            return true;
        }

        // --- FramedBlocks: framed_slab, framed_divided_slab, … ---
        if (path.startsWith("framed_") || path.startsWith("framed/")) {
            return true;
        }
        if ("framedblocks".equals(ns) && path.startsWith("framed")) {
            return true;
        }

        // --- covers / camo / disguise (RS, IE, …) ---
        if ("cover".equals(path) || path.endsWith("_cover") || path.startsWith("cover_")) {
            return true;
        }
        if (path.startsWith("camo_") || path.contains("_camo_") || path.endsWith("_camo")) {
            return true;
        }
        if (path.startsWith("disguise") || path.contains("disguise_")) {
            return true;
        }
        if (path.startsWith("mimic_") || "mimic".equals(path)) {
            return true;
        }

        // --- mod-specific all-wrapper items ---
        return switch (ns) {
            case "refinedstorage" -> path.startsWith("cover");
            case "refinedstorage_quartz_arsenal" -> path.startsWith("cover");
            default -> false;
        };
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

    /** Short label for “skipped N universal recipes” in JEI dump. */
    public static String skipReasonLabel() {
        return skipReasonLabel(ReplyLang.current());
    }

    /** Short label for “skipped N universal recipes” in JEI dump. */
    public static String skipReasonLabel(String replyLang) {
        String lang = replyLang == null || replyLang.isBlank() ? "zh_tw" : replyLang.trim();
        return ReplyLang.spamSkipLabel(lang);
    }
}

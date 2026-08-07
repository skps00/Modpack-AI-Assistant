package com.skps9.packai.client.knowledge;

import java.util.List;

import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.RecipeGetMarks;
import com.skps9.packai.logic.ReplyLang;

import net.neoforged.fml.ModList;

/**
 * Thin client façade for Ask get+use grounding + minimal item search.
 * JEI is the only recipe UI queried in this slice; EMI is detect/stub only.
 */
public final class PackKnowledge {
    public enum Backend {
        NONE,
        JEI,
        /** EMI loaded (or preferred) but no recipe adapter yet. */
        EMI_STUB
    }

    private PackKnowledge() {}

    public static boolean jeiLoaded() {
        return ModList.get().isLoaded("jei");
    }

    public static boolean emiLoaded() {
        return ModList.get().isLoaded("emi");
    }

    /**
     * Resolve recipe UI backend from config + loaded mods.
     * {@code auto}: JEI first when both present; EMI stub if only EMI; else none.
     * {@code emi}: still query JEI when JEI is loaded (cards); EMI_STUB only when EMI and no JEI.
     */
    public static Backend resolveBackend() {
        String pref = PackAiConfig.recipeBackend();
        boolean jei = jeiLoaded();
        boolean emi = emiLoaded();
        if ("jei".equals(pref)) {
            return jei ? Backend.JEI : Backend.NONE;
        }
        if ("emi".equals(pref)) {
            if (jei) {
                return Backend.JEI;
            }
            if (emi) {
                return Backend.EMI_STUB;
            }
            return Backend.NONE;
        }
        if (jei) {
            return Backend.JEI;
        }
        if (emi) {
            return Backend.EMI_STUB;
        }
        return Backend.NONE;
    }

    public static boolean shouldQueryJei() {
        return resolveBackend() == Backend.JEI;
    }

    /**
     * When JEI is not used for get-section, return a tagged gap message for AskEngine / Sources.
     * Empty when JEI path is active (caller builds real summary).
     */
    public static String recipeGetGapOrEmpty(String replyLang) {
        Backend b = resolveBackend();
        if (b == Backend.EMI_STUB) {
            return RecipeGetMarks.EMI_PREVIEW + ReplyLang.emiRecipePreviewGap(replyLang);
        }
        if (b == Backend.NONE) {
            return RecipeGetMarks.NO_RECIPE_UI + ReplyLang.noRecipeBackend(replyLang);
        }
        return "";
    }

    /** Name / id search for Search UI — same item space Ask can focus. */
    public static List<ItemSearch.Hit> searchItems(String query, int limit) {
        return ItemSearch.search(query, limit);
    }
}

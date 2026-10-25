package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.jei.JeiCategoryCatalog;
import com.skps9.packai.client.jei.PackAiJeiPlugin;
import com.skps9.packai.client.jei.SpamAuditProbeIo;

import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;

/**
 * Autotest-only JEI category recipe audit for spam-filter tuning. No product paths call this.
 */
public final class SpamAuditProbe {
    public static final int MAX_CATS = 800;
    public static final int MAX_RECIPES_PER_CAT = 5000;
    public static final long MAX_MS = 180_000L;

    private static boolean ran;

    private SpamAuditProbe() {}

    public static void runOnce(IRecipeManager rm) {
        if (ran) {
            return;
        }
        ran = true;
        long startMs = System.currentTimeMillis();
        boolean capHit = false;
        int catsLogged = 0;
        int totalRecipes = 0;
        IIngredientManager ingredients = null;
        try {
            ingredients = PackAiJeiPlugin.runtime()
                    .map(IJeiRuntime::getIngredientManager)
                    .orElse(null);
        } catch (Throwable ignored) {
            ingredients = null;
        }
        try {
            List<IRecipeCategory<?>> categories = new ArrayList<>(rm.createRecipeCategoryLookup()
                    .includeHidden()
                    .get()
                    .toList());
            for (IRecipeCategory<?> category : categories) {
                if (System.currentTimeMillis() - startMs > MAX_MS) {
                    capHit = true;
                    break;
                }
                if (catsLogged >= MAX_CATS) {
                    capHit = true;
                    break;
                }
                if (category == null) {
                    continue;
                }
                String uid = JeiCategoryCatalog.categoryUid(category);
                if (uid == null || uid.isBlank()) {
                    uid = "?";
                }
                RecipeType<?> type;
                try {
                    type = category.getRecipeType();
                } catch (Throwable t) {
                    PackAiMod.LOGGER.info(
                            "Pack AI spam_audit cat={} n=0 outs=", uid);
                    catsLogged++;
                    continue;
                }
                if (type == null) {
                    PackAiMod.LOGGER.info(
                            "Pack AI spam_audit cat={} n=0 outs=", uid);
                    catsLogged++;
                    continue;
                }
                List<?> found;
                try {
                    found = rm.createRecipeLookup(type)
                            .includeHidden()
                            .get()
                            .toList();
                } catch (Throwable t) {
                    PackAiMod.LOGGER.info(
                            "Pack AI spam_audit cat={} n=0 outs=", uid);
                    catsLogged++;
                    continue;
                }
                int recipeCount = found.size();
                totalRecipes += recipeCount;
                Set<String> sampleOuts = new LinkedHashSet<>();
                int scanned = 0;
                for (Object recipe : found) {
                    if (System.currentTimeMillis() - startMs > MAX_MS) {
                        capHit = true;
                        break;
                    }
                    if (scanned >= MAX_RECIPES_PER_CAT) {
                        break;
                    }
                    scanned++;
                    if (sampleOuts.size() >= 3 || ingredients == null) {
                        continue;
                    }
                    for (String id : SpamAuditProbeIo.sampleOutputItemIds(
                            category, recipe, ingredients, 3 - sampleOuts.size())) {
                        sampleOuts.add(id);
                    }
                }
                PackAiMod.LOGGER.info(
                        "Pack AI spam_audit cat={} n={} outs={}",
                        uid, recipeCount, String.join(",", sampleOuts));
                catsLogged++;
                if (capHit) {
                    break;
                }
            }
        } catch (Throwable t) {
            PackAiMod.LOGGER.warn("Pack AI spam_audit error={}", t.toString());
        }
        long elapsed = System.currentTimeMillis() - startMs;
        PackAiMod.LOGGER.info(
                "Pack AI spam_audit_done cats={} recipes={} ms={} cap_hit={}",
                catsLogged, totalRecipes, elapsed, capHit);
    }
}

package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.jei.JeiCategoryCatalog;
import com.skps9.packai.client.jei.JeiCoverageProbeIo;
import com.skps9.packai.client.jei.PackAiJeiPlugin;

import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;

/**
 * Autotest-only JEI category/recipe coverage measurement. No product paths call this.
 */
public final class JeiCoverageProbe {
    public static final int MAX_RECIPES = 120_000;
    public static final long MAX_MS = 60_000L;
    private static final int MAX_CAT_LOG_LINES = 80;

    private static boolean ran;

    private JeiCoverageProbe() {}

    /**
     * Walk all JEI categories once; log summary + up to {@value MAX_CAT_LOG_LINES} category lines.
     */
    public static void runOnce(IRecipeManager rm) {
        if (ran) {
            return;
        }
        ran = true;
        long startMs = System.currentTimeMillis();
        boolean capHit = false;
        int cats = 0;
        int totalRecipes = 0;
        int unsupportedCats = 0;
        int emptyCats = 0;
        List<CatRow> rows = new ArrayList<>();
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
            cats = categories.size();
            for (IRecipeCategory<?> category : categories) {
                if (System.currentTimeMillis() - startMs > MAX_MS) {
                    capHit = true;
                    break;
                }
                if (totalRecipes >= MAX_RECIPES) {
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
                    unsupportedCats++;
                    rows.add(new CatRow(uid, 0, 0, 0));
                    continue;
                }
                if (type == null) {
                    unsupportedCats++;
                    rows.add(new CatRow(uid, 0, 0, 0));
                    continue;
                }
                List<?> found;
                try {
                    found = rm.createRecipeLookup(type)
                            .includeHidden()
                            .get()
                            .toList();
                } catch (Throwable t) {
                    unsupportedCats++;
                    rows.add(new CatRow(uid, 0, 0, 0));
                    continue;
                }
                int recipeCount = found.size();
                if (recipeCount == 0) {
                    emptyCats++;
                }
                int inputs = 0;
                int outputs = 0;
                for (Object recipe : found) {
                    if (System.currentTimeMillis() - startMs > MAX_MS) {
                        capHit = true;
                        break;
                    }
                    if (totalRecipes >= MAX_RECIPES) {
                        capHit = true;
                        break;
                    }
                    totalRecipes++;
                    if (ingredients != null && recipe != null) {
                        try {
                            inputs += JeiCoverageProbeIo.countIngredients(
                                    category, recipe, ingredients, RecipeIngredientRole.INPUT);
                            outputs += JeiCoverageProbeIo.countIngredients(
                                    category, recipe, ingredients, RecipeIngredientRole.OUTPUT);
                        } catch (Throwable ignored) {
                            // per-recipe layout harvest may fail on odd mods
                        }
                    }
                }
                rows.add(new CatRow(uid, recipeCount, outputs, inputs));
                if (capHit) {
                    break;
                }
            }
        } catch (Throwable t) {
            PackAiMod.LOGGER.warn("Pack AI jei_coverage error={}", t.toString());
        }
        long elapsed = System.currentTimeMillis() - startMs;
        PackAiMod.LOGGER.info(
                "Pack AI jei_coverage cats={} recipes={} unsupported_cats={} empty_cats={} ms={} cap_hit={}",
                cats, totalRecipes, unsupportedCats, emptyCats, elapsed, capHit);
        rows.sort(Comparator.comparingInt(CatRow::recipes).reversed());
        int logged = 0;
        for (CatRow row : rows) {
            if (logged >= MAX_CAT_LOG_LINES) {
                break;
            }
            PackAiMod.LOGGER.info(
                    "Pack AI jei_coverage.cat id={} recipes={} outputs={} inputs={}",
                    row.uid(), row.recipes(), row.outputs(), row.inputs());
            logged++;
        }
    }

    private record CatRow(String uid, int recipes, int outputs, int inputs) {}
}

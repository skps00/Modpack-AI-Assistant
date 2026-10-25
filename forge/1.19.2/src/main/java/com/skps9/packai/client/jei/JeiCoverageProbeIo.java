package com.skps9.packai.client.jei;

import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;

/** IO slot counts for autotest {@link com.skps9.packai.logic.JeiCoverageProbe} only. */
public final class JeiCoverageProbeIo {
    private JeiCoverageProbeIo() {}

    public static int countIngredients(
            IRecipeCategory<?> category,
            Object recipe,
            IIngredientManager ingredientManager,
            RecipeIngredientRole role
    ) {
        return JeiRecipeLayoutCollector.collect(category, recipe, ingredientManager)
                .ingredients(role)
                .size();
    }
}

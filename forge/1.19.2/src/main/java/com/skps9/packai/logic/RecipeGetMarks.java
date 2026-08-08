package com.skps9.packai.logic;

/** Markers AskService / PackKnowledge prefix onto recipe-get text for AskEngine. */
public final class RecipeGetMarks {
    public static final String EMI_PREVIEW = "[[packai.emi_preview]]\n";
    public static final String NO_RECIPE_UI = "[[packai.no_recipe_ui]]\n";

    private RecipeGetMarks() {}

    public static String strip(String recipeGetText) {
        if (recipeGetText == null || recipeGetText.isBlank()) {
            return recipeGetText;
        }
        if (recipeGetText.startsWith(EMI_PREVIEW)) {
            return recipeGetText.substring(EMI_PREVIEW.length());
        }
        if (recipeGetText.startsWith(NO_RECIPE_UI)) {
            return recipeGetText.substring(NO_RECIPE_UI.length());
        }
        return recipeGetText;
    }

    public static boolean isEmiPreview(String recipeGetText) {
        return recipeGetText != null && recipeGetText.startsWith(EMI_PREVIEW);
    }

    public static boolean isNoRecipeUi(String recipeGetText) {
        return recipeGetText != null && recipeGetText.startsWith(NO_RECIPE_UI);
    }
}

package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.skps9.packai.PackAiMod;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * JEI-backed recipe (R) and usage (U) summaries for the held item.
 * Safe when JEI is absent — returns null.
 */
public final class JeiLookup {
    private static final int MAX_CATEGORIES = 6;
    private static final int MAX_RECIPES_PER_CAT = 2;
    private static final int MAX_LINES = 14;
    private static final int MAX_CHARS = 1600;

    private JeiLookup() {}

    /**
     * @return plain-text JEI facts for the LLM, or null if JEI unavailable / empty stack
     */
    public static String summarize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (!ModList.get().isLoaded("jei")) {
            return null;
        }
        try {
            return summarizeUnsafe(stack);
        } catch (NoClassDefFoundError | Exception e) {
            PackAiMod.LOGGER.debug("JEI lookup skipped: {}", e.toString());
            return null;
        }
    }

    private static String summarizeUnsafe(ItemStack stack) {
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty()) {
            return null;
        }
        IJeiRuntime runtime = opt.get();
        IRecipeManager recipes = runtime.getRecipeManager();
        IFocusFactory focuses = runtime.getJeiHelpers().getFocusFactory();

        IFocus<ItemStack> asOutput = focuses.createFocus(
                RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack.copy());
        IFocus<ItemStack> asInput = focuses.createFocus(
                RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack.copy());

        String itemName = stack.getHoverName().getString();
        StringBuilder sb = new StringBuilder();
        sb.append("【JEI 資料】物品「").append(itemName).append("」\n");

        int lines = appendSection(sb, recipes, asOutput, "配方（如何製作，等同 JEI 按 R）", 0);
        lines = appendSection(sb, recipes, asInput, "用途（用在何處，等同 JEI 按 U）", lines);

        if (lines == 0) {
            return "【JEI 資料】「" + itemName + "」目前沒有可顯示的配方或用途。";
        }
        String out = sb.toString().trim();
        if (out.length() > MAX_CHARS) {
            out = out.substring(0, MAX_CHARS) + "…";
        }
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int appendSection(
            StringBuilder sb,
            IRecipeManager recipes,
            IFocus<ItemStack> focus,
            String title,
            int linesSoFar
    ) {
        List<IRecipeCategory<?>> categories = recipes.createRecipeCategoryLookup()
                .limitFocus(List.of(focus))
                .get()
                .limit(MAX_CATEGORIES)
                .toList();
        if (categories.isEmpty()) {
            return linesSoFar;
        }

        sb.append(title).append("：\n");
        int lines = linesSoFar;
        for (IRecipeCategory<?> category : categories) {
            if (lines >= MAX_LINES) {
                break;
            }
            RecipeType type = category.getRecipeType();
            IRecipeCategory cat = category;
            List<?> found = recipes.createRecipeLookup(type)
                    .limitFocus(List.of(focus))
                    .get()
                    .limit(MAX_RECIPES_PER_CAT)
                    .toList();
            String catTitle = category.getTitle().getString();
            for (Object recipe : found) {
                if (lines >= MAX_LINES) {
                    break;
                }
                String detail = formatRecipe(recipes, cat, recipe);
                sb.append("- [").append(catTitle).append("] ").append(detail).append('\n');
                lines++;
            }
        }
        return lines;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String formatRecipe(IRecipeManager recipes, IRecipeCategory category, Object recipe) {
        try {
            IIngredientSupplier supplier = recipes.getRecipeIngredients(category, recipe);
            List<String> inputs = labels(supplier.getIngredients(RecipeIngredientRole.INPUT), 8);
            List<String> outputs = labels(supplier.getIngredients(RecipeIngredientRole.OUTPUT), 4);
            String in = inputs.isEmpty() ? "（無材料）" : String.join("、", inputs);
            String out = outputs.isEmpty() ? "（無產物）" : String.join("、", outputs);
            return in + " → " + out;
        } catch (Exception e) {
            return category.getTitle().getString();
        }
    }

    private static List<String> labels(List<ITypedIngredient<?>> ingredients, int max) {
        Set<String> uniq = new LinkedHashSet<>();
        for (ITypedIngredient<?> typed : ingredients) {
            if (uniq.size() >= max) {
                break;
            }
            Optional<ItemStack> stack = typed.getItemStack();
            if (stack.isPresent() && !stack.get().isEmpty()) {
                uniq.add(stack.get().getHoverName().getString());
            }
        }
        return new ArrayList<>(uniq);
    }
}

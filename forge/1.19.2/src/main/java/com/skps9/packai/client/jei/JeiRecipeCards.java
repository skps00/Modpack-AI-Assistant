package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.logic.CraftPriority;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.RecipeCard;
import com.skps9.packai.logic.RecipeCategoryPrefs;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.fml.ModList;

/**
 * JEI 11 recipe cards — crafting best-effort; machine flow deferred (no IIngredientSupplier).
 */
public final class JeiRecipeCards {
    private static final int MAX_SCAN_PER_CAT = 200;

    private JeiRecipeCards() {}

    public static List<RecipeCard> forItem(ItemStack stack) {
        return forItem(stack, 3);
    }

    public static List<RecipeCard> forItem(ItemStack stack, int maxCards) {
        if (stack == null || stack.isEmpty() || maxCards <= 0) {
            return List.of();
        }
        if (!ModList.get().isLoaded("jei")) {
            return List.of();
        }
        try {
            return collect(stack, maxCards);
        } catch (NoClassDefFoundError | Exception e) {
            PackAiMod.LOGGER.debug("JEI recipe cards skipped: {}", e.toString());
            return List.of();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeCard> collect(ItemStack stack, int maxCards) {
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty()) {
            return List.of();
        }
        IJeiRuntime runtime = opt.get();
        IRecipeManager recipes = runtime.getRecipeManager();
        IFocusFactory focuses = runtime.getJeiHelpers().getFocusFactory();
        IFocus<ItemStack> asOutput = focuses.createFocus(
                RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack.copy());

        List<IRecipeCategory<?>> categories = new ArrayList<>(recipes.createRecipeCategoryLookup()
                .limitFocus(List.of(asOutput))
                .get()
                .toList());
        categories.removeIf(c -> RecipeCategoryPrefs.isHidden(JeiCategoryCatalog.categoryUid(c)));
        categories.sort(Comparator
                .comparingInt((IRecipeCategory<?> c) -> RecipeCategoryPrefs.sortKey(
                        JeiCategoryCatalog.categoryUid(c), c.getTitle().getString()))
                .thenComparingInt(c -> CraftPriority.speedTier(c.getTitle().getString()))
                .thenComparing(c -> c.getTitle().getString()));

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RecipeCard> out = new ArrayList<>();

        for (IRecipeCategory<?> category : categories) {
            if (out.size() >= maxCards) {
                break;
            }
            String catTitle = Plainify.stripMcFormat(category.getTitle().getString());
            RecipeType type = category.getRecipeType();
            if (JeiUniversalSpam.isSpamCategory(type, catTitle)) {
                continue;
            }
            List<?> found = recipes.createRecipeLookup(type)
                    .limitFocus(List.of(asOutput))
                    .get()
                    .limit(MAX_SCAN_PER_CAT)
                    .toList();
            for (Object recipe : found) {
                if (out.size() >= maxCards) {
                    break;
                }
                RecipeCard card = tryCrafting(recipe, catTitle);
                if (card == null || card.isEmpty()) {
                    continue;
                }
                if (!JeiFocusMatch.craftingOutputMatches(recipe, stack)) {
                    continue;
                }
                String sig = signature(card);
                if (!seen.add(sig)) {
                    continue;
                }
                out.add(card);
            }
        }
        return List.copyOf(out);
    }

    private static RecipeCard tryCrafting(Object recipe, String catTitle) {
        if (!(recipe instanceof CraftingRecipe crafting)) {
            return null;
        }
        ItemStack result;
        try {
            result = crafting.getResultItem();
        } catch (Exception e) {
            return null;
        }
        if (result == null) {
            result = ItemStack.EMPTY;
        }
        if (crafting instanceof ShapedRecipe shaped) {
            int w = Math.max(1, shaped.getWidth());
            NonNullList<Ingredient> ings = shaped.getIngredients();
            List<ItemStack> grid = emptyNine();
            for (int i = 0; i < ings.size(); i++) {
                int row = i / w;
                int col = i % w;
                if (row >= 3 || col >= 3) {
                    continue;
                }
                grid.set(row * 3 + col, firstOf(ings.get(i)));
            }
            return RecipeCard.crafting3x3(catTitle, grid, result);
        }
        if (crafting instanceof ShapelessRecipe shapeless) {
            NonNullList<Ingredient> ings = shapeless.getIngredients();
            List<ItemStack> grid = emptyNine();
            int n = Math.min(9, ings.size());
            for (int i = 0; i < n; i++) {
                grid.set(i, firstOf(ings.get(i)));
            }
            return RecipeCard.crafting3x3(catTitle, grid, result);
        }
        return null;
    }

    private static ItemStack firstOf(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack[] stacks = ingredient.getItems();
        if (stacks == null || stacks.length == 0) {
            return ItemStack.EMPTY;
        }
        return stacks[0].copy();
    }

    private static List<ItemStack> emptyNine() {
        List<ItemStack> grid = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            grid.add(ItemStack.EMPTY);
        }
        return grid;
    }

    private static String signature(RecipeCard card) {
        StringBuilder sb = new StringBuilder(card.categoryTitle()).append('|');
        for (ItemStack s : card.grid()) {
            sb.append(s == null || s.isEmpty() ? "-" : s.getItem().toString()).append(',');
        }
        for (ItemStack s : card.outputs()) {
            sb.append('>').append(s == null || s.isEmpty() ? "-" : s.getItem().toString());
        }
        return sb.toString();
    }
}

package com.skps9.packai.client.jei;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import mezz.jei.api.recipe.RecipeIngredientRole;

import java.util.Locale;

import com.skps9.packai.logic.Plainify;

/**
 * Match JEI recipe outputs/inputs to a focused stack (same id / NBT / tag ingredients).
 */
public final class JeiFocusMatch {
    private JeiFocusMatch() {}

    public static boolean outputMatchesFocus(JeiRecipeLayoutCollector.CollectedLayout layout, ItemStack focus) {
        return roleMatchesFocus(layout, focus, RecipeIngredientRole.OUTPUT, null);
    }

    public static boolean roleMatchesFocus(
            JeiRecipeLayoutCollector.CollectedLayout layout, ItemStack focus, RecipeIngredientRole role
    ) {
        return roleMatchesFocus(layout, focus, role, null);
    }

    /**
     * True if layout stacks match focus, or crafting {@link Ingredient#test} accepts focus
     * (fixes JEI11 tag slots that only show oak while focus is spruce planks).
     */
    public static boolean roleMatchesFocus(
            JeiRecipeLayoutCollector.CollectedLayout layout,
            ItemStack focus,
            RecipeIngredientRole role,
            Object recipe
    ) {
        if (focus == null || focus.isEmpty() || role == null) {
            return true;
        }
        String focusName = normName(focus.getHoverName().getString());
        String focusId = itemId(focus);
        boolean nameUseful = nameUseful(focusName, focusId);

        boolean anyLayout = false;
        if (layout != null) {
            for (ItemStack stack : layout.itemStacks(role)) {
                if (stack.isEmpty()) {
                    continue;
                }
                anyLayout = true;
                if (ItemStack.isSameItemSameTags(stack, focus)) {
                    return true;
                }
                if (nameUseful && focusName.equals(normName(stack.getHoverName().getString()))) {
                    return true;
                }
            }
        }

        if (role == RecipeIngredientRole.INPUT && craftingInputsAccept(recipe, focus)) {
            return true;
        }
        if (role == RecipeIngredientRole.OUTPUT && craftingResultMatches(recipe, focus)) {
            return true;
        }

        // No item evidence (fluid/soft-only) — keep
        if (!anyLayout && !hasCraftingIngredients(recipe)) {
            return true;
        }
        return false;
    }

    public static boolean sameRegistryId(ItemStack stack, String id) {
        if (stack == null || stack.isEmpty() || id == null || id.isBlank()) {
            return false;
        }
        ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
        return key != null && id.equalsIgnoreCase(key.toString());
    }

    /**
     * Upgrade-style JEI recipes: focus registry id appears as both INPUT and OUTPUT
     * (same item in, same item out — often with different NBT / level).
     */
    public static boolean focusAppearsAsInputAndOutput(
            JeiRecipeLayoutCollector.CollectedLayout layout, ItemStack focus
    ) {
        if (layout == null || focus == null || focus.isEmpty()) {
            return false;
        }
        String focusId = itemId(focus);
        if (focusId.isEmpty()) {
            return false;
        }
        return roleHasRegistryId(layout, RecipeIngredientRole.INPUT, focusId)
                && roleHasRegistryId(layout, RecipeIngredientRole.OUTPUT, focusId);
    }

    private static boolean roleHasRegistryId(
            JeiRecipeLayoutCollector.CollectedLayout layout, RecipeIngredientRole role, String focusId
    ) {
        for (ItemStack stack : layout.itemStacks(role)) {
            if (!stack.isEmpty() && sameRegistryId(stack, focusId)) {
                return true;
            }
        }
        return false;
    }

    /** Strict: crafting result matches focus item (false when not a crafting recipe). */
    public static boolean craftingResultMatches(Object recipe, ItemStack focus) {
        if (!(recipe instanceof CraftingRecipe crafting) || focus == null || focus.isEmpty()) {
            return false;
        }
        try {
            ItemStack out = crafting.getResultItem();
            if (out == null || out.isEmpty()) {
                return false;
            }
            if (ItemStack.isSameItemSameTags(out, focus) || out.is(focus.getItem())) {
                return true;
            }
            var focusKey = Registry.ITEM.getKey(focus.getItem());
            return focusKey != null && sameRegistryId(out, focusKey.toString());
        } catch (Exception e) {
            return false;
        }
    }

    /** Alias of {@link #craftingResultMatches}. */
    public static boolean craftingOutputMatches(Object recipe, ItemStack focus) {
        return craftingResultMatches(recipe, focus);
    }

    public static boolean craftingInputsAccept(Object recipe, ItemStack focus) {
        if (!(recipe instanceof CraftingRecipe crafting) || focus == null || focus.isEmpty()) {
            return false;
        }
        try {
            for (Ingredient ingredient : crafting.getIngredients()) {
                if (ingredientMentions(ingredient, focus)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    public static boolean hasCraftingIngredients(Object recipe) {
        if (!(recipe instanceof CraftingRecipe crafting)) {
            return false;
        }
        try {
            var list = crafting.getIngredients();
            if (list == null || list.isEmpty()) {
                return false;
            }
            for (Ingredient ingredient : list) {
                if (ingredient != null && !ingredient.isEmpty()) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    public static boolean ingredientMentions(Ingredient ingredient, ItemStack focus) {
        if (ingredient == null || ingredient.isEmpty() || focus == null || focus.isEmpty()) {
            return false;
        }
        try {
            return ingredient.test(focus);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean nameUseful(String name, String id) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        if (id != null) {
            String path = id;
            int colon = id.indexOf(':');
            if (colon >= 0) {
                path = id.substring(colon + 1);
            }
            if (normalized.equals(path.toLowerCase(Locale.ROOT)) || normalized.equals(id.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return !"item".equals(normalized);
    }

    private static String normName(String raw) {
        return Plainify.stripMcFormat(raw == null ? "" : raw).trim();
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }
}

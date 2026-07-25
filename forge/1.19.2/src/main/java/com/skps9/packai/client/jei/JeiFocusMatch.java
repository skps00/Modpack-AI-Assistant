package com.skps9.packai.client.jei;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * JEI 11 focus helpers (no IIngredientSupplier — that API is newer JEI).
 */
public final class JeiFocusMatch {
    private JeiFocusMatch() {}

    public static boolean sameRegistryId(ItemStack stack, String id) {
        if (stack == null || stack.isEmpty() || id == null || id.isBlank()) {
            return false;
        }
        ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
        return key != null && id.equalsIgnoreCase(key.toString());
    }

    /** Best-effort: crafting result matches focus id. */
    public static boolean craftingOutputMatches(Object recipe, ItemStack focus) {
        if (!(recipe instanceof CraftingRecipe crafting) || focus == null || focus.isEmpty()) {
            return true;
        }
        try {
            ItemStack out = crafting.getResultItem();
            if (out == null || out.isEmpty()) {
                return true;
            }
            if (out.is(focus.getItem())) {
                return true;
            }
            var focusKey = Registry.ITEM.getKey(focus.getItem());
            return focusKey != null && sameRegistryId(out, focusKey.toString());
        } catch (Exception e) {
            return true;
        }
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
}

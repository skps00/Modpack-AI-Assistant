package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.List;

import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Output item id sampling for autotest {@link com.skps9.packai.logic.SpamAuditProbe} only. */
public final class SpamAuditProbeIo {
    private SpamAuditProbeIo() {}

    public static List<String> sampleOutputItemIds(
            IRecipeCategory<?> category,
            Object recipe,
            IIngredientManager ingredientManager,
            int maxIds
    ) {
        List<String> out = new ArrayList<>();
        if (maxIds <= 0 || recipe == null || ingredientManager == null) {
            return out;
        }
        try {
            JeiRecipeLayoutCollector.CollectedLayout layout =
                    JeiRecipeLayoutCollector.collect(category, recipe, ingredientManager);
            for (ItemStack stack : layout.itemStacks(RecipeIngredientRole.OUTPUT)) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (key == null) {
                    continue;
                }
                String id = key.toString();
                if (!out.contains(id)) {
                    out.add(id);
                }
                if (out.size() >= maxIds) {
                    break;
                }
            }
        } catch (Throwable ignored) {
            // odd mod layouts
        }
        return out;
    }
}

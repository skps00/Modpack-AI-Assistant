package com.skps9.packai.client.autotest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.skps9.packai.client.jei.JeiCategoryCatalog;
import com.skps9.packai.client.jei.PackAiJeiPlugin;
import com.skps9.packai.logic.Plainify;

import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Autotest-only JEI catalyst coverage dump. No file I/O here — caller writes under
 * {@code packai/autotest/} when the autotest flag is active.
 */
final class JeiCatalystCensus {
    private JeiCatalystCensus() {}

    /**
     * Build {@code {"categories":[...]}} from live JEI runtime, or {@code null} if JEI unavailable.
     */
    static JsonObject build() {
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty()) {
            return null;
        }
        IJeiRuntime runtime = opt.get();
        IRecipeManager recipes = runtime.getRecipeManager();
        List<IRecipeCategory<?>> categories;
        try {
            categories = recipes.createRecipeCategoryLookup().get().toList();
        } catch (Throwable t) {
            return null;
        }
        JsonArray arr = new JsonArray();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (IRecipeCategory<?> category : categories) {
            if (category == null) {
                continue;
            }
            String uid = JeiCategoryCatalog.categoryUid(category);
            if (uid == null || uid.isBlank() || !seen.add(uid)) {
                continue;
            }
            String title = "";
            try {
                title = Plainify.stripMcFormat(category.getTitle().getString());
            } catch (Throwable ignored) {
                title = uid;
            }
            List<String> catalystIds = catalystItemIds(recipes, category);
            JsonObject row = new JsonObject();
            row.addProperty("uid", uid);
            row.addProperty("title", title == null || title.isBlank() ? uid : title);
            JsonArray cats = new JsonArray();
            for (String id : catalystIds) {
                cats.add(id);
            }
            row.add("catalysts", cats);
            row.addProperty("catalystCount", catalystIds.size());
            row.addProperty("hasCatalystItem", !catalystIds.isEmpty());
            arr.add(row);
        }
        JsonObject root = new JsonObject();
        root.add("categories", arr);
        return root;
    }

    private static List<String> catalystItemIds(IRecipeManager recipes, IRecipeCategory<?> category) {
        List<String> out = new ArrayList<>();
        LinkedHashSet<String> dedupe = new LinkedHashSet<>();
        RecipeType<?> type;
        try {
            type = category.getRecipeType();
        } catch (Throwable t) {
            return out;
        }
        if (type == null) {
            return out;
        }
        try {
            var stream = recipes.createRecipeCatalystLookup(type).includeHidden().getItemStack();
            for (ItemStack stack : stream.toList()) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
                if (key == null) {
                    continue;
                }
                String id = key.toString();
                if (dedupe.add(id)) {
                    out.add(id);
                }
            }
        } catch (Throwable ignored) {
            // category without catalyst lookup
        }
        return out;
    }
}

package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Crafting-page recipe id → result item HIT. Run with -ea. */
public final class PatchouliEntryScanCheck {
    private PatchouliEntryScanCheck() {}

    public static void main(String[] args) throws Exception {
        assert "goety:cursed_ingot_craft".equals(
                RecipeJsonOutputs.recipeIdFromDataPath(
                        "data/goety/recipes/cursed_ingot_craft.json"));
        assert "goety:cursed_ingot_craft".equals(
                RecipeJsonOutputs.recipeIdFromDataPath(
                        "kubejs/data/goety/recipes/cursed_ingot_craft.json"));
        assert "goety:cursed_ingot_craft".equals(
                RecipeJsonOutputs.recipeIdFromDataPath(
                        "data/goety/recipe/cursed_ingot_craft.json"));
        assert RecipeJsonOutputs.recipeIdFromDataPath(
                "data/minecraft/advancements/recipes/foo.json").isEmpty();

        String shapeless = "{\"type\":\"minecraft:crafting_shapeless\","
                + "\"result\":\"goety:cursed_ingot\"}";
        assert "goety:cursed_ingot".equals(RecipeJsonOutputs.resultItemFromJson(shapeless));
        String objResult = "{\"result\":{\"item\":\"extradelight:cheese\",\"count\":1}}";
        assert "extradelight:cheese".equals(RecipeJsonOutputs.resultItemFromJson(objResult));
        String idResult = "{\"result\":{\"id\":\"goety:cursed_ingot\"}}";
        assert "goety:cursed_ingot".equals(RecipeJsonOutputs.resultItemFromJson(idResult));

        JsonObject entry = JsonParser.parseString("{"
                + "\"name\":\"Cursed Ingot\","
                + "\"icon\":\"minecraft:book\","
                + "\"pages\":["
                + "{\"type\":\"patchouli:text\",\"text\":\"Lore.\"},"
                + "{\"type\":\"patchouli:crafting\",\"recipe\":\"goety:cursed_ingot_craft\","
                + "\"recipe2\":\"goety:cursed_ingot_block\"}"
                + "]}").getAsJsonObject();
        assert PatchouliEntryScan.collectCraftingRecipeIds(entry)
                .equals(List.of("goety:cursed_ingot_craft", "goety:cursed_ingot_block"));
        List<String> miss = PatchouliEntryScan.collectLinkedItems(entry);
        assert !miss.contains("goety:cursed_ingot") : miss;
        assert miss.contains("minecraft:book") : miss;

        Map<String, String> outputs = Map.of(
                "goety:cursed_ingot_craft", "goety:cursed_ingot",
                "goety:cursed_ingot_block", "goety:cursed_ingot_block");
        List<String> hit = PatchouliEntryScan.collectLinkedItems(entry, outputs);
        assert hit.contains("goety:cursed_ingot") : hit;
        assert hit.contains("goety:cursed_ingot_block") : hit;

        Path tmp = Files.createTempDirectory("packai-recipe-json");
        Path recipe = tmp.resolve("kubejs/data/goety/recipes/cursed_ingot_craft.json");
        Files.createDirectories(recipe.getParent());
        Files.writeString(recipe, shapeless, StandardCharsets.UTF_8);
        Map<String, String> resolved = RecipeJsonOutputs.resolve(
                tmp, Set.of("goety:cursed_ingot_craft"));
        assert "goety:cursed_ingot".equals(resolved.get("goety:cursed_ingot_craft")) : resolved;

        System.out.println("PatchouliEntryScanCheck OK");
    }
}

package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Harness for {@link AskReplyScrub#insertAtHowToGetTop} + hard insert-line cap.
 */
public final class ObtainInsertCheck {
    private ObtainInsertCheck() {}

    public static void main(String[] args) {
        caseNullEmptyUnchanged();
        caseAlreadyHasLineUnchanged();
        caseMissLineReplaced();
        caseProseAppendAfter();
        caseMissingSectionUnchanged();
        caseDuplicateIngredientsSkipped();
        caseDifferentIngredientsInserted();

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        caseTenStepRendersFourPlusTooMany();

        System.out.println("ObtainInsertCheck OK");
    }

    private static void caseNullEmptyUnchanged() {
        assert AskReplyScrub.insertAtHowToGetTop(null, List.of("a"), "miss") == null
                : "null body";
        String empty = "";
        assert AskReplyScrub.insertAtHowToGetTop(empty, List.of("a"), "miss") == empty
                : "empty body";
        String body = "How to get:\nkeep\n";
        assert AskReplyScrub.insertAtHowToGetTop(body, null, "miss") == body
                : "null lines";
        assert AskReplyScrub.insertAtHowToGetTop(body, List.of(), "miss") == body
                : "empty lines";
    }

    private static void caseAlreadyHasLineUnchanged() {
        String line = "PATH_LINE_A";
        String body = "How to get:\n" + line + "\nextra\n\nHow to use:\nuse\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of(line), "MISS");
        assert out.equals(body) : "duplicate must not rewrite: " + out;
    }

    private static void caseMissLineReplaced() {
        String miss = "MISS_LINE_ONLY";
        String path = "PATH_LINE_B";
        String body = "How to get:\n" + miss + "\n\nHow to use:\nuse\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of(path), miss);
        assert out.contains(path) : out;
        assert !out.contains(miss) : "miss must be gone: " + out;
        assert out.contains("How to use:") : out;
    }

    /** Section with real prose → path appended after prose; prose unchanged. */
    private static void caseProseAppendAfter() {
        String path = "PATH_LINE_C";
        String keep = "ORIGINAL_CONTENT_KEEP";
        String body = "How to get:\n" + keep + "\n\nHow to use:\nuse\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of(path), "MISS");
        assert out.contains(path) : out;
        assert out.contains(keep) : "original must remain: " + out;
        int pathAt = out.indexOf(path);
        int keepAt = out.indexOf(keep);
        assert pathAt >= 0 && keepAt >= 0 && keepAt < pathAt
                : "path must be after prose: " + out;
        assert out.contains("How to get:\n" + keep) : "prose block intact: " + out;
    }

    private static void caseMissingSectionUnchanged() {
        String body = "plain answer\nno how-to-get heading\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of("PATH_LINE_D"), "MISS");
        assert out.equals(body) : "missing section must stay: " + out;
    }

    /** Section prose already lists the same ingredients → skip insert (duplicate). */
    private static void caseDuplicateIngredientsSkipped() {
        String path = "craft: Iron Ingot, Stick -> Iron Pickaxe";
        String body = "How to get:\nCraft with 3 Iron Ingots + 2 Sticks.\n\nHow to use:\nuse\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of(path), "MISS");
        assert out.equals(body) : "same ingredients must skip: " + out;
        assert AskReplyScrub.howToFirstStepDuplicate(
                "Craft with 3 Iron Ingots + 2 Sticks.\n", path)
                : "helper must see duplicate";
    }

    /** Section lists different ingredients → path lines still inserted. */
    private static void caseDifferentIngredientsInserted() {
        String path = "craft: Iron Ingot, Stick -> Iron Pickaxe";
        String keep = "Need Diamond and Cobblestone.";
        String body = "How to get:\n" + keep + "\n\nHow to use:\nuse\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of(path), "MISS");
        assert out.contains(path) : "different ingredients must insert: " + out;
        assert out.contains(keep) : "prose kept: " + out;
        assert !out.equals(body) : "body must change";
    }

    /** 10-step path @ budget 12 → exactly 4 lines, last = too_many. */
    private static void caseTenStepRendersFourPlusTooMany() {
        // 10-edge chain → 10 render steps when complete leaf.
        Item[] chain = {
            Items.DIAMOND_BLOCK,
            Items.DIAMOND,
            Items.DIAMOND_ORE,
            Items.DEEPSLATE_DIAMOND_ORE,
            Items.COAL_BLOCK,
            Items.COAL,
            Items.COAL_ORE,
            Items.CHARCOAL,
            Items.OAK_LOG,
            Items.OAK_PLANKS,
            Items.STICK
        };
        List<Recipe<?>> recipes = new ArrayList<>();
        for (int i = 0; i < chain.length - 1; i++) {
            recipes.add(Fake.crafting("packai:ins_" + i, chain[i], Ingredient.of(chain[i + 1])));
        }
        // Depth cap → 6 steps + partial line = 7 before render trim (> MAX_INSERT_LINES).
        ObtainGraph deep = ObtainGraph.build(recipes);
        List<ObtainPaths.Path> paths = ObtainPaths.find(deep, chain[0], 1);
        assert !paths.isEmpty() : "need a deep path";
        List<ObtainPathText.Line> rendered = ObtainPathText.render(paths, "en_us", 12);
        assert rendered.size() == ObtainPathText.MAX_INSERT_LINES
                : "cap 4 got " + rendered.size() + " " + rendered;
        assert rendered.get(rendered.size() - 1).key().equals("M|too_many")
                : rendered;
        assert paths.get(0).steps().size() + (paths.get(0).complete() ? 0 : 1) > 4
                : "fixture must exceed cap before render";
    }

    static final class Fake implements Recipe<Container> {
        final ResourceLocation id;
        final RecipeType<?> type;
        final ItemStack result;
        final NonNullList<Ingredient> ingredients;

        private Fake(
                ResourceLocation id,
                RecipeType<?> type,
                ItemStack result,
                NonNullList<Ingredient> ingredients) {
            this.id = id;
            this.type = type;
            this.result = result;
            this.ingredients = ingredients;
        }

        static Fake crafting(String id, Item out, Ingredient... ings) {
            NonNullList<Ingredient> list = NonNullList.create();
            for (Ingredient ing : ings) {
                list.add(ing);
            }
            return new Fake(
                    new ResourceLocation(id), RecipeType.CRAFTING, new ItemStack(out), list);
        }

        @Override
        public boolean matches(Container container, Level level) {
            return false;
        }

        @Override
        public ItemStack assemble(Container container) {
            return result.copy();
        }

        @Override
        public boolean canCraftInDimensions(int w, int h) {
            return true;
        }

        @Override
        public ItemStack getResultItem() {
            return result;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return null;
        }

        @Override
        public RecipeType<?> getType() {
            return type;
        }

        @Override
        public NonNullList<Ingredient> getIngredients() {
            return ingredients;
        }

        @Override
        public boolean isSpecial() {
            return false;
        }
    }
}

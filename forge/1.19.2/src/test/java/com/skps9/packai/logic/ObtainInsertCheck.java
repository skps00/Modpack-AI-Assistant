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

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        caseNullEmptyUnchanged();
        caseAlreadyHasLineUnchanged();
        caseMissLineReplaced();
        caseProseAppendAfter();
        caseMissingSectionUnchanged();
        caseDuplicateIngredientsSkipped();
        caseDifferentIngredientsInserted();
        caseFractionThreeOfFourSkipped();
        caseFractionTwoOfFourInserted();
        caseLastStepUsedForDuplicate();
        caseSingleLabelNeverSkipped();
        caseWordBoundaryNoSubstringFalsePositive();
        caseZhTwHowToGetInsert();

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        caseTenStepRendersFourPlusTooMany();

        System.out.println("ObtainInsertCheck OK");
    }

    private static void caseNullEmptyUnchanged() {
        if (AskReplyScrub.insertAtHowToGetTop(null, List.of("a"), "miss") != null) {
            fail("null body");
        }
        String empty = "";
        if (AskReplyScrub.insertAtHowToGetTop(empty, List.of("a"), "miss") != empty) {
            fail("empty body");
        }
        String body = "How to get:\nkeep\n";
        if (AskReplyScrub.insertAtHowToGetTop(body, null, "miss") != body) {
            fail("null lines");
        }
        if (AskReplyScrub.insertAtHowToGetTop(body, List.of(), "miss") != body) {
            fail("empty lines");
        }
    }

    private static void caseAlreadyHasLineUnchanged() {
        String line = "PATH_LINE_A";
        String body = "How to get:\n" + line + "\nextra\n\nHow to use:\nuse\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of(line), "MISS");
        if (!out.equals(body)) {
            fail("duplicate must not rewrite: " + out);
        }
    }

    private static void caseMissLineReplaced() {
        String miss = "MISS_LINE_ONLY";
        String path = "PATH_LINE_B";
        String body = "How to get:\n" + miss + "\n\nHow to use:\nuse\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of(path), miss);
        if (!out.contains(path)) {
            fail(out);
        }
        if (out.contains(miss)) {
            fail("miss must be gone: " + out);
        }
        if (!out.contains("How to use:")) {
            fail(out);
        }
    }

    /** Section with real prose → path appended after prose; prose unchanged. */
    private static void caseProseAppendAfter() {
        String path = "PATH_LINE_C";
        String keep = "ORIGINAL_CONTENT_KEEP";
        String body = "How to get:\n" + keep + "\n\nHow to use:\nuse\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of(path), "MISS");
        if (!out.contains(path)) {
            fail(out);
        }
        if (!out.contains(keep)) {
            fail("original must remain: " + out);
        }
        int pathAt = out.indexOf(path);
        int keepAt = out.indexOf(keep);
        if (!(pathAt >= 0 && keepAt >= 0 && keepAt < pathAt)) {
            fail("path must be after prose: " + out);
        }
        if (!out.contains("How to get:\n" + keep)) {
            fail("prose block intact: " + out);
        }
    }

    private static void caseMissingSectionUnchanged() {
        String body = "plain answer\nno how-to-get heading\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of("PATH_LINE_D"), "MISS");
        if (!out.equals(body)) {
            fail("missing section must stay: " + out);
        }
    }

    /** Section prose already lists the same ingredients → skip insert (duplicate). */
    private static void caseDuplicateIngredientsSkipped() {
        String path = "craft: Iron Ingot, Stick -> Iron Pickaxe";
        String body = "How to get:\nCraft with 3 Iron Ingots + 2 Sticks.\n\nHow to use:\nuse\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of(path), "MISS");
        if (!out.equals(body)) {
            fail("same ingredients must skip: " + out);
        }
        if (!AskReplyScrub.howToFirstStepDuplicate(
                "Craft with 3 Iron Ingots + 2 Sticks.\n", path)) {
            fail("helper must see duplicate");
        }
    }

    /** Section lists different ingredients → path lines still inserted. */
    private static void caseDifferentIngredientsInserted() {
        String path = "craft: Iron Ingot, Stick -> Iron Pickaxe";
        String keep = "Need Diamond and Cobblestone.";
        String body = "How to get:\n" + keep + "\n\nHow to use:\nuse\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of(path), "MISS");
        if (!out.contains(path)) {
            fail("different ingredients must insert: " + out);
        }
        if (!out.contains(keep)) {
            fail("prose kept: " + out);
        }
        if (out.equals(body)) {
            fail("body must change");
        }
    }

    /** 3 of 4 step-1 labels in prose (0.75 ≥ 0.7) → skip insert. */
    private static void caseFractionThreeOfFourSkipped() {
        String path = "craft: Alpha Block, Bravo Block, Charlie Block, Delta Block -> Result Item";
        String body = "How to get:\nNeed Alpha Block, Bravo Block, and Charlie Block.\n\nHow to use:\nuse\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of(path), "MISS");
        if (!out.equals(body)) {
            fail("3/4 labels must skip: " + out);
        }
        if (!AskReplyScrub.howToFirstStepDuplicate(
                "Need Alpha Block, Bravo Block, and Charlie Block.\n", path)) {
            fail("helper must see 3/4 as duplicate");
        }
    }

    /** 2 of 4 step-1 labels in prose (0.5 < 0.7) → insert. */
    private static void caseFractionTwoOfFourInserted() {
        String path = "craft: Alpha Block, Bravo Block, Charlie Block, Delta Block -> Result Item";
        String keep = "Need Alpha Block and Bravo Block.";
        String body = "How to get:\n" + keep + "\n\nHow to use:\nuse\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of(path), "MISS");
        if (!out.contains(path)) {
            fail("2/4 labels must insert: " + out);
        }
        if (!out.contains(keep)) {
            fail("prose kept: " + out);
        }
        if (AskReplyScrub.howToFirstStepDuplicate(keep + "\n", path)) {
            fail("helper must not treat 2/4 as duplicate");
        }
    }

    /**
     * Duplicate probe uses LAST path line. Prose matching only the first step must still insert.
     */
    private static void caseLastStepUsedForDuplicate() {
        String first = "craft: Iron Ingot, Stick -> Iron Pickaxe";
        String last = "smelt: Raw Iron -> Iron Ingot";
        String body = "How to get:\nCraft with 3 Iron Ingots + 2 Sticks.\n\nHow to use:\nuse\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of(first, last), "MISS");
        if (!out.contains(last)) {
            fail("first-step overlap must not skip when last differs: " + out);
        }
        if (!AskReplyScrub.howToFirstStepDuplicate(
                "Craft with 3 Iron Ingots + 2 Sticks.\n", first)) {
            fail("first step alone is still a duplicate helper hit");
        }
        if (AskReplyScrub.howToFirstStepDuplicate(
                "Craft with 3 Iron Ingots + 2 Sticks.\n", last)) {
            fail("last step labels must not match that prose");
        }
    }

    /** Single-label path step never triggers duplicate skip (≥2 labels required). */
    private static void caseSingleLabelNeverSkipped() {
        String path = "smelt: Raw Iron -> Iron Ingot";
        String keep = "Smelt Raw Iron in a furnace.";
        String body = "How to get:\n" + keep + "\n\nHow to use:\nuse\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of(path), "MISS");
        if (!out.contains(path)) {
            fail("single-label must insert: " + out);
        }
        if (AskReplyScrub.howToFirstStepDuplicate(keep + "\n", path)) {
            fail("single-label helper must not skip");
        }
    }

    /** Word-boundary match: substring inside a longer token must not count. */
    private static void caseWordBoundaryNoSubstringFalsePositive() {
        String path = "craft: Stick, Cobblestone -> Lever";
        String keep = "Need Chopsticks and Cobblestone.";
        String body = "How to get:\n" + keep + "\n\nHow to use:\nuse\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of(path), "MISS");
        if (!out.contains(path)) {
            fail("Stick inside Chopsticks must not skip: " + out);
        }
        if (AskReplyScrub.howToFirstStepDuplicate(keep + "\n", path)) {
            fail("substring Stick in Chopsticks must not hit");
        }
    }

    /**
     * zh_tw heading {@code 怎麼來：} (HOW_TO_GET_LABEL) + localized craft step with ASCII
     * {@code ->} (post-{@link Plainify#forMinecraftUi} shape).
     */
    private static void caseZhTwHowToGetInsert() {
        // Matches ObtainPathText zh_tw craft template after Plainify arrow ASCII-ify.
        String path = "合成：Alpha Block、Bravo Block -> Charlie Block";
        String keep = "原本說明保留";
        String body = "怎麼來：\n" + keep + "\n\n怎麼用：\nuse\n";
        String out = AskReplyScrub.insertAtHowToGetTop(body, List.of(path), "MISS");
        if (!out.contains(path)) {
            fail("zh_tw path must insert: " + out);
        }
        if (!out.contains(keep)) {
            fail("zh_tw prose kept: " + out);
        }
        if (!out.contains("怎麼來：")) {
            fail("zh_tw heading must remain: " + out);
        }
        int pathAt = out.indexOf(path);
        int keepAt = out.indexOf(keep);
        if (!(pathAt >= 0 && keepAt >= 0 && keepAt < pathAt)) {
            fail("zh_tw path after prose: " + out);
        }
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
        if (paths.isEmpty()) {
            fail("need a deep path");
        }
        List<ObtainPathText.Line> rendered = ObtainPathText.render(paths, "en_us", 12);
        if (rendered.size() != ObtainPathText.MAX_INSERT_LINES) {
            fail("cap 4 got " + rendered.size() + " " + rendered);
        }
        if (!rendered.get(rendered.size() - 1).key().equals("M|too_many")) {
            fail(String.valueOf(rendered));
        }
        if (!(paths.get(0).steps().size() + (paths.get(0).complete() ? 0 : 1) > 4)) {
            fail("fixture must exceed cap before render");
        }
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

package com.skps9.packai.logic;

import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Proves the recipe -> RecipeIo count hop (C1 in plan v12): a recipe whose ingredient
 * stack carries count=4 must surface as Group.count==4, and {@link RecipeIo#inputGroups}
 * must stay a projection of the same single pass.
 */
public final class RecipeCountsCheck {
    private RecipeCountsCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    private static void eq(Object want, Object got, String what) {
        if (want == null ? got != null : !want.equals(got)) {
            fail(what + ": want=" + want + " got=" + got);
        }
    }

    /** Minimal recipe: fixed ingredient list, no outputs (counts are all this harness needs). */
    private static Recipe<?> fakeRecipe(List<Ingredient> ingredients) {
        return new Recipe<net.minecraft.world.Container>() {
            @Override
            public boolean matches(net.minecraft.world.Container inv, net.minecraft.world.level.Level level) {
                return false;
            }

            @Override
            public ItemStack assemble(net.minecraft.world.Container inv) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean canCraftInDimensions(int w, int h) {
                return true;
            }

            @Override
            public ItemStack getResultItem() {
                return ItemStack.EMPTY;
            }

            @Override
            public NonNullList<Ingredient> getIngredients() {
                NonNullList<Ingredient> list = NonNullList.create();
                list.addAll(ingredients);
                return list;
            }

            @Override
            public RecipeSerializer<?> getSerializer() {
                return RecipeSerializer.SHAPELESS_RECIPE;
            }

            @Override
            public net.minecraft.resources.ResourceLocation getId() {
                return new net.minecraft.resources.ResourceLocation("packai", "counts_check");
            }

            @Override
            public RecipeType<?> getType() {
                return RecipeType.CRAFTING;
            }
        };
    }

    private static void caseStackCountSurvives() {
        Ingredient four = Ingredient.of(new ItemStack(Items.CLAY_BALL, 4));
        Ingredient one = Ingredient.of(new ItemStack(Items.SAND, 1));
        Recipe<?> recipe = fakeRecipe(List.of(four, one));

        List<RecipeIo.Group> groups = RecipeIo.inputGroupsWithCounts(recipe);
        eq(2, groups.size(), "group count");
        eq(4, groups.get(0).count(), "clay ball slot count must be 4");
        eq(1, groups.get(1).count(), "sand slot count must be 1");
        boolean clayPresent = groups.get(0).items().stream().anyMatch(i -> i == Items.CLAY_BALL);
        if (!clayPresent) {
            fail("clay ball missing from group 0: " + groups.get(0).items());
        }

        // The old projection must agree with the counted pass (single source of truth).
        List<List<Item>> plain = RecipeIo.inputGroups(recipe);
        eq(groups.size(), plain.size(), "projection size");
        for (int i = 0; i < groups.size(); i++) {
            eq(groups.get(i).items(), plain.get(i), "projection group " + i);
        }
    }

    /** Each ingredient slot must trigger exactly one {@code Ingredient#getItems()} per RecipeIo call. */
    private static void caseSinglePassGetItemsCalls() {
        Ingredient four = Ingredient.of(new ItemStack(Items.CLAY_BALL, 4));
        Ingredient one = Ingredient.of(new ItemStack(Items.SAND, 1));
        Recipe<?> recipe = fakeRecipe(List.of(four, one));
        int expected = recipe.getIngredients().size();

        RecipeIo.resetStats();
        RecipeIo.inputGroupsWithCounts(recipe);
        long deltaWithCounts = RecipeIo.statGetItemsCalls;
        if (deltaWithCounts != expected) {
            fail("inputGroupsWithCounts: expected " + expected + " getItems calls, got " + deltaWithCounts);
        }

        RecipeIo.resetStats();
        RecipeIo.inputGroups(recipe);
        long deltaPlain = RecipeIo.statGetItemsCalls;
        if (deltaPlain != expected) {
            fail("inputGroups: expected " + expected + " getItems calls, got " + deltaPlain);
        }
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        caseStackCountSurvives();
        caseSinglePassGetItemsCalls();
        System.out.println("check RecipeCountsCheck OK");
    }
}

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
 * Harness for {@link ObtainPaths} + {@link ObtainPathText#render} budget semantics (A13).
 */
public final class ObtainPathsCheck {
    private ObtainPathsCheck() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        caseCycleStops();
        caseDepthCapIncomplete();
        caseDepthExactComplete();
        caseMaxPathsTwo();
        caseNoEdgesEmpty();
        caseRenderBudgets();
        caseCraftBeatsStonecutter();
        caseVariantConversionNoPath();
        caseLargeGroupNotExpanded();

        System.out.println("ObtainPathsCheck OK");
    }

    private static void caseCycleStops() {
        List<Recipe<?>> recipes = List.of(
                fake("packai:cyc_a", Items.IRON_INGOT, Items.GOLD_INGOT),
                fake("packai:cyc_b", Items.GOLD_INGOT, Items.IRON_INGOT));
        ObtainGraph g = ObtainGraph.build(recipes);
        List<ObtainPaths.Path> paths = ObtainPaths.find(g, Items.IRON_INGOT, 2);
        assert !paths.isEmpty() : "cycle must terminate with a path";
        int totalSteps = 0;
        for (ObtainPaths.Path p : paths) {
            totalSteps += p.steps().size();
            assert p.steps().size() <= ObtainPaths.MAX_DEPTH : p.steps().size();
        }
        assert totalSteps < 100 : "cycle must not explode steps=" + totalSteps;
    }

    private static void caseDepthCapIncomplete() {
        // 7-edge chain: at depth==MAX_DEPTH node still has edges → incomplete, steps==6.
        Item[] chain = {
            Items.DIAMOND_BLOCK,
            Items.DIAMOND,
            Items.DIAMOND_ORE,
            Items.DEEPSLATE_DIAMOND_ORE,
            Items.COAL_BLOCK,
            Items.COAL,
            Items.COAL_ORE,
            Items.CHARCOAL
        };
        List<Recipe<?>> recipes = new ArrayList<>();
        for (int i = 0; i < chain.length - 1; i++) {
            recipes.add(fake("packai:depth_" + i, chain[i], chain[i + 1]));
        }
        ObtainGraph g = ObtainGraph.build(recipes);
        List<ObtainPaths.Path> paths = ObtainPaths.find(g, chain[0], 1);
        assert paths.size() == 1 : paths.size();
        assert !paths.get(0).complete() : "depth 7 must be incomplete leaf";
        assert paths.get(0).steps().size() == ObtainPaths.MAX_DEPTH
                : "steps must == MAX_DEPTH got " + paths.get(0).steps().size();
    }

    private static void caseDepthExactComplete() {
        // 6-edge chain ending on a leaf at depth==MAX_DEPTH → complete=true, steps==6.
        Item[] chain = {
            Items.DIAMOND_BLOCK,
            Items.DIAMOND,
            Items.DIAMOND_ORE,
            Items.DEEPSLATE_DIAMOND_ORE,
            Items.COAL_BLOCK,
            Items.COAL,
            Items.COAL_ORE
        };
        List<Recipe<?>> recipes = new ArrayList<>();
        for (int i = 0; i < chain.length - 1; i++) {
            recipes.add(fake("packai:exact_" + i, chain[i], chain[i + 1]));
        }
        ObtainGraph g = ObtainGraph.build(recipes);
        List<ObtainPaths.Path> paths = ObtainPaths.find(g, chain[0], 1);
        assert paths.size() == 1 : paths.size();
        assert paths.get(0).complete() : "6-edge leaf must be complete";
        assert paths.get(0).steps().size() == ObtainPaths.MAX_DEPTH
                : "steps must == MAX_DEPTH got " + paths.get(0).steps().size();
    }

    private static void caseMaxPathsTwo() {
        List<Recipe<?>> recipes = List.of(
                fake("packai:mp_a", Items.STONE_BRICKS, Items.STONE),
                fake("packai:mp_b", Items.STONE_BRICKS, Items.COBBLESTONE),
                fake("packai:mp_c", Items.STONE_BRICKS, Items.SMOOTH_STONE));
        ObtainGraph g = ObtainGraph.build(recipes);
        List<ObtainPaths.Path> paths = ObtainPaths.find(g, Items.STONE_BRICKS, 2);
        assert paths.size() == 2 : "maxPaths=2 got " + paths.size();
        List<ObtainPaths.Path> one = ObtainPaths.find(g, Items.STONE_BRICKS, 1);
        assert one.size() == 1 : one.size();
    }

    private static void caseNoEdgesEmpty() {
        ObtainGraph g = ObtainGraph.build(List.of());
        List<ObtainPaths.Path> paths = ObtainPaths.find(g, Items.BEDROCK, 2);
        assert paths.isEmpty() : paths;
    }

    private static void caseRenderBudgets() {
        // Multi-step path so full render has >3 lines (for slim too_many cases).
        List<Recipe<?>> recipes = List.of(
                fake("packai:bud_pick", Items.IRON_PICKAXE, Items.IRON_INGOT),
                fakeSmelt("packai:bud_ingot", Items.IRON_INGOT, Items.RAW_IRON),
                fake("packai:bud_raw", Items.RAW_IRON, Items.IRON_ORE),
                fake("packai:bud_alt", Items.IRON_PICKAXE, Items.IRON_NUGGET));
        ObtainGraph g = ObtainGraph.build(recipes);
        List<ObtainPaths.Path> paths = ObtainPaths.find(g, Items.IRON_PICKAXE, 2);
        assert !paths.isEmpty() : "need paths for budget cases";

        // Uncapped full size for fixture (bypass MAX_INSERT via counting steps).
        int rawSteps = 0;
        for (ObtainPaths.Path p : paths) {
            rawSteps += p.steps().size();
            if (!p.complete()) {
                rawSteps++;
            }
        }
        assert paths.size() >= 2 : "need 2 paths for truncate cases, got " + paths.size();
        assert rawSteps >= 4 : "fixture need >=4 raw steps for budget-3 truncate, got " + rawSteps;

        assert ObtainPathText.render(paths, "en_us", 0).isEmpty() : "budget 0";
        assert ObtainPathText.render(paths, "en_us", 1).size() == 1 : "budget 1";

        List<ObtainPathText.Line> b2 = ObtainPathText.render(paths, "en_us", 2);
        assert b2.size() == 2 : "budget 2 got " + b2;
        assert b2.get(1).key().equals("M|too_many") : b2;

        List<ObtainPathText.Line> b3 = ObtainPathText.render(paths, "en_us", 3);
        assert b3.size() == 3 : "budget 3 = 2 content + too_many, got " + b3;
        assert b3.get(2).key().equals("M|too_many") : b3;

        List<ObtainPathText.Line> capped = ObtainPathText.render(paths, "en_us", 12);
        assert capped.size() <= ObtainPathText.MAX_INSERT_LINES
                : "budget 12 must respect MAX_INSERT_LINES got " + capped.size();
        assert !capped.isEmpty() : capped;
        assert !capped.get(0).text().contains("#") : capped.get(0).text();
        assert !capped.get(0).text().contains("packai:bud_") : capped.get(0).text();
    }

    /** Crafting edge preferred over stonecutter even when stonecutter id sorts first. */
    private static void caseCraftBeatsStonecutter() {
        List<Recipe<?>> recipes = List.of(
                Fake.stonecutting("packai:a_cut", Items.STONE_BRICKS, Ingredient.of(Items.STONE)),
                Fake.crafting("packai:z_craft", Items.STONE_BRICKS, Ingredient.of(Items.COBBLESTONE)));
        ObtainGraph g = ObtainGraph.build(recipes);
        List<ObtainGraph.Edge> edges = g.edgesFor(Items.STONE_BRICKS);
        assert edges.size() == 2 : edges;
        assert edges.get(0).typeId().getPath().equals("crafting")
                : "craft must sort first: " + edges.get(0).typeId();
        List<ObtainPaths.Path> paths = ObtainPaths.find(g, Items.STONE_BRICKS, 1);
        assert paths.size() == 1 : paths;
        assert paths.get(0).steps().get(0).edge().recipeId().toString().equals("packai:z_craft")
                : "chosen path must be crafting: " + paths.get(0).steps().get(0).edge().recipeId();
    }

    /** Sole ingredient group contains the output itself → edge dropped → no path. */
    private static void caseVariantConversionNoPath() {
        Ingredient variants = Ingredient.of(Items.STONE_BRICKS, Items.MOSSY_STONE_BRICKS);
        List<Recipe<?>> recipes = List.of(
                Fake.stonecutting("packai:variant_only", Items.STONE_BRICKS, variants));
        ObtainGraph g = ObtainGraph.build(recipes);
        assert g.edgesFor(Items.STONE_BRICKS).isEmpty() : "variant conversion must not add edge";
        assert ObtainPaths.find(g, Items.STONE_BRICKS, 2).isEmpty() : "no path";
    }

    /**
     * Ingredient group with 9+ members must NOT expand into any member — path ends at
     * that step. Deterministic across input order of the same members.
     */
    private static void caseLargeGroupNotExpanded() {
        Item[] members = {
            Items.OAK_PLANKS,
            Items.SPRUCE_PLANKS,
            Items.BIRCH_PLANKS,
            Items.JUNGLE_PLANKS,
            Items.ACACIA_PLANKS,
            Items.DARK_OAK_PLANKS,
            Items.CRIMSON_PLANKS,
            Items.WARPED_PLANKS,
            Items.MANGROVE_PLANKS
        };
        assert members.length > ObtainPaths.MAX_EXPAND_GROUP
                : "fixture need > MAX_EXPAND_GROUP members";

        List<ObtainPaths.Path> forward = pathWithLargeGroup(members);
        assert forward.size() == 1 : forward;
        assert forward.get(0).complete() : "large group must leaf-complete";
        assert forward.get(0).steps().size() == 1
                : "must not expand into group member, steps=" + forward.get(0).steps().size();

        Item[] reversed = new Item[members.length];
        for (int i = 0; i < members.length; i++) {
            reversed[i] = members[members.length - 1 - i];
        }
        List<ObtainPaths.Path> backward = pathWithLargeGroup(reversed);
        assert backward.size() == 1 : backward;
        assert backward.get(0).complete() : backward;
        assert backward.get(0).steps().size() == 1
                : "order must not change expand skip, steps=" + backward.get(0).steps().size();
        assert forward.get(0).steps().get(0).edge().recipeId()
                .equals(backward.get(0).steps().get(0).edge().recipeId())
                : "same leaf recipe regardless of member order";
    }

    private static List<ObtainPaths.Path> pathWithLargeGroup(Item[] members) {
        Ingredient big = Ingredient.of(members);
        // Give members[0] a craft edge so expand WOULD happen without the size guard.
        List<Recipe<?>> recipes = List.of(
                Fake.crafting("packai:big_table", Items.CRAFTING_TABLE, big),
                Fake.crafting(
                        "packai:plank_from_log",
                        members[0],
                        Ingredient.of(Items.OAK_LOG)));
        ObtainGraph g = ObtainGraph.build(recipes);
        return ObtainPaths.find(g, Items.CRAFTING_TABLE, 1);
    }

    private static Recipe<?> fake(String id, Item out, Item in) {
        return Fake.crafting(id, out, Ingredient.of(in));
    }

    private static Recipe<?> fakeSmelt(String id, Item out, Item in) {
        return Fake.smelting(id, out, Ingredient.of(in));
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

        static Fake smelting(String id, Item out, Ingredient... ings) {
            NonNullList<Ingredient> list = NonNullList.create();
            for (Ingredient ing : ings) {
                list.add(ing);
            }
            return new Fake(
                    new ResourceLocation(id), RecipeType.SMELTING, new ItemStack(out), list);
        }

        static Fake stonecutting(String id, Item out, Ingredient... ings) {
            NonNullList<Ingredient> list = NonNullList.create();
            for (Ingredient ing : ings) {
                list.add(ing);
            }
            return new Fake(
                    new ResourceLocation(id), RecipeType.STONECUTTING, new ItemStack(out), list);
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

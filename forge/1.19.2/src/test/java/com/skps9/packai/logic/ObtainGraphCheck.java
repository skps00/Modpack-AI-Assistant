package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * Harness for {@link ObtainGraph}: chain, cycle, alt groups, special/empty skip,
 * Barrier filter, sort-before-truncate, shuffle determinism, stale generation.
 */
public final class ObtainGraphCheck {
    private ObtainGraphCheck() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        caseSimpleChain();
        caseCycleTerminates();
        caseAltGroupThreePlanks();
        caseMultiGroup();
        caseSpecialSkipped();
        caseEmptyResultSkipped();
        caseBarrierFiltered();
        caseTruncateAfterSort();
        caseShuffleDeterminism();
        caseStaleGenerationDropped();

        System.out.println("ObtainGraphCheck OK");
    }

    private static void caseSimpleChain() {
        // stick <- plank ; plank <- log
        FakeRecipe stick = FakeRecipe.crafting(
                "packai:stick_from_plank", Items.STICK, Ingredient.of(Items.OAK_PLANKS));
        FakeRecipe plank = FakeRecipe.crafting(
                "packai:plank_from_log", Items.OAK_PLANKS, Ingredient.of(Items.OAK_LOG));
        ObtainGraph g = ObtainGraph.build(List.of(stick, plank));
        assert g.edgeCount() == 2 : "chain edges=" + g.edgeCount();
        assert g.edgesFor(Items.STICK).size() == 1 : g.edgesFor(Items.STICK);
        assert g.edgesFor(Items.OAK_PLANKS).size() == 1 : g.edgesFor(Items.OAK_PLANKS);
        List<List<Item>> stickIns = g.edgesFor(Items.STICK).get(0).inputGroups();
        assert stickIns.size() == 1 && stickIns.get(0).contains(Items.OAK_PLANKS) : stickIns;
    }

    private static void caseCycleTerminates() {
        FakeRecipe aFromB = FakeRecipe.crafting(
                "packai:a_from_b", Items.IRON_INGOT, Ingredient.of(Items.GOLD_INGOT));
        FakeRecipe bFromA = FakeRecipe.crafting(
                "packai:b_from_a", Items.GOLD_INGOT, Ingredient.of(Items.IRON_INGOT));
        ObtainGraph g = ObtainGraph.build(List.of(aFromB, bFromA));
        assert g.edgeCount() == 2 : g.edgeCount();
        List<ObtainPaths.Path> paths = ObtainPaths.find(g, Items.IRON_INGOT, 2);
        assert !paths.isEmpty() : "cycle must still yield a path";
        assert paths.size() <= 2 : paths.size();
        // Must terminate (finite steps); cycle edge present but no infinite expand.
        for (ObtainPaths.Path p : paths) {
            assert p.steps().size() <= ObtainPaths.MAX_DEPTH + 1 : p.steps().size();
        }
    }

    private static void caseAltGroupThreePlanks() {
        Ingredient planks = Ingredient.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS);
        FakeRecipe table = FakeRecipe.crafting("packai:crafting_table", Items.CRAFTING_TABLE, planks);
        ObtainGraph g = ObtainGraph.build(List.of(table));
        List<ObtainGraph.Edge> edges = g.edgesFor(Items.CRAFTING_TABLE);
        assert edges.size() == 1 : edges;
        List<List<Item>> groups = edges.get(0).inputGroups();
        assert groups.size() == 1 : "alt items must be one group, got " + groups.size();
        assert groups.get(0).size() == 3 : groups.get(0);
        assert new HashSet<>(groups.get(0)).containsAll(
                Set.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS))
                : groups.get(0);

        List<ObtainPaths.Path> paths = ObtainPaths.find(g, Items.CRAFTING_TABLE, 1);
        List<ObtainPathText.Line> lines = ObtainPathText.render(paths, "en_us", 12);
        assert !lines.isEmpty() : lines;
        String text = lines.get(0).text();
        assert text.contains("any one of") || text.contains("3") : text;
        assert !text.contains("#") : "must not emit #tag: " + text;
    }

    private static void caseMultiGroup() {
        FakeRecipe pick = FakeRecipe.crafting(
                "packai:iron_pickaxe",
                Items.IRON_PICKAXE,
                Ingredient.of(Items.IRON_INGOT),
                Ingredient.of(Items.IRON_INGOT),
                Ingredient.of(Items.IRON_INGOT),
                Ingredient.of(Items.STICK),
                Ingredient.of(Items.STICK));
        ObtainGraph g = ObtainGraph.build(List.of(pick));
        List<List<Item>> groups = g.edgesFor(Items.IRON_PICKAXE).get(0).inputGroups();
        assert groups.size() == 5 : "multi-group size=" + groups.size();
    }

    private static void caseSpecialSkipped() {
        FakeRecipe special = FakeRecipe.crafting(
                "packai:special", Items.DIAMOND, Ingredient.of(Items.DIRT));
        special.special = true;
        ObtainGraph g = ObtainGraph.build(List.of(special));
        assert g.edgeCount() == 0 : "special must be skipped, edges=" + g.edgeCount();
        assert g.edgesFor(Items.DIAMOND).isEmpty();
    }

    private static void caseEmptyResultSkipped() {
        FakeRecipe empty = FakeRecipe.crafting(
                "packai:empty_out", Items.AIR, Ingredient.of(Items.DIRT));
        empty.result = ItemStack.EMPTY;
        ObtainGraph g = ObtainGraph.build(List.of(empty));
        assert g.edgeCount() == 0 : "empty result must be skipped";
    }

    private static void caseBarrierFiltered() {
        // Group is only BARRIER → incomplete → skip recipe.
        FakeRecipe bad = FakeRecipe.crafting(
                "packai:barrier_only", Items.DIAMOND, Ingredient.of(Items.BARRIER));
        ObtainGraph g1 = ObtainGraph.build(List.of(bad));
        assert g1.edgeCount() == 0 : "barrier-only group must skip recipe";

        // Mixed BARRIER + real item → BARRIER dropped, group keeps real item.
        FakeRecipe mixed = FakeRecipe.crafting(
                "packai:barrier_mixed",
                Items.EMERALD,
                Ingredient.of(Items.BARRIER, Items.COBBLESTONE));
        ObtainGraph g2 = ObtainGraph.build(List.of(mixed));
        assert g2.edgeCount() == 1 : g2.edgeCount();
        List<Item> group = g2.edgesFor(Items.EMERALD).get(0).inputGroups().get(0);
        assert !group.contains(Items.BARRIER) : group;
        assert group.contains(Items.COBBLESTONE) : group;
    }

    private static void caseTruncateAfterSort() {
        List<Recipe<?>> recipes = new ArrayList<>();
        // Lexicographic ids: packai:cap_00 .. packai:cap_39 (40 edges for same output)
        for (int i = 0; i < 40; i++) {
            String id = String.format("packai:cap_%02d", i);
            recipes.add(FakeRecipe.crafting(id, Items.STONE, Ingredient.of(Items.COBBLESTONE)));
        }
        // Feed in reverse order so truncation-without-sort would keep wrong ids.
        Collections.reverse(recipes);
        ObtainGraph g = ObtainGraph.build(recipes);
        List<ObtainGraph.Edge> edges = g.edgesFor(Items.STONE);
        assert edges.size() == ObtainGraph.MAX_EDGES_PER_ITEM : edges.size();
        for (int i = 0; i < ObtainGraph.MAX_EDGES_PER_ITEM; i++) {
            String expect = String.format("packai:cap_%02d", i);
            assert edges.get(i).recipeId().toString().equals(expect)
                    : "kept[" + i + "]=" + edges.get(i).recipeId() + " expect " + expect;
        }
        // Negative control: last 8 (lex largest) must NOT be present.
        Set<String> kept = new HashSet<>();
        for (ObtainGraph.Edge e : edges) {
            kept.add(e.recipeId().toString());
        }
        for (int i = 32; i < 40; i++) {
            String id = String.format("packai:cap_%02d", i);
            assert !kept.contains(id) : "lex-large id must be truncated: " + id;
        }
    }

    private static void caseShuffleDeterminism() {
        List<Recipe<?>> all = new ArrayList<>();
        all.add(FakeRecipe.crafting("packai:s_a", Items.APPLE, Ingredient.of(Items.OAK_LEAVES)));
        all.add(FakeRecipe.crafting("packai:s_b", Items.BREAD, Ingredient.of(Items.WHEAT)));
        all.add(FakeRecipe.crafting("packai:s_c", Items.COOKIE, Ingredient.of(Items.COCOA_BEANS)));
        all.add(FakeRecipe.smelting("packai:s_d", Items.IRON_INGOT, Ingredient.of(Items.RAW_IRON)));
        all.add(FakeRecipe.crafting(
                "packai:s_e", Items.CRAFTING_TABLE, Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS)));

        String first = null;
        for (int round = 0; round < 10; round++) {
            List<Recipe<?>> shuffled = new ArrayList<>(all);
            Collections.shuffle(shuffled);
            // Two feed batches in shuffled order.
            ObtainGraph.Builder b = new ObtainGraph.Builder(1L);
            int mid = shuffled.size() / 2;
            b.feed(shuffled.subList(0, mid));
            b.feed(shuffled.subList(mid, shuffled.size()));
            ObtainGraph g = b.finish();
            String fp = g.toString();
            if (first == null) {
                first = fp;
            } else {
                assert first.equals(fp) : "round " + round + " fingerprint mismatch\n" + first + "\n" + fp;
            }
        }
        // Call build twice with different list orders — identical toString.
        List<Recipe<?>> rev = new ArrayList<>(all);
        Collections.reverse(rev);
        assert ObtainGraph.build(all).toString().equals(ObtainGraph.build(rev).toString());
    }

    private static void caseStaleGenerationDropped() {
        FakeRecipe keep = FakeRecipe.crafting(
                "packai:gen_keep", Items.DIAMOND, Ingredient.of(Items.COAL));
        FakeRecipe stale = FakeRecipe.crafting(
                "packai:gen_stale", Items.EMERALD, Ingredient.of(Items.DIRT));
        ObtainGraph.Builder b = new ObtainGraph.Builder(5L);
        b.feed(List.of(keep), 5L);
        b.feed(List.of(stale), 6L); // wrong generation → dropped
        ObtainGraph g = b.finish();
        assert g.edgeCount() == 1 : "stale feed must drop, edges=" + g.edgeCount();
        assert g.edgesFor(Items.DIAMOND).size() == 1;
        assert g.edgesFor(Items.EMERALD).isEmpty() : g.edgesFor(Items.EMERALD);
    }

    /** Minimal Recipe&lt;Container&gt; for harness — no vanilla JSON. */
    static final class FakeRecipe implements Recipe<Container> {
        final ResourceLocation id;
        final RecipeType<?> type;
        ItemStack result;
        final NonNullList<Ingredient> ingredients;
        boolean special;

        private FakeRecipe(
                ResourceLocation id,
                RecipeType<?> type,
                ItemStack result,
                NonNullList<Ingredient> ingredients) {
            this.id = id;
            this.type = type;
            this.result = result;
            this.ingredients = ingredients;
            this.special = false;
        }

        static FakeRecipe crafting(String id, Item out, Ingredient... ings) {
            NonNullList<Ingredient> list = NonNullList.create();
            for (Ingredient ing : ings) {
                list.add(ing);
            }
            return new FakeRecipe(
                    new ResourceLocation(id),
                    RecipeType.CRAFTING,
                    new ItemStack(out),
                    list);
        }

        static FakeRecipe smelting(String id, Item out, Ingredient... ings) {
            NonNullList<Ingredient> list = NonNullList.create();
            for (Ingredient ing : ings) {
                list.add(ing);
            }
            return new FakeRecipe(
                    new ResourceLocation(id),
                    RecipeType.SMELTING,
                    new ItemStack(out),
                    list);
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
            return special;
        }
    }
}

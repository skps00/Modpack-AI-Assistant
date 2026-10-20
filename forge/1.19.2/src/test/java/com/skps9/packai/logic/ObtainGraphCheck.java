package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    /** Mirror {@link ObtainPathText} item labeling for exact expected strings. */
    private static String itemLabel(Item item, String lang) {
        if (item == null) {
            return ReplyLang.tr(lang, "packai.reply.unknown_item");
        }
        try {
            String id = ObtainGraph.itemKey(item);
            String name = OfficialDisplay.officialName(id);
            if (name != null && !name.isBlank() && !OfficialDisplay.isTranslationKeyShaped(name)) {
                return Plainify.stripMcFormat(name);
            }
        } catch (Throwable ignored) {
            // headless / missing lang
        }
        return ReplyLang.tr(lang, "packai.reply.unknown_item");
    }

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
        if (g.edgeCount() != 2) {
            fail("chain edges=" + g.edgeCount());
        }
        if (g.edgesFor(Items.STICK).size() != 1) {
            fail(String.valueOf(g.edgesFor(Items.STICK)));
        }
        if (g.edgesFor(Items.OAK_PLANKS).size() != 1) {
            fail(String.valueOf(g.edgesFor(Items.OAK_PLANKS)));
        }
        List<List<Item>> stickIns = g.edgesFor(Items.STICK).get(0).inputGroups();
        if (!(stickIns.size() == 1 && stickIns.get(0).contains(Items.OAK_PLANKS))) {
            fail(String.valueOf(stickIns));
        }
    }

    private static void caseCycleTerminates() {
        FakeRecipe aFromB = FakeRecipe.crafting(
                "packai:a_from_b", Items.IRON_INGOT, Ingredient.of(Items.GOLD_INGOT));
        FakeRecipe bFromA = FakeRecipe.crafting(
                "packai:b_from_a", Items.GOLD_INGOT, Ingredient.of(Items.IRON_INGOT));
        ObtainGraph g = ObtainGraph.build(List.of(aFromB, bFromA));
        if (g.edgeCount() != 2) {
            fail(String.valueOf(g.edgeCount()));
        }
        List<ObtainPaths.Path> paths = ObtainPaths.find(g, Items.IRON_INGOT, 2);
        if (paths.isEmpty()) {
            fail("cycle must still yield a path");
        }
        if (paths.size() != 1) {
            fail("cycle fixture paths=" + paths.size());
        }
        // Iron←Gold stops at Gold (Iron already on stack) → exactly 1 step.
        if (paths.get(0).steps().size() != 1) {
            fail("cycle fixture steps=" + paths.get(0).steps().size());
        }
    }

    private static void caseAltGroupThreePlanks() {
        Ingredient planks = Ingredient.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS);
        FakeRecipe table = FakeRecipe.crafting("packai:crafting_table", Items.CRAFTING_TABLE, planks);
        ObtainGraph g = ObtainGraph.build(List.of(table));
        List<ObtainGraph.Edge> edges = g.edgesFor(Items.CRAFTING_TABLE);
        if (edges.size() != 1) {
            fail(String.valueOf(edges));
        }
        List<List<Item>> groups = edges.get(0).inputGroups();
        if (groups.size() != 1) {
            fail("alt items must be one group, got " + groups.size());
        }
        if (groups.get(0).size() != 3) {
            fail(String.valueOf(groups.get(0)));
        }
        if (!new HashSet<>(groups.get(0)).containsAll(
                Set.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS))) {
            fail(String.valueOf(groups.get(0)));
        }

        List<ObtainPaths.Path> paths = ObtainPaths.find(g, Items.CRAFTING_TABLE, 1);
        List<ObtainPathText.Line> lines = ObtainPathText.render(paths, "en_us", 12);
        if (lines.isEmpty()) {
            fail(String.valueOf(lines));
        }
        String text = lines.get(0).text();
        // Groups are lex-sorted by itemKey; EXAMPLE_CAP=2 → first two labels + total 3.
        List<Item> sorted = groups.get(0);
        String examples = itemLabel(sorted.get(0), "en_us") + ", " + itemLabel(sorted.get(1), "en_us");
        String inputs = ReplyLang.tr("en_us", "packai.obtain.any_of", examples, Integer.valueOf(3));
        String product = itemLabel(Items.CRAFTING_TABLE, "en_us");
        String expect = ReplyLang.tr("en_us", "packai.obtain.step.craft", product, inputs);
        if (!expect.equals(text)) {
            fail("alt group text got=[" + text + "] expect=[" + expect + "]");
        }
        if (text.contains("#")) {
            fail("must not emit #tag: " + text);
        }
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
        if (groups.size() != 5) {
            fail("multi-group size=" + groups.size());
        }
    }

    private static void caseSpecialSkipped() {
        FakeRecipe special = FakeRecipe.crafting(
                "packai:special", Items.DIAMOND, Ingredient.of(Items.DIRT));
        special.special = true;
        ObtainGraph g = ObtainGraph.build(List.of(special));
        if (g.edgeCount() != 0) {
            fail("special must be skipped, edges=" + g.edgeCount());
        }
        if (!g.edgesFor(Items.DIAMOND).isEmpty()) {
            fail("special diamond edges non-empty");
        }
    }

    private static void caseEmptyResultSkipped() {
        FakeRecipe empty = FakeRecipe.crafting(
                "packai:empty_out", Items.AIR, Ingredient.of(Items.DIRT));
        empty.result = ItemStack.EMPTY;
        ObtainGraph g = ObtainGraph.build(List.of(empty));
        if (g.edgeCount() != 0) {
            fail("empty result must be skipped");
        }
    }

    private static void caseBarrierFiltered() {
        // Group is only BARRIER → incomplete → skip recipe.
        FakeRecipe bad = FakeRecipe.crafting(
                "packai:barrier_only", Items.DIAMOND, Ingredient.of(Items.BARRIER));
        ObtainGraph g1 = ObtainGraph.build(List.of(bad));
        if (g1.edgeCount() != 0) {
            fail("barrier-only group must skip recipe");
        }

        // Mixed BARRIER + real item → BARRIER dropped, group keeps real item.
        FakeRecipe mixed = FakeRecipe.crafting(
                "packai:barrier_mixed",
                Items.EMERALD,
                Ingredient.of(Items.BARRIER, Items.COBBLESTONE));
        ObtainGraph g2 = ObtainGraph.build(List.of(mixed));
        if (g2.edgeCount() != 1) {
            fail(String.valueOf(g2.edgeCount()));
        }
        List<Item> group = g2.edgesFor(Items.EMERALD).get(0).inputGroups().get(0);
        if (group.contains(Items.BARRIER)) {
            fail(String.valueOf(group));
        }
        if (!group.contains(Items.COBBLESTONE)) {
            fail(String.valueOf(group));
        }
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
        if (edges.size() != ObtainGraph.MAX_EDGES_PER_ITEM) {
            fail(String.valueOf(edges.size()));
        }
        for (int i = 0; i < ObtainGraph.MAX_EDGES_PER_ITEM; i++) {
            String expect = String.format("packai:cap_%02d", i);
            if (!edges.get(i).recipeId().toString().equals(expect)) {
                fail("kept[" + i + "]=" + edges.get(i).recipeId() + " expect " + expect);
            }
        }
        // Negative control: last 8 (lex largest) must NOT be present.
        Set<String> kept = new HashSet<>();
        for (ObtainGraph.Edge e : edges) {
            kept.add(e.recipeId().toString());
        }
        for (int i = 32; i < 40; i++) {
            String id = String.format("packai:cap_%02d", i);
            if (kept.contains(id)) {
                fail("lex-large id must be truncated: " + id);
            }
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
            String fp = structureKey(g);
            if (first == null) {
                first = fp;
            } else if (!first.equals(fp)) {
                fail("round " + round + " structure mismatch\n" + first + "\n" + fp);
            }
        }
        // Call build twice with different list orders — identical edge structure.
        List<Recipe<?>> rev = new ArrayList<>(all);
        Collections.reverse(rev);
        if (!structureKey(ObtainGraph.build(all)).equals(structureKey(ObtainGraph.build(rev)))) {
            fail("reverse feed structure mismatch");
        }
    }

    /** Deterministic edge map key (no eager fingerprint field on ObtainGraph). */
    private static String structureKey(ObtainGraph g) {
        StringBuilder sb = new StringBuilder();
        sb.append("edges=").append(g.edgeCount());
        List<Item> items = new ArrayList<>(g.byOutput().keySet());
        items.sort(Comparator.comparing(ObtainGraph::itemKey));
        for (Item item : items) {
            sb.append('|').append(ObtainGraph.itemKey(item)).append(':');
            List<ObtainGraph.Edge> edges = g.edgesFor(item);
            for (int i = 0; i < edges.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                ObtainGraph.Edge e = edges.get(i);
                sb.append(e.recipeId()).append('@').append(e.typeId());
            }
        }
        return sb.toString();
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
        if (g.edgeCount() != 1) {
            fail("stale feed must drop, edges=" + g.edgeCount());
        }
        if (g.edgesFor(Items.DIAMOND).size() != 1) {
            fail("keep edge missing");
        }
        if (!g.edgesFor(Items.EMERALD).isEmpty()) {
            fail(String.valueOf(g.edgesFor(Items.EMERALD)));
        }
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

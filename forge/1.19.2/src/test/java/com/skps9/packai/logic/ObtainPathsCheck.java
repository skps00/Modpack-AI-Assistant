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

    private static String formatGroups(List<List<Item>> groups, String lang) {
        if (groups == null || groups.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(formatGroup(groups.get(i), lang));
        }
        return sb.toString();
    }

    private static String formatGroup(List<Item> group, String lang) {
        if (group == null || group.isEmpty()) {
            return "";
        }
        if (group.size() == 1) {
            return itemLabel(group.get(0), lang);
        }
        StringBuilder examples = new StringBuilder();
        int n = Math.min(2, group.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                examples.append(", ");
            }
            examples.append(itemLabel(group.get(i), lang));
        }
        return ReplyLang.tr(
                lang, "packai.obtain.any_of", examples.toString(), Integer.valueOf(group.size()));
    }

    private static String expectStepText(ObtainPaths.Step step, String lang) {
        ObtainGraph.Edge edge = step.edge();
        String product = itemLabel(edge.output(), lang);
        String inputs = formatGroups(step.groups() != null ? step.groups() : edge.inputGroups(), lang);
        String typePath = edge.typeId() != null ? edge.typeId().getPath() : "";
        if ("crafting".equals(typePath)
                || "crafting_shaped".equals(typePath)
                || "crafting_shapeless".equals(typePath)) {
            return ReplyLang.tr(lang, "packai.obtain.step.craft", product, inputs);
        }
        if ("smelting".equals(typePath)
                || "blasting".equals(typePath)
                || "smoking".equals(typePath)
                || "campfire_cooking".equals(typePath)) {
            return ReplyLang.tr(lang, "packai.obtain.step.smelt", product, inputs);
        }
        String machine = edge.actionLabel() != null && !edge.actionLabel().isBlank()
                ? edge.actionLabel()
                : ReplyLang.tr(lang, "packai.obtain.machine_generic");
        return ReplyLang.tr(lang, "packai.obtain.step.machine", product, inputs, machine);
    }

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
        caseIdenticalAltSkipped();
        caseZhTwLocalizedPath();

        System.out.println("ObtainPathsCheck OK");
    }

    private static void caseCycleStops() {
        List<Recipe<?>> recipes = List.of(
                fake("packai:cyc_a", Items.IRON_INGOT, Items.GOLD_INGOT),
                fake("packai:cyc_b", Items.GOLD_INGOT, Items.IRON_INGOT));
        ObtainGraph g = ObtainGraph.build(recipes);
        List<ObtainPaths.Path> paths = ObtainPaths.find(g, Items.IRON_INGOT, 2);
        if (paths.isEmpty()) {
            fail("cycle must terminate with a path");
        }
        int totalSteps = 0;
        for (ObtainPaths.Path p : paths) {
            totalSteps += p.steps().size();
            if (p.steps().size() > ObtainPaths.MAX_DEPTH) {
                fail(String.valueOf(p.steps().size()));
            }
        }
        if (totalSteps >= 100) {
            fail("cycle must not explode steps=" + totalSteps);
        }
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
        if (paths.size() != 1) {
            fail(String.valueOf(paths.size()));
        }
        if (paths.get(0).complete()) {
            fail("depth 7 must be incomplete leaf");
        }
        if (paths.get(0).steps().size() != ObtainPaths.MAX_DEPTH) {
            fail("steps must == MAX_DEPTH got " + paths.get(0).steps().size());
        }
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
        if (paths.size() != 1) {
            fail(String.valueOf(paths.size()));
        }
        if (!paths.get(0).complete()) {
            fail("6-edge leaf must be complete");
        }
        if (paths.get(0).steps().size() != ObtainPaths.MAX_DEPTH) {
            fail("steps must == MAX_DEPTH got " + paths.get(0).steps().size());
        }
    }

    private static void caseMaxPathsTwo() {
        List<Recipe<?>> recipes = List.of(
                fake("packai:mp_a", Items.STONE_BRICKS, Items.STONE),
                fake("packai:mp_b", Items.STONE_BRICKS, Items.COBBLESTONE),
                fake("packai:mp_c", Items.STONE_BRICKS, Items.SMOOTH_STONE));
        ObtainGraph g = ObtainGraph.build(recipes);
        List<ObtainPaths.Path> paths = ObtainPaths.find(g, Items.STONE_BRICKS, 2);
        if (paths.size() != 2) {
            fail("maxPaths=2 got " + paths.size());
        }
        List<ObtainPaths.Path> one = ObtainPaths.find(g, Items.STONE_BRICKS, 1);
        if (one.size() != 1) {
            fail(String.valueOf(one.size()));
        }
    }

    private static void caseNoEdgesEmpty() {
        ObtainGraph g = ObtainGraph.build(List.of());
        List<ObtainPaths.Path> paths = ObtainPaths.find(g, Items.BEDROCK, 2);
        if (!paths.isEmpty()) {
            fail(String.valueOf(paths));
        }
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
        if (paths.isEmpty()) {
            fail("need paths for budget cases");
        }

        // Uncapped full size for fixture (bypass MAX_INSERT via counting steps).
        int rawSteps = 0;
        for (ObtainPaths.Path p : paths) {
            rawSteps += p.steps().size();
            if (!p.complete()) {
                rawSteps++;
            }
        }
        if (paths.size() < 2) {
            fail("need 2 paths for truncate cases, got " + paths.size());
        }
        if (rawSteps < 4) {
            fail("fixture need >=4 raw steps for budget-3 truncate, got " + rawSteps);
        }

        ObtainPaths.Step firstStep = paths.get(0).steps().get(0);
        String expectFirst = expectStepText(firstStep, "en_us");

        if (!ObtainPathText.render(paths, "en_us", 0).isEmpty()) {
            fail("budget 0");
        }

        List<ObtainPathText.Line> b1 = ObtainPathText.render(paths, "en_us", 1);
        if (b1.size() != 1) {
            fail("budget 1 got " + b1);
        }
        if (!expectFirst.equals(b1.get(0).text())) {
            fail("budget 1 first got=[" + b1.get(0).text() + "] expect=[" + expectFirst + "]");
        }

        List<ObtainPathText.Line> b2 = ObtainPathText.render(paths, "en_us", 2);
        if (b2.size() != 2) {
            fail("budget 2 got " + b2);
        }
        if (!expectFirst.equals(b2.get(0).text())) {
            fail("budget 2 first got=[" + b2.get(0).text() + "] expect=[" + expectFirst + "]");
        }
        if (!b2.get(1).key().equals("M|too_many")) {
            fail(String.valueOf(b2));
        }

        List<ObtainPathText.Line> b3 = ObtainPathText.render(paths, "en_us", 3);
        if (b3.size() != 3) {
            fail("budget 3 = 2 content + too_many, got " + b3);
        }
        if (!expectFirst.equals(b3.get(0).text())) {
            fail("budget 3 first got=[" + b3.get(0).text() + "] expect=[" + expectFirst + "]");
        }
        if (!b3.get(2).key().equals("M|too_many")) {
            fail(String.valueOf(b3));
        }

        List<ObtainPathText.Line> capped = ObtainPathText.render(paths, "en_us", 12);
        if (capped.size() > ObtainPathText.MAX_INSERT_LINES) {
            fail("budget 12 must respect MAX_INSERT_LINES got " + capped.size());
        }
        if (capped.isEmpty()) {
            fail(String.valueOf(capped));
        }
        if (!expectFirst.equals(capped.get(0).text())) {
            fail("budget 12 first got=[" + capped.get(0).text() + "] expect=[" + expectFirst + "]");
        }
        if (capped.get(0).text().contains("#")) {
            fail(capped.get(0).text());
        }
        if (capped.get(0).text().contains("packai:bud_")) {
            fail(capped.get(0).text());
        }
    }

    /** Crafting edge preferred over stonecutter even when stonecutter id sorts first. */
    private static void caseCraftBeatsStonecutter() {
        List<Recipe<?>> recipes = List.of(
                Fake.stonecutting("packai:a_cut", Items.STONE_BRICKS, Ingredient.of(Items.STONE)),
                Fake.crafting("packai:z_craft", Items.STONE_BRICKS, Ingredient.of(Items.COBBLESTONE)));
        ObtainGraph g = ObtainGraph.build(recipes);
        List<ObtainGraph.Edge> edges = g.edgesFor(Items.STONE_BRICKS);
        if (edges.size() != 2) {
            fail(String.valueOf(edges));
        }
        if (!edges.get(0).typeId().getPath().equals("crafting")) {
            fail("craft must sort first: " + edges.get(0).typeId());
        }
        List<ObtainPaths.Path> paths = ObtainPaths.find(g, Items.STONE_BRICKS, 1);
        if (paths.size() != 1) {
            fail(String.valueOf(paths));
        }
        if (!paths.get(0).steps().get(0).edge().recipeId().toString().equals("packai:z_craft")) {
            fail("chosen path must be crafting: " + paths.get(0).steps().get(0).edge().recipeId());
        }
    }

    /** Sole ingredient group contains the output itself → edge dropped → no path. */
    private static void caseVariantConversionNoPath() {
        Ingredient variants = Ingredient.of(Items.STONE_BRICKS, Items.MOSSY_STONE_BRICKS);
        List<Recipe<?>> recipes = List.of(
                Fake.stonecutting("packai:variant_only", Items.STONE_BRICKS, variants));
        ObtainGraph g = ObtainGraph.build(recipes);
        if (!g.edgesFor(Items.STONE_BRICKS).isEmpty()) {
            fail("variant conversion must not add edge");
        }
        if (!ObtainPaths.find(g, Items.STONE_BRICKS, 2).isEmpty()) {
            fail("no path");
        }
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
        if (members.length <= ObtainPaths.MAX_EXPAND_GROUP) {
            fail("fixture need > MAX_EXPAND_GROUP members");
        }

        List<ObtainPaths.Path> forward = pathWithLargeGroup(members);
        if (forward.size() != 1) {
            fail(String.valueOf(forward));
        }
        if (forward.get(0).complete()) {
            fail("large group skip must be incomplete (deeper unconfirmed)");
        }
        if (forward.get(0).steps().size() != 1) {
            fail("must not expand into group member, steps=" + forward.get(0).steps().size());
        }

        Item[] reversed = new Item[members.length];
        for (int i = 0; i < members.length; i++) {
            reversed[i] = members[members.length - 1 - i];
        }
        List<ObtainPaths.Path> backward = pathWithLargeGroup(reversed);
        if (backward.size() != 1) {
            fail(String.valueOf(backward));
        }
        if (backward.get(0).complete()) {
            fail(String.valueOf(backward));
        }
        if (backward.get(0).steps().size() != 1) {
            fail("order must not change expand skip, steps=" + backward.get(0).steps().size());
        }
        if (!forward.get(0).steps().get(0).edge().recipeId()
                .equals(backward.get(0).steps().get(0).edge().recipeId())) {
            fail("same leaf recipe regardless of member order");
        }
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

    /**
     * Two recipes with identical render text → primary kept; no {@code or:} alt line.
     */
    private static void caseIdenticalAltSkipped() {
        List<Recipe<?>> recipes = List.of(
                Fake.crafting("packai:same_a", Items.STICK, Ingredient.of(Items.OAK_PLANKS)),
                Fake.crafting("packai:same_b", Items.STICK, Ingredient.of(Items.OAK_PLANKS)));
        ObtainGraph g = ObtainGraph.build(recipes);
        if (g.edgesFor(Items.STICK).size() < 2) {
            fail("need two edges for identical-alt fixture");
        }
        List<ObtainPaths.Path> paths = ObtainPaths.find(g, Items.STICK, 2);
        if (paths.size() != 2) {
            fail("need primary + identical alt, got " + paths.size());
        }
        List<ObtainPathText.Line> rendered = ObtainPathText.render(paths, "en_us", 12);
        if (rendered.isEmpty()) {
            fail("primary must render");
        }
        for (ObtainPathText.Line ln : rendered) {
            if (ln == null || ln.text() == null || ln.text().isBlank()) {
                fail(String.valueOf(ln));
            }
            // Content craft/smelt/machine lines use ASCII "->" (en_us template / Plainify).
            if (!"M|too_many".equals(ln.key()) && !"M|partial".equals(ln.key())) {
                if (!ln.text().contains(" -> ")) {
                    fail("content line must contain ASCII arrow: " + ln.text());
                }
            }
            if (ln.text().trim().startsWith("or:")) {
                fail("identical alt must not emit or: line: " + ln.text());
            }
        }
    }

    /**
     * zh_tw localized craft + any_of shapes via {@link ObtainPathText#render}.
     * Lang template uses U+2192; player UI may ASCII-ify via {@link Plainify#forMinecraftUi}.
     */
    private static void caseZhTwLocalizedPath() {
        Ingredient alts = Ingredient.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS);
        List<Recipe<?>> recipes = List.of(
                Fake.crafting("packai:zh_table", Items.CRAFTING_TABLE, alts));
        ObtainGraph g = ObtainGraph.build(recipes);
        List<ObtainPaths.Path> paths = ObtainPaths.find(g, Items.CRAFTING_TABLE, 1);
        if (paths.size() != 1) {
            fail("zh_tw fixture paths=" + paths.size());
        }
        List<ObtainPathText.Line> lines = ObtainPathText.render(paths, "zh_tw", 12);
        if (lines.isEmpty()) {
            fail("zh_tw render empty");
        }
        String text = lines.get(0).text();
        String expect = expectStepText(paths.get(0).steps().get(0), "zh_tw");
        if (!expect.equals(text)) {
            fail("zh_tw craft got=[" + text + "] expect=[" + expect + "]");
        }
        if (!text.contains("合成：")) {
            fail("zh_tw must use craft prefix: " + text);
        }
        // Observed shape from packai.obtain.any_of (examples joined with ", " in Java).
        List<Item> group = paths.get(0).steps().get(0).groups().get(0);
        String examples =
                itemLabel(group.get(0), "zh_tw") + ", " + itemLabel(group.get(1), "zh_tw");
        String anyOf = ReplyLang.tr(
                "zh_tw", "packai.obtain.any_of", examples, Integer.valueOf(group.size()));
        if (!text.contains(anyOf)) {
            fail("zh_tw any_of missing; text=[" + text + "] anyOf=[" + anyOf + "]");
        }
        // Report-friendly: renderer keeps lang U+2192; Plainify.forMinecraftUi → ASCII.
        if (!text.contains("→") && !text.contains("->")) {
            fail("zh_tw step missing arrow: " + text);
        }
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

package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.skps9.packai.client.routes.RouteStoreCodec;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Harness for the material-count chain (C1-C3) and the route-tree builder (B1-B3).
 * Headless: no Minecraft bootstrap needed for pickCount / StepDto / RouteTreeBuilder.
 */
public final class RouteCountsCheck {
    private RouteCountsCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    private static void eq(Object want, Object got, String what) {
        if (want == null ? got != null : !want.equals(got)) {
            fail(what + ": want=" + want + " got=" + got);
        }
    }

    private static RouteStoreCodec.InputDto dto(String id, int count) {
        return new RouteStoreCodec.InputDto(id, count);
    }

    private static RouteStoreCodec.StepDto step(String output, List<RouteStoreCodec.InputDto> items) {
        return new RouteStoreCodec.StepDto(
                "packai:test/" + output, "minecraft:crafting", output, "Crafting Table",
                items.stream().map(RouteStoreCodec.InputDto::id).toList(),
                List.of(), List.of(), items);
    }

    // C1: pickCount rule (empty -> 1, else max).
    private static void casePickCount() {
        eq(1, RecipeIo.pickCount(new int[0]), "pickCount(empty)");
        eq(4, RecipeIo.pickCount(new int[] {1, 4}), "pickCount({1,4})");
        eq(1, RecipeIo.pickCount(new int[] {1, 1}), "pickCount({1,1})");
        eq(1, RecipeIo.pickCount(new int[] {0, 0}), "pickCount({0,0}) must floor at 1");
        eq(64, RecipeIo.pickCount(new int[] {64}), "pickCount({64})");
    }

    // C2: StepDto roundtrip keeps inputItems.
    private static void caseStepDtoRoundtrip() {
        RouteStoreCodec.SavedRoute route = new RouteStoreCodec.SavedRoute(
                "packai:root", "2026-10-23T00:00:00Z", true, "mods",
                List.of(step("packai:root", List.of(dto("minecraft:iron_ingot", 4), dto("minecraft:coal", 2)))));
        String json = RouteStoreCodec.encode(List.of(route));
        List<RouteStoreCodec.SavedRoute> back = RouteStoreCodec.decode(json);
        eq(1, back.size(), "roundtrip route count");
        List<RouteStoreCodec.StepDto> steps = back.get(0).steps();
        eq(1, steps.size(), "roundtrip step count");
        eq(2, steps.get(0).inputItems().size(), "inputItems size");
        eq("minecraft:iron_ingot", steps.get(0).inputItems().get(0).id(), "inputItems[0].id");
        eq(4, steps.get(0).inputItems().get(0).count(), "inputItems[0].count");
        eq(2, steps.get(0).inputItems().get(1).count(), "inputItems[1].count");
    }

    // C2b: literal legacy JSON (no inputItems key, or null) must decode without dropping inputs[].
    private static void caseLegacyJsonMissingInputItemsField() {
        String jsonMissingField =
                "[{\"itemId\":\"packai:legacy_root\",\"createdAt\":\"2026-01-01T00:00:00Z\",\"complete\":true,"
                        + "\"modsStamp\":\"m\",\"steps\":[{\"recipeId\":\"packai:r\",\"typeId\":\"minecraft:crafting\","
                        + "\"output\":\"packai:legacy_root\",\"inputs\":[\"packai:a\",\"packai:b\"],"
                        + "\"actionLabel\":\"Crafting Table\",\"fluids\":[],\"fluidSources\":[]}]}]";
        assertLegacyDecodeShape(jsonMissingField, "missing inputItems key");

        String jsonNullField =
                "[{\"itemId\":\"packai:legacy_root\",\"createdAt\":\"2026-01-01T00:00:00Z\",\"complete\":true,"
                        + "\"modsStamp\":\"m\",\"steps\":[{\"recipeId\":\"packai:r\",\"typeId\":\"minecraft:crafting\","
                        + "\"output\":\"packai:legacy_root\",\"inputs\":[\"packai:a\",\"packai:b\"],"
                        + "\"inputItems\":null,\"actionLabel\":\"Crafting Table\",\"fluids\":[],\"fluidSources\":[]}]}]";
        assertLegacyDecodeShape(jsonNullField, "inputItems null");
    }

    private static void assertLegacyDecodeShape(String json, String label) {
        List<RouteStoreCodec.SavedRoute> routes = RouteStoreCodec.decode(json);
        eq(1, routes.size(), label + " route count");
        List<RouteStoreCodec.StepDto> steps = routes.get(0).steps();
        eq(1, steps.size(), label + " step count");
        RouteStoreCodec.StepDto step0 = steps.get(0);
        eq(List.of("packai:a", "packai:b"), step0.inputs(), label + " inputs preserved");
        if (step0.inputItems() == null) {
            fail(label + " inputItems must not be null");
        }
        eq(0, step0.inputItems().size(), label + " inputItems empty list");

        RouteTreeData.Tree tree = RouteTreeBuilder.build("packai:legacy_root", steps);
        List<RouteTreeData.Node> children = tree.nodes().stream()
                .filter(n -> n.depth() == 1)
                .toList();
        eq(2, children.size(), label + " child node count");
        for (RouteTreeData.Node child : children) {
            eq(1, child.count(), label + " child count for " + child.itemId());
        }
    }

    // C3 + B1: counts sum per item id, depth/parent, pre-order.
    private static void caseBuilderCountsAndDepth() {
        RouteStoreCodec.StepDto root = step("packai:block", List.of(dto("packai:ball", 1), dto("packai:ball", 4)));
        RouteStoreCodec.StepDto child = step("packai:ball", List.of(dto("packai:clay", 2)));
        RouteTreeData.Tree tree = RouteTreeBuilder.build("packai:block", List.of(root, child));
        eq(3, tree.nodes().size(), "node count");
        RouteTreeData.Node n0 = tree.nodes().get(0);
        eq("packai:block", n0.itemId(), "root id");
        eq(0, n0.depth(), "root depth");
        eq(-1, n0.parentIndex(), "root parent");
        RouteTreeData.Node n1 = tree.nodes().get(1);
        eq("packai:ball", n1.itemId(), "child id");
        eq(1, n1.depth(), "child depth");
        eq(0, n1.parentIndex(), "child parent");
        eq(5, n1.count(), "child count must sum 1+4=5");
        RouteTreeData.Node n2 = tree.nodes().get(2);
        eq("packai:clay", n2.itemId(), "grandchild id");
        eq(2, n2.depth(), "grandchild depth");
        eq(2, n2.count(), "grandchild count");
    }

    // B2: cycle detection without runaway growth.
    private static void caseBuilderCycle() {
        RouteStoreCodec.StepDto a = step("packai:a", List.of(dto("packai:b", 1)));
        RouteStoreCodec.StepDto b = step("packai:b", List.of(dto("packai:a", 1)));
        RouteTreeData.Tree tree = RouteTreeBuilder.build("packai:a", List.of(a, b));
        boolean sawCycle = tree.nodes().stream()
                .anyMatch(n -> n.status() == RouteTreeData.Status.CYCLE);
        if (!sawCycle) {
            fail("expected a CYCLE node, got " + tree.nodes());
        }
        if (tree.nodes().size() > 8) {
            fail("cycle blew up to " + tree.nodes().size() + " nodes");
        }
    }

    // C4: graph cache edge roundtrip keeps inputCounts; legacy JSON without field → all 1, edge kept.
    private static void caseEdgeInputCountsRoundtrip() {
        List<List<Item>> groups = List.of(List.of(Items.CLAY_BALL), List.of(Items.SAND));
        List<Integer> counts = List.of(4, 2);
        ObtainGraph.Edge edge = new ObtainGraph.Edge(
                new ResourceLocation("packai", "c4"),
                new ResourceLocation("minecraft", "crafting"),
                Items.BRICK,
                1,
                groups,
                counts,
                null,
                List.of(),
                List.of(),
                false);
        Map<Item, List<ObtainGraph.Edge>> byOutput = Map.of(Items.BRICK, List.of(edge));
        Set<Item> known = Set.of(Items.BRICK, Items.CLAY_BALL, Items.SAND);
        ObtainGraph graph =
                ObtainGraph.fromCached(byOutput, Map.of(), known, 1, 0, 0, 0, 0, 0, false);

        JsonObject root = ObtainGraphCache.encodeGraph(graph, "c4fp", 1);
        ObtainGraph back = ObtainGraphCache.decodeGraph(root);
        if (back == null) {
            fail("C4 decode null");
        }
        ObtainGraph.Edge backEdge = back.edgesFor(Items.BRICK).get(0);
        eq(counts, backEdge.inputCounts(), "C4 roundtrip inputCounts");

        root.getAsJsonArray("edges").get(0).getAsJsonObject().remove("inputCounts");
        ObtainGraph legacy = ObtainGraphCache.decodeGraph(root);
        if (legacy == null) {
            fail("C4 legacy decode null");
        }
        if (legacy.edgesFor(Items.BRICK).isEmpty()) {
            fail("C4 legacy edge lost");
        }
        ObtainGraph.Edge legacyEdge = legacy.edgesFor(Items.BRICK).get(0);
        eq(List.of(1, 1), legacyEdge.inputCounts(), "C4 legacy inputCounts default to 1");
    }

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
            public ResourceLocation getId() {
                return new ResourceLocation("packai", "route_counts_c5");
            }

            @Override
            public RecipeType<?> getType() {
                return RecipeType.CRAFTING;
            }
        };
    }

    // C5: recipe stack count → inputGroupsWithCounts → path → SavedRoute inputItems.
    private static void caseRecipeToSavedRouteCounts() {
        Ingredient four = Ingredient.of(new ItemStack(Items.CLAY_BALL, 4));
        Recipe<?> recipe = fakeRecipe(List.of(four));
        List<RecipeIo.Group> groups = RecipeIo.inputGroupsWithCounts(recipe);
        List<List<Item>> inputGroups = new ArrayList<>();
        List<Integer> inputCounts = new ArrayList<>();
        for (RecipeIo.Group g : groups) {
            inputGroups.add(List.copyOf(g.items()));
            inputCounts.add(g.count());
        }
        ObtainGraph.Edge edge = new ObtainGraph.Edge(
                new ResourceLocation("packai", "c5"),
                new ResourceLocation("minecraft", "crafting"),
                Items.BRICK,
                1,
                inputGroups,
                inputCounts,
                "Crafting Table",
                List.of(),
                List.of(),
                false);
        ObtainPaths.Step pathStep = new ObtainPaths.Step(edge, inputGroups, List.of(), inputCounts);
        ObtainPaths.Path path = new ObtainPaths.Path(Items.BRICK, List.of(pathStep), true);
        RouteStoreCodec.SavedRoute saved = RouteStoreCodec.fromPath(
                "minecraft:brick", "2026-10-23T00:00:00Z", "stamp", path);
        if (saved.steps().isEmpty()) {
            fail("C5 no steps");
        }
        RouteStoreCodec.StepDto dto = saved.steps().get(0);
        boolean foundClay = false;
        for (RouteStoreCodec.InputDto in : dto.inputItems()) {
            if ("minecraft:clay_ball".equals(in.id())) {
                eq(4, in.count(), "C5 clay_ball count");
                foundClay = true;
            }
        }
        if (!foundClay) {
            fail("C5 clay_ball missing from inputItems: " + dto.inputItems());
        }
    }

    // B3: a material with no producing step is NOT_IN_ROUTE (never "no recipe").
    private static void caseBuilderNotInRoute() {
        RouteStoreCodec.StepDto root = step("packai:block", List.of(dto("minecraft:sand", 4)));
        RouteTreeData.Tree tree = RouteTreeBuilder.build("packai:block", List.of(root));
        RouteTreeData.Node sand = tree.nodes().get(1);
        eq("minecraft:sand", sand.itemId(), "sand id");
        eq(RouteTreeData.Status.NOT_IN_ROUTE, sand.status(), "sand status");
        eq(-1, sand.stepIndex(), "sand stepIndex");
    }

    public static void main(String[] args) {
        casePickCount();
        caseStepDtoRoundtrip();
        caseLegacyJsonMissingInputItemsField();
        caseBuilderCountsAndDepth();
        caseBuilderCycle();
        caseBuilderNotInRoute();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        caseEdgeInputCountsRoundtrip();
        caseRecipeToSavedRouteCounts();
        System.out.println("check RouteCountsCheck OK");
    }
}

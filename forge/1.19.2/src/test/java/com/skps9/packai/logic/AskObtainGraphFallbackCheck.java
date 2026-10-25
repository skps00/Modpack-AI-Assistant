package com.skps9.packai.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.skps9.packai.config.PackAiConfig;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** JEI output=0 → ObtainGraph obtain-path fallback at focus prefetch. Run with -ea. */
public final class AskObtainGraphFallbackCheck {
    private AskObtainGraphFallbackCheck() {}

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bindConfig();
        try {
            jeiZeroGraphTwoLines();
            jeiOneSkipsGraph();
            graphNotReadyUnchanged();
            moreThanSixStableSort();
            negativeControlDisabled();
        } finally {
            AskFocusPrefetch.resetJeiForTest();
            AskFocusPrefetch.setEnabledForTest(true);
            AskFocusPrefetch.lastPlacements.clear();
            AskObtainGraphFallback.resetForTest();
            ObtainGraphRuntime.harnessClearGraph();
        }
        System.out.println("AskObtainGraphFallbackCheck OK");
    }

    private static void jeiZeroGraphTwoLines() {
        String itemId = "minecraft:egg";
        ObtainGraph graph = graphForEggTwoEdges();
        ObtainGraphRuntime.harnessInstallGraph(graph);

        AskFocusPrefetch.setJeiForTest((stack, maxOut, maxIn, deadline) -> List.of());

        AskLoopState loop = AskLoopState.start("how to get", itemId, List.of(), System.currentTimeMillis() + 60_000L);
        loop.setLang("en_us");
        AskToolEnv env = new AskToolEnv(ItemStack.EMPTY, null, null, List.of(), ItemRef.NONE);

        AskFocusPrefetch.run(loop, itemId, env, loop.deadlineMs());

        assert loop.catalogCards().size() == 2 : loop.catalogCards();
        assert loop.recipeCardLines().size() == 2 : loop.recipeCardLines();
        for (String line : loop.recipeCardLines()) {
            assert line.contains("role=output") : line;
            assert line.contains("→") : line;
        }
        assert AskObtainGraphFallback.lastGraphLogged == 2 : AskObtainGraphFallback.lastGraphLogged;
        assert AskObtainGraphFallback.lastPlacements.size() == 2 : AskObtainGraphFallback.lastPlacements;
        for (String p : AskObtainGraphFallback.lastPlacements) {
            assert AskObtainGraphFallback.PLACEMENT.equals(p) : p;
        }
        ObtainGraphRuntime.harnessClearGraph();
        System.out.println("jeiZeroGraphTwoLines OK");
    }

    private static void jeiOneSkipsGraph() {
        String itemId = "minecraft:stone";
        ObtainGraph graph = graphForEggTwoEdges();
        ObtainGraphRuntime.harnessInstallGraph(graph);

        RecipeCard jeiOut = RecipeCard.flow(
                "Crafting",
                List.of(new ItemStack(Items.DIRT)),
                List.of(),
                List.of(new ItemStack(Items.STONE)),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ).withSourceItemId(itemId).withFocusRole(RecipeCard.FocusRole.OUTPUT);

        AskFocusPrefetch.setJeiForTest((stack, maxOut, maxIn, deadline) -> {
            if (maxIn == 0) {
                return List.of(jeiOut);
            }
            return List.of();
        });

        AskLoopState loop = AskLoopState.start("q", itemId, List.of(), System.currentTimeMillis() + 60_000L);
        AskToolEnv env = new AskToolEnv(ItemStack.EMPTY, null, null, List.of(), ItemRef.NONE);
        AskFocusPrefetch.run(loop, itemId, env, loop.deadlineMs());

        assert loop.catalogCards().size() == 1 : loop.catalogCards();
        assert AskObtainGraphFallback.lastGraphLogged == -1 : AskObtainGraphFallback.lastGraphLogged;
        ObtainGraphRuntime.harnessClearGraph();
        System.out.println("jeiOneSkipsGraph OK");
    }

    private static void graphNotReadyUnchanged() {
        String itemId = "minecraft:egg";
        ObtainGraphRuntime.harnessClearGraph();
        AskFocusPrefetch.setJeiForTest((stack, maxOut, maxIn, deadline) -> List.of());

        AskLoopState loop = AskLoopState.start("q", itemId, List.of(), System.currentTimeMillis() + 60_000L);
        AskToolEnv env = new AskToolEnv(ItemStack.EMPTY, null, null, List.of(), ItemRef.NONE);
        AskFocusPrefetch.run(loop, itemId, env, loop.deadlineMs());

        assert loop.catalogCards().isEmpty() : loop.catalogCards();
        assert AskObtainGraphFallback.lastGraphLogged == -1 : AskObtainGraphFallback.lastGraphLogged;
        System.out.println("graphNotReadyUnchanged OK");
    }

    private static void moreThanSixStableSort() {
        String itemId = "minecraft:egg";
        List<ObtainGraph.Edge> edges = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            edges.add(edge(
                    "packai:sort_" + i,
                    Items.EGG,
                    10 - i,
                    List.of(List.of(Items.HONEYCOMB)),
                    "Cat " + i));
        }
        ObtainGraph graph = singleOutputGraph(Items.EGG, edges);
        ObtainGraphRuntime.harnessInstallGraph(graph);
        AskFocusPrefetch.setJeiForTest((stack, maxOut, maxIn, deadline) -> List.of());

        List<String> run1 = runPrefetchLines(itemId);
        List<String> run2 = runPrefetchLines(itemId);
        assert run1.size() == 6 : run1;
        assert run1.equals(run2) : run1 + " vs " + run2;
        assert run1.get(0).contains("Cat 0") : run1.get(0);
        ObtainGraphRuntime.harnessClearGraph();
        System.out.println("moreThanSixStableSort OK");
    }

    private static List<String> runPrefetchLines(String itemId) {
        AskObtainGraphFallback.lastPlacements.clear();
        AskObtainGraphFallback.lastGraphLogged = -1;
        AskLoopState loop = AskLoopState.start("q", itemId, List.of(), System.currentTimeMillis() + 60_000L);
        loop.setLang("en_us");
        AskToolEnv env = new AskToolEnv(ItemStack.EMPTY, null, null, List.of(), ItemRef.NONE);
        AskFocusPrefetch.run(loop, itemId, env, loop.deadlineMs());
        return loop.recipeCardLines();
    }

    private static void negativeControlDisabled() {
        AskObtainGraphFallback.setEnabledForTest(false);
        String itemId = "minecraft:egg";
        ObtainGraphRuntime.harnessInstallGraph(graphForEggTwoEdges());
        AskFocusPrefetch.setJeiForTest((stack, maxOut, maxIn, deadline) -> List.of());

        AskLoopState loop = AskLoopState.start("q", itemId, List.of(), System.currentTimeMillis() + 60_000L);
        AskToolEnv env = new AskToolEnv(ItemStack.EMPTY, null, null, List.of(), ItemRef.NONE);
        AskFocusPrefetch.run(loop, itemId, env, loop.deadlineMs());

        assert loop.catalogCards().isEmpty() : "fallback disabled must not add cards";
        assert AskObtainGraphFallback.lastGraphLogged == -1 : AskObtainGraphFallback.lastGraphLogged;
        System.out.println("negativeControlDisabled OK");
    }

    private static ObtainGraph graphForEggTwoEdges() {
        ObtainGraph.Edge a = edge(
                "productivebees:incubation_a",
                Items.EGG,
                1,
                List.of(List.of(Items.HONEYCOMB)),
                "Incubation · BaBee Incubator");
        ObtainGraph.Edge b = edge(
                "minecraft:craft_egg",
                Items.EGG,
                1,
                List.of(List.of(Items.WHEAT_SEEDS)),
                "Crafting");
        return singleOutputGraph(Items.EGG, List.of(a, b));
    }

    private static ObtainGraph.Edge edge(
            String recipeId,
            Item output,
            int outputCount,
            List<List<Item>> inputs,
            String actionLabel
    ) {
        return new ObtainGraph.Edge(
                new ResourceLocation(recipeId),
                new ResourceLocation("minecraft", "crafting"),
                output,
                outputCount,
                inputs,
                actionLabel,
                List.of(),
                false);
    }

    private static ObtainGraph singleOutputGraph(Item output, List<ObtainGraph.Edge> edges) {
        Map<Item, List<ObtainGraph.Edge>> byOutput = Map.of(output, List.copyOf(edges));
        Set<Item> known = Set.of(output, Items.HONEYCOMB, Items.WHEAT_SEEDS);
        return ObtainGraph.fromCached(byOutput, Map.of(), known, edges.size(), 0, 0, 0, 0, 0, false);
    }

    private static void bindConfig() throws Exception {
        Path toml = Path.of(System.getProperty("java.io.tmpdir"), "packai-obtain-graph-fallback-check.toml");
        Files.createDirectories(toml.getParent());
        CommentedFileConfig cfg = CommentedFileConfig.builder(toml).sync().autosave().build();
        cfg.load();
        PackAiConfig.SPEC.setConfig(cfg);
    }
}

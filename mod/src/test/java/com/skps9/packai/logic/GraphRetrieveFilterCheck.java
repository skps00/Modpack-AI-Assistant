package com.skps9.packai.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Related-graph retrieve: only seed-neighborhood facts, skip raw clips when facts suffice.
 */
public final class GraphRetrieveFilterCheck {
    private GraphRetrieveFilterCheck() {}

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("packai-graph-filter");
        Path js = root.resolve("kubejs/server_scripts/recipes.js");
        Files.createDirectories(js.getParent());
        Files.writeString(js, """
                event.shaped('minecraft:diamond', ['AAA','A A','AAA'], { A: 'minecraft:coal' })
                event.shaped('minecraft:stick', ['A','A'], { A: 'minecraft:bamboo' })
                """);

        PackIndex idx = new PackIndex();
        idx.build(root, List.of("kubejs"));

        var hit = idx.retrieve("如何做鑽石", "minecraft:diamond", List.of());
        assert hit.graphFacts().stream().anyMatch(f -> f.contains("minecraft:diamond") && f.contains("recipe_needs"))
                : "diamond facts missing: " + hit.graphFacts();
        assert hit.graphFacts().stream().noneMatch(f -> f.contains("minecraft:stick"))
                : "unrelated stick fact leaked: " + hit.graphFacts();
        assert hit.snippets().isEmpty() : "expected no raw clips when facts cover ask";

        Set<String> seeds = PackIndex.seedItemIds(null, List.of(), List.of("kubejs:furnace_core"));
        assert seeds.contains("kubejs:furnace_core") : seeds;

        var empty = idx.retrieve("隨便問問天氣", null, List.of());
        assert empty.graphFacts().isEmpty() : "no seed should not dump whole graph: " + empty.graphFacts();

        System.out.println("GraphRetrieveFilterCheck OK");
    }
}

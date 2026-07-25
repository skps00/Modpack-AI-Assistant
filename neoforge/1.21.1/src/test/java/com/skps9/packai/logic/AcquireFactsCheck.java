package com.skps9.packai.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Runnable check: loot / trade / shaped become acquire facts beyond JEI.
 */
public final class AcquireFactsCheck {
    private AcquireFactsCheck() {}

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("packai-acquire");
        Path loot = root.resolve("datapacks/pack/data/minecraft/loot_table/chests/bonus_box.json");
        Files.createDirectories(loot.getParent());
        Files.writeString(loot, """
                {
                  "pools": [{
                    "entries": [
                      { "type": "minecraft:item", "name": "minecraft:diamond" },
                      { "type": "minecraft:item", "name": "create:andesite_alloy" }
                    ]
                  }]
                }
                """);

        Path trade = root.resolve("datapacks/pack/data/minecraft/villager_trades/weaponsmith.json");
        Files.createDirectories(trade.getParent());
        Files.writeString(trade, """
                { "sells": [ { "id": "minecraft:diamond_sword" } ] }
                """);

        Path js = root.resolve("kubejs/server_scripts/recipes.js");
        Files.createDirectories(js.getParent());
        Files.writeString(js, """
                event.shaped('minecraft:diamond', [
                  'AAA',
                  'A A',
                  'AAA'
                ], { A: 'minecraft:coal' })
                """);

        Path fish = root.resolve("datapacks/pack/data/minecraft/loot_table/gameplay/fishing/treasure.json");
        Files.createDirectories(fish.getParent());
        Files.writeString(fish, """
                { "pools": [{ "entries": [ { "type": "minecraft:item", "name": "minecraft:nautilus_shell" } ] }] }
                """);

        PackIndex idx = new PackIndex();
        idx.build(root, List.of("kubejs", "datapacks"));

        assert PackIndex.isLootPath("datapacks/x/loot_table/chests/a.json");
        assert PackIndex.isTradePath("datapacks/x/villager_trades/y.json");
        assert PackIndex.isFishingPath("datapacks/x/loot_table/gameplay/fishing/treasure.json");

        List<String> shell = idx.acquireFactsFor("minecraft:nautilus_shell");
        assert shell.stream().anyMatch(s -> s.contains("釣魚")) : "expected fishing: " + shell;

        List<String> diamondLoot = idx.acquireFactsFor("minecraft:diamond");
        assert diamondLoot.stream().anyMatch(s -> s.contains("掉落")) : "expected loot: " + diamondLoot;

        var retrieved = idx.retrieve("如何做鑽石", "minecraft:diamond", List.of("minecraft"));
        assert retrieved.graphFacts().stream()
                .anyMatch(f -> f.contains("minecraft:diamond") && f.contains("recipe_needs"))
                : "shaped not ingested: " + retrieved.graphFacts();

        List<String> diamondAll = idx.acquireFactsFor("minecraft:diamond");
        assert diamondAll.stream().anyMatch(s -> s.contains("腳本配方")) : "expected recipe: " + diamondAll;

        List<String> sword = idx.acquireFactsFor("minecraft:diamond_sword");
        assert sword.stream().anyMatch(s -> s.contains("交易")) : "expected trade: " + sword;

        idx.ingestGraph("kubejs/test.js", "event.shapeless('mod:out', ['mod:a', 'mod:b'])");
        assert idx.acquireFactsFor("mod:out").stream().anyMatch(s -> s.contains("腳本配方"))
                : "shapeless acquire";

        idx.ingestGraph("kubejs/compact.js", """
                event.shaped('minecraft:iron_block', ['AAA','AAA','AAA'], { A: 'minecraft:iron_ingot' })
                event.shapeless('minecraft:iron_ingot', ['minecraft:iron_block'])
                """);
        List<String> ingot = idx.acquireFactsFor("minecraft:iron_ingot");
        assert ingot.stream().anyMatch(s -> s.contains("壓縮循環")) : "expected compact cycle: " + ingot;
        assert PackIndex.looksLikeStoragePair("minecraft:iron_ingot", "minecraft:iron_block");
        assert PackIndex.isCompactCycle(
                "minecraft:iron_ingot", "minecraft:iron_block", idx.recipeNeedsIndex());

        System.out.println("AcquireFactsCheck OK");
    }
}

package com.skps9.packai.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Related-graph retrieve: seed-neighborhood facts; nearby clips; PURPOSE keeps kubejs when thin.
 */
public final class GraphRetrieveFilterCheck {
    private GraphRetrieveFilterCheck() {}

    public static void main(String[] args) throws Exception {
        // Unit: clip centers on hit, not file head.
        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            pad.append("// PAD_LINE_").append(i).append('\n');
        }
        String deep = pad + "ItemEvents.foodEaten('kubejs:miracle_milk', e => {\n"
                + "  e.player.tell('soul_mana')\n"
                + "})\n";
        String near = PackIndex.clipNearMatch(deep, List.of("kubejs:miracle_milk"));
        assert near.contains("miracle_milk") : near;
        assert near.contains("soul_mana") : near;
        assert !near.contains("PAD_LINE_0") : "expected nearby clip, not file head: " + near;

        assert PackIndex.shouldSkipSnippets(
                "如何做鑽石",
                List.of("item:minecraft:diamond -[recipe_needs]-> item:minecraft:coal"),
                Set.of("minecraft:diamond"));
        assert !PackIndex.shouldSkipSnippets(
                "告訴我鑽石",
                List.of("item:minecraft:diamond -[recipe_needs]-> item:minecraft:coal"),
                Set.of("minecraft:diamond"))
                : "general ask + single recipe fact must keep snippets";
        assert PackIndex.shouldSkipSnippets(
                "告訴我鑽石",
                List.of(
                        "item:minecraft:diamond -[recipe_needs]-> item:minecraft:coal",
                        "item:minecraft:diamond -[loot]-> chest"),
                Set.of("minecraft:diamond"));
        assert !PackIndex.shouldSkipSnippets(
                "這個有什麼用",
                List.of("item:kubejs:miracle_milk -[recipe_needs]-> item:minecraft:milk_bucket"),
                Set.of("kubejs:miracle_milk"))
                : "PURPOSE ask with only recipe fact must keep snippets";
        assert PackIndex.shouldSkipSnippets(
                "這個有什麼用",
                List.of("item:kubejs:miracle_milk -[desc]-> restores soul"),
                Set.of("kubejs:miracle_milk"));
        assert !PackIndex.shouldSkipSnippets(
                "check it's code",
                List.of("item:kubejs:miracle_milk -[desc]-> restores soul"),
                Set.of("kubejs:miracle_milk"))
                : "code ask must keep kubejs clips";
        assert PackIndex.isCodeOrBehaviorQuestion("check it's code");
        assert PackIndex.isCodeOrBehaviorQuestion("看一下原理");
        assert !PackIndex.shouldAttachAskRecipeCards("check it's code");
        assert PackIndex.shouldAttachAskRecipeCards("如何做鑽石");
        assert !PackIndex.shouldAttachAskRecipeCards("魔力转化器")
                : "bare item name must not auto-attach recipe cards";
        assert !PackIndex.shouldAttachAskRecipeCards("tetra 工作台放什麼")
                : "placement ask must not attach crafting cards";
        assert PackIndex.shouldAttachAskRecipeCards("怎么合成这个");
        assert PackIndex.shouldAttachAskRecipeCards("how to get iron");
        assert PackIndex.shouldAttachAskRecipeCards("这个有什么用")
                : "purpose ask may attach INPUT use cards";

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
        assert hit.snippets().isEmpty() : "expected no raw clips when facts cover craft ask";

        Path milkRoot = Files.createTempDirectory("packai-purpose-clip");
        Path milkJs = milkRoot.resolve("kubejs/server_scripts/food.js");
        Files.createDirectories(milkJs.getParent());
        StringBuilder milkPad = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            milkPad.append("// noise ").append(i).append('\n');
        }
        milkPad.append("ItemEvents.foodEaten('kubejs:miracle_milk', e => {\n");
        milkPad.append("  // drink restores soul\n");
        milkPad.append("})\n");
        Files.writeString(milkJs, milkPad.toString());
        PackIndex milkIdx = new PackIndex();
        milkIdx.build(milkRoot, List.of("kubejs"));
        var purpose = milkIdx.retrieve("这个有什么用", "kubejs:miracle_milk", List.of());
        assert !purpose.snippets().isEmpty() : "PURPOSE ask must keep kubejs nearby clip";
        String snip = purpose.snippets().get(0);
        assert snip.contains("miracle_milk") : snip;
        assert snip.contains("foodEaten") || snip.contains("drink restores soul") : snip;
        assert !snip.contains("// noise 0") : "clip should be near hit, not file head: " + snip;

        Set<String> seeds = PackIndex.seedItemIds(null, List.of(), List.of("kubejs:furnace_core"));
        assert seeds.contains("kubejs:furnace_core") : seeds;

        var empty = idx.retrieve("隨便問問天氣", null, List.of());
        assert empty.graphFacts().isEmpty() : "no seed should not dump whole graph: " + empty.graphFacts();

        // Seed ask must not ingest unrelated kubejs scripts just because focusMods=kubejs.
        Path lazyRoot = Files.createTempDirectory("packai-lazy-seed");
        Path noiseJs = lazyRoot.resolve("kubejs/server_scripts/noise.js");
        Path hitJs = lazyRoot.resolve("kubejs/server_scripts/diamond.js");
        Files.createDirectories(noiseJs.getParent());
        Files.writeString(noiseJs, """
                event.shaped('minecraft:stick', ['A','A'], { A: 'minecraft:bamboo' })
                """);
        Files.writeString(hitJs, """
                event.shaped('minecraft:diamond', ['AAA','A A','AAA'], { A: 'minecraft:coal' })
                """);
        PackIndex lazyIdx = new PackIndex();
        lazyIdx.build(lazyRoot, List.of("kubejs"));
        var lazy = lazyIdx.retrieve("如何做", "minecraft:diamond", List.of("kubejs"));
        assert lazy.graphFacts().stream().anyMatch(f -> f.contains("minecraft:diamond"))
                : "diamond script should ingest: " + lazy.graphFacts();
        assert lazy.graphFacts().stream().noneMatch(f -> f.contains("minecraft:stick"))
                : "unrelated stick script must not ingest on diamond ask: " + lazy.graphFacts();
        assert PackIndex.bodyMentionsSeed("x minecraft:diamond y", Set.of("minecraft:diamond"));
        assert !PackIndex.bodyMentionsSeed("only sticks here", Set.of("minecraft:diamond"));
        assert PackIndex.bodyMentionsSeed(
                "event.create('random_delivery_agreement').finishUsing",
                Set.of("kubejs:random_delivery_agreement"))
                : "bare create id must match seed path";
        assert !PackIndex.bodyMentionsSeed("create('scrap')", Set.of("kubejs:random_delivery_agreement"));
        assert PackIndex.pathHintsSeed("kubejs/data/minecraft/diamond.js", "minecraft:diamond");
        assert "create".equals(PackIndex.namespaceOf("create:wrench"));

        // create().finishUsing → script_use fact + PURPOSE nearby clip (NFWC random delivery).
        Path deliveryRoot = Files.createTempDirectory("packai-delivery-use");
        Path deliveryJs = deliveryRoot.resolve("kubejs/startup_scripts/item_register.js");
        Files.createDirectories(deliveryJs.getParent());
        StringBuilder deliveryPad = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            deliveryPad.append("// noise ").append(i).append('\n');
        }
        deliveryPad.append("""
                event.create('random_delivery_agreement').texture('kubejs:item/random_delivery_agreement')
                    .maxStackSize(16)
                    .useAnimation('bow')
                    .use((level, player, hand) => { return true; })
                    .useDuration(itemStack => 20)
                    .finishUsing((itemstack, level, entity) => {
                        if (level.isClientSide()) return itemstack
                        let wares = global.getRandomWare()
                        entity.give(wares)
                        itemstack.shrink(1)
                        return itemstack
                    })
                """);
        Files.writeString(deliveryJs, deliveryPad.toString());
        PackIndex deliveryIdx = new PackIndex();
        deliveryIdx.build(deliveryRoot, List.of("kubejs"));
        assert deliveryIdx.descFactsFor("kubejs:random_delivery_agreement").stream()
                .anyMatch(f -> f.contains("script_use") && f.contains("finish_using")
                        && f.contains("getRandomWare"))
                : deliveryIdx.descFactsFor("kubejs:random_delivery_agreement");
        var deliveryAsk = deliveryIdx.retrieve("这个有什么用", "kubejs:random_delivery_agreement", List.of());
        assert deliveryAsk.graphFacts().stream()
                .anyMatch(f -> f.contains("script_use") && f.contains("getRandomWare"))
                : "PURPOSE facts missing: " + deliveryAsk.graphFacts();
        assert !deliveryAsk.snippets().isEmpty() : "PURPOSE must keep finishUsing clip";
        String dSnip = deliveryAsk.snippets().get(0);
        assert dSnip.contains("finishUsing") && dSnip.contains("getRandomWare") : dSnip;
        assert !dSnip.contains("// noise 0") : "clip should be near create id, not file head: " + dSnip;

        System.out.println("GraphRetrieveFilterCheck OK");
    }
}

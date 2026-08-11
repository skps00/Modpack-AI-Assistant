package com.skps9.packai.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Generic KubeJS {@code create().finishUsing}/{@code .use} → script_use → PURPOSE
 * (not item-specific; delivery is one fixture among others).
 */
public final class ItemCreateUseCheck {
    private ItemCreateUseCheck() {}

    public static void main(String[] args) throws Exception {
        assert PackIndex.bodyMentionsSeed(
                "event.create('random_delivery_agreement').finishUsing",
                Set.of("kubejs:random_delivery_agreement"));
        assert !PackIndex.bodyMentionsSeed("create('scrap')", Set.of("kubejs:random_delivery_agreement"));

        // Shape A: finishUsing + dynamic helper (NFWC delivery-like).
        List<String> dynamicFacts = PackIndex.parseItemCreateUseFacts("""
                event.create('random_delivery_agreement')
                    .use((level, player, hand) => { return true; })
                    .finishUsing((itemstack, level, entity) => {
                        let wares = global.getRandomWare()
                        entity.give(wares)
                        itemstack.shrink(1)
                        return itemstack
                    })
                """);
        assert dynamicFacts.stream().anyMatch(f ->
                f.contains("item:kubejs:random_delivery_agreement")
                        && f.contains("script_use")
                        && f.contains("via:finish_using")
                        && f.contains("call:getRandomWare")) : dynamicFacts;
        assert dynamicFacts.stream().allMatch(AskPurposeContext::isPurposeGraphFact)
                : "script_use must be PURPOSE graph fact: " + dynamicFacts;

        // Shape B: finishUsing + literal give (different item / chain).
        List<String> literalFacts = PackIndex.parseItemCreateUseFacts("""
                event.create('demo:loot_token')
                    .finishUsing((itemstack, level, entity) => {
                        entity.give(Item.of('minecraft:diamond'))
                        return itemstack
                    })
                """);
        assert literalFacts.stream().anyMatch(f ->
                f.contains("item:demo:loot_token")
                        && f.contains("script_use")
                        && f.contains("via:finish_using")
                        && f.contains("gets:minecraft:diamond")) : literalFacts;
        assert AskPurposeContext.isPurposeGraphFact(literalFacts.get(0));

        // Shape C: namespaced create + .use only (no finishUsing) still indexes.
        List<String> useFacts = PackIndex.parseItemCreateUseFacts("""
                event.create('kubejs:snack_box')
                    .use((level, player, hand) => {
                        player.give('minecraft:bread')
                        return true
                    })
                """);
        assert useFacts.stream().anyMatch(f ->
                f.contains("item:kubejs:snack_box")
                        && f.contains("script_use")
                        && f.contains("gets:minecraft:bread")) : useFacts;

        // ItemEvents.rightClicked + give(randomGet) → PURPOSE right_click_use (generic; not item-hardcoded)
        List<String> randomClick = PackIndex.parseRightClickFacts("""
                ItemEvents.rightClicked('kubejs:demo_random_trinket', event => {
                    event.item.shrink(1)
                    event.player.give(randomGet(trinketList))
                })
                """);
        assert randomClick.stream().anyMatch(f ->
                f.contains("item:kubejs:demo_random_trinket")
                        && f.contains("-[drops]-> random")) : randomClick;
        assert randomClick.stream().anyMatch(f ->
                f.contains("item:kubejs:demo_random_trinket")
                        && f.contains("-[right_click_use]->")
                        && f.contains("gets:random")) : randomClick;
        assert randomClick.stream().filter(f -> f.contains("right_click_use"))
                .allMatch(AskPurposeContext::isPurposeGraphFact) : randomClick;

        assert "kubejs:scrap".equals(PackIndex.resolveCreateItemId("scrap"));
        assert !AskPurposeContext.isPurposeGraphFact("item:x -[recipe_needs]-> item:y");

        // #5: food().eaten → script_use PURPOSE
        List<String> eaten = PackIndex.parseItemCreateUseFacts("""
                event.create('lucky_cookie').food(food => {
                    food.hunger(1).eaten(event => {
                        event.player.give(Item.of('kubejs:lucky_cookie_organ'))
                    })
                })
                """);
        assert eaten.stream().anyMatch(f ->
                f.contains("item:kubejs:lucky_cookie")
                        && f.contains("script_use")
                        && f.contains("via:food_eaten")
                        && f.contains("gets:kubejs:lucky_cookie_organ")) : eaten;
        assert eaten.stream().allMatch(AskPurposeContext::isPurposeGraphFact) : eaten;

        // #5: LootJS → loot acquire (not PURPOSE)
        List<String> loot = PackIndex.parseLootJsFacts("""
                LootJS.modifiers(event => {
                    event.addEntityLootModifier('minecraft:slime')
                        .addLoot(LootEntry.of('kubejs:mini_slime'));
                })
                """);
        assert loot.stream().anyMatch(f ->
                f.contains("item:kubejs:mini_slime")
                        && f.contains("-[loot]->")
                        && f.contains("via:lootjs")
                        && f.contains("entity:minecraft:slime")) : loot;
        assert loot.stream().noneMatch(AskPurposeContext::isPurposeGraphFact) : loot;

        Path root = Files.createTempDirectory("packai-create-use");
        Path js = root.resolve("kubejs/startup_scripts/item_register.js");
        Files.createDirectories(js.getParent());
        Files.writeString(js, """
                event.create('random_delivery_agreement')
                    .use((level, player, hand) => { return true; })
                    .finishUsing((itemstack, level, entity) => {
                        let wares = global.getRandomWare()
                        entity.give(wares)
                        return itemstack
                    })
                event.create('demo_box')
                    .finishUsing((itemstack, level, entity) => {
                        entity.give(Item.of('minecraft:apple'))
                        return itemstack
                    })
                """);
        PackIndex idx = new PackIndex();
        idx.build(root, List.of("kubejs"));
        assert idx.descFactsFor("kubejs:random_delivery_agreement").stream()
                .anyMatch(f -> f.contains("getRandomWare"))
                : idx.descFactsFor("kubejs:random_delivery_agreement");
        assert idx.descFactsFor("kubejs:demo_box").stream()
                .anyMatch(f -> f.contains("script_use") && f.contains("minecraft:apple"))
                : idx.descFactsFor("kubejs:demo_box");
        var ask = idx.retrieve("这个有什么用", "kubejs:random_delivery_agreement", List.of());
        assert ask.graphFacts().stream().anyMatch(f -> f.contains("script_use") && f.contains("getRandomWare"))
                : "PURPOSE retrieve missing script_use: " + ask.graphFacts();
        assert ask.graphFacts().stream().filter(f -> f.contains("script_use"))
                .allMatch(AskPurposeContext::isPurposeGraphFact);
        assert !ask.snippets().isEmpty() : ask;
        assert ask.snippets().get(0).contains("finishUsing") : ask.snippets();

        String purpose = AskPurposeContext.buildPurposeBlock(
                "",
                List.of("Use (hold to use): use this item → get getRandomWare"));
        assert purpose.contains(AskPurposeContext.PURPOSE_HEADER);
        assert purpose.contains("getRandomWare");

        System.out.println("ItemCreateUseCheck OK");
    }
}

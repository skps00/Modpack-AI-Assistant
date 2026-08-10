package com.skps9.packai.logic;

import java.util.List;

/**
 * Heavy-script corpus checks: dispatch maps, tick narrow, dynamic drops, tags, conditions.
 */
public final class HeavyScriptChecks {
    private HeavyScriptChecks() {}

    public static void main(String[] args) {
        // Event dispatch: map used from ItemEvents.rightClicked
        List<String> dispatch = ItemDescFacts.parse("""
                ItemEvents.rightClicked(event => {
                    organRightClickedOnlyStrategies[organ.id](event, organ)
                })
                const organRightClickedOnlyStrategies = {
                    'kubejs:revolution_steam_engine': function (event, organ) {
                        event.player.give(Item.of('minecraft:glass_bottle'))
                    },
                };
                """, k -> null);
        assert dispatch.stream().anyMatch(f -> f.contains("gives:minecraft:glass_bottle")
                && f.contains("on:item_right_clicked")) : dispatch;

        // Narrow tick: empty body → no interact facts; give → keep
        List<String> tickEmpty = PackIndex.parseRightClickFacts("""
                PlayerEvents.tick(event => {
                    let player = event.player
                    if (!player) return
                })
                """);
        assert tickEmpty.isEmpty() : tickEmpty;

        List<String> tickGive = PackIndex.parseRightClickFacts("""
                PlayerEvents.tick(event => {
                    if (event.player.age % 100 == 0) {
                        event.player.give('minecraft:apple')
                    }
                })
                """);
        assert tickGive.stream().anyMatch(f -> f.contains("apple") && f.contains("via:tick")) : tickGive;

        // Dynamic drops
        List<String> lucky = PackIndex.parseRightClickFacts("""
                BlockEvents.broken('kubejs:lucky_block', event => {
                    event.block.popItemFromFace(getLuckyBlockRandomLoot(), event.entity.facing)
                })
                """);
        assert lucky.stream().anyMatch(f -> f.contains("-[drops]-> random")
                && f.contains("kubejs:lucky_block")) : lucky;

        // Tag held
        List<String> tag = PackIndex.parseRightClickFacts("""
                BlockEvents.rightClicked('minecraft:sand', event => {
                    if (event.item.hasTag('kubejs:lung')) {
                        event.player.giveInHand(Item.of('kubejs:ore_lung'))
                    }
                })
                """);
        assert tag.stream().anyMatch(f -> f.contains("held:#kubejs:lung")
                && f.contains("ore_lung")) : tag;

        // Thunder + stage conditions
        List<String> cond = PackIndex.parseRightClickFacts("""
                BlockEvents.rightClicked('minecraft:diamond_block', event => {
                    if (event.level.isThundering() && event.player.stages.has('flos_magic_stage_1')) {
                        event.player.give(Item.of('kubejs:diamond_bottle'))
                    }
                })
                """);
        assert cond.stream().anyMatch(f -> f.contains("diamond_bottle") && f.contains("if:thunder")
                && f.contains("if:stage:flos_magic_stage_1")) : cond;

        // Startup create().finishUsing → script_use (not ItemEvents.rightClicked)
        List<String> createUse = PackIndex.parseItemCreateUseFacts("""
                event.create('random_delivery_agreement')
                    .use((level, player, hand) => { return true; })
                    .finishUsing((itemstack, level, entity) => {
                        let wares = global.getRandomWare()
                        entity.give(wares)
                        itemstack.shrink(1)
                        return itemstack
                    })
                """);
        assert createUse.stream().anyMatch(f ->
                f.contains("item:kubejs:random_delivery_agreement")
                        && f.contains("script_use")
                        && f.contains("via:finish_using")
                        && f.contains("call:getRandomWare")) : createUse;
        assert createUse.stream().allMatch(AskPurposeContext::isPurposeGraphFact) : createUse;

        List<String> literalCreate = PackIndex.parseItemCreateUseFacts("""
                event.create('demo:loot_token')
                    .finishUsing((itemstack, level, entity) => {
                        entity.give(Item.of('minecraft:diamond'))
                        return itemstack
                    })
                """);
        assert literalCreate.stream().anyMatch(f ->
                f.contains("item:demo:loot_token")
                        && f.contains("script_use")
                        && f.contains("gets:minecraft:diamond")) : literalCreate;
        assert AskPurposeContext.isPurposeGraphFact(literalCreate.get(0));

        assert "kubejs:scrap".equals(PackIndex.resolveCreateItemId("scrap"));
        assert "mod:foo".equals(PackIndex.resolveCreateItemId("mod:foo"));

        System.out.println("HeavyScriptChecks OK");
    }
}

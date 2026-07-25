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

        System.out.println("HeavyScriptChecks OK");
    }
}

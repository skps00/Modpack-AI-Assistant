package com.skps9.packai.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Item description facts from tooltip / organ / strategy scripts + lang JSON.
 */
public final class ItemDescFactsCheck {
    private ItemDescFactsCheck() {}

    public static void main(String[] args) throws Exception {
        // Unit: parser resolves keys
        List<String> parsed = ItemDescFacts.parse("""
                RegistryOrganTooltip(new MultiStateTooltip('kubejs:furnace_core')
                    .addDefault(Text.translatable('tooltips.kubejs.furnace_core.default.1').gray())
                    .addAlt(Text.translatable('tooltips.kubejs.furnace_core.alt.1'))
                )
                RegistryOrgan('kubejs:furnace_core')
                    .addScore('chestcavity:health', 1.5)
                RegistryOrganStrategy(
                    new OrganStrategyModel('kubejs:furnace_core')
                        .addOnlyStrategy('entity_tick', fn)
                        .addOnlyStrategy('key_active', fn2)
                )
                """, Map.of(
                "tooltips.kubejs.furnace_core.default.1", "裝在胸腔可積蓄熱力",
                "tooltips.kubejs.furnace_core.alt.1", "右鍵煤炭觸發燃燒之心"
        )::get);
        assert parsed.stream().anyMatch(f -> f.contains("-[desc]->") && f.contains("積蓄熱力")) : parsed;
        assert parsed.stream().anyMatch(f -> f.contains("-[score]->") && f.contains("health=1.5")) : parsed;
        assert parsed.stream().anyMatch(f -> f.contains("-[triggers]-> entity_tick")) : parsed;
        assert parsed.stream().anyMatch(f -> f.contains("-[triggers]-> key_active")) : parsed;

        // NFWC1-style Organ builder
        List<String> organ = ItemDescFacts.parse("""
                registerOrgan(new Organ('kubejs:greedy_stomach')
                    .addScore('digestion', 0.5)
                    .addTextLines('alt', [Text.translatable("kubejs.tooltips.greedy_stomach.1")])
                    .build())
                """, Map.of("kubejs.tooltips.greedy_stomach.1", "吃東西有機率掉寶")::get);
        assert organ.stream().anyMatch(f -> f.contains("greedy_stomach") && f.contains("掉寶")) : organ;
        assert organ.stream().anyMatch(f -> f.contains("digestion=0.5")) : organ;

        // Index build: lang + script
        Path root = Files.createTempDirectory("packai-desc");
        Path lang = root.resolve("kubejs/assets/kubejs/lang/zh_cn.json");
        Files.createDirectories(lang.getParent());
        Files.writeString(lang, """
                {
                  "tooltips.kubejs.demo.1": "示範器官說明文字"
                }
                """);
        Path tip = root.resolve("kubejs/client_scripts/tooltips.js");
        Files.createDirectories(tip.getParent());
        Files.writeString(tip, """
                RegistryOrganTooltip(new MultiStateTooltip('kubejs:demo')
                  .addDefault(Text.translatable('tooltips.kubejs.demo.1')))
                """);

        PackIndex idx = new PackIndex();
        idx.build(root, List.of("kubejs"));
        List<String> facts = idx.descFactsFor("kubejs:demo");
        assert facts.stream().anyMatch(f -> f.contains("示範器官")) : facts;

        var retrieved = idx.retrieve("kubejs:demo 有什麼用", "kubejs:demo", List.of());
        assert retrieved.graphFacts().stream().anyMatch(f -> f.contains("-[desc]->")) : retrieved.graphFacts();
        assert PackIndex.isPurposeQuestion("這個器官有什麼用");
        assert PackIndex.isPurposeQuestion("how does this organ work");

        // block.set(air) must not become interact target when filter is diamond_block
        List<String> interact = PackIndex.parseRightClickFacts("""
                BlockEvents.rightClicked('minecraft:diamond_block', event => {
                  event.block.set('minecraft:air')
                  event.player.give(Item.of('kubejs:diamond_bottle'))
                })
                """);
        assert interact.stream().anyMatch(f -> f.contains("diamond_bottle") && f.contains("diamond_block"))
                : interact;
        assert interact.stream().noneMatch(f -> f.contains("block:minecraft:air")) : interact;

        // NFWC1-style strategy map: give glass bottle
        List<String> steam = ItemDescFacts.parse("""
                const organRightClickedOnlyStrategies = {
                    'kubejs:revolution_steam_engine': function (event, organ) {
                        if (event.item != Item.of('minecraft:potion', '{Potion:"minecraft:water"}')) {
                            return
                        }
                        event.item.shrink(1)
                        event.player.give(Item.of('minecraft:glass_bottle'))
                    },
                    'kubejs:furnace_core': function (event, organ) {
                        event.player.potionEffects.add('kubejs:burning_heart', 100, 0);
                    },
                };
                """, k -> null);
        assert steam.stream().anyMatch(f -> f.contains("revolution_steam_engine")
                && f.contains("-[on:item_right_clicked]->") && f.contains("gives:minecraft:glass_bottle")) : steam;
        assert steam.stream().anyMatch(f -> f.contains("furnace_core")
                && f.contains("effect:kubejs:burning_heart")) : steam;

        // NFWC2-style: addOnlyStrategy + named fn with replaceItem
        List<String> nfwc2 = ItemDescFacts.parse("""
                function FurnaceCoreEntityTickDefer(customData, event, organItem, organIndex, slotType) {
                    let replaceItem = Item.of('kubejs:burning_heart')
                    organItem = replaceItem
                }
                RegistryOrganStrategy(
                    new OrganStrategyModel('kubejs:furnace_core')
                        .addOnlyStrategy('entity_tick', FurnaceCoreEntityTickDefer, -1)
                )
                """, k -> null);
        assert nfwc2.stream().anyMatch(f -> f.contains("-[triggers]-> entity_tick")) : nfwc2;
        assert nfwc2.stream().anyMatch(f -> f.contains("-[on:entity_tick]->")
                && f.contains("becomes:kubejs:burning_heart")) : nfwc2;

        // Missing fn: still keep triggers, no crash
        List<String> missing = ItemDescFacts.parse("""
                new OrganStrategyModel('kubejs:demo_organ')
                    .addOnlyStrategy('key_active', MissingFnNeverDefined)
                """, k -> null);
        assert missing.stream().anyMatch(f -> f.contains("-[triggers]-> key_active")) : missing;
        assert missing.stream().noneMatch(f -> f.contains("-[on:")) : missing;

        System.out.println("ItemDescFactsCheck OK");
    }
}

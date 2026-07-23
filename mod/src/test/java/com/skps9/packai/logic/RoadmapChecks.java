package com.skps9.packai.logic;

import java.util.List;

import com.skps9.packai.client.context.SeasonContext;

/** Runnable checks for item marker parsing and craft priority. */
public final class RoadmapChecks {
    private RoadmapChecks() {}

    public static void main(String[] args) {
        String raw = "好的，用自動攪拌機<!--packai:items=evilcraft:environmental_accumulator,minecraft:dirt-->";
        assert ItemResolver.stripMarker(raw).equals("好的，用自動攪拌機");
        assert ItemResolver.extractIds(raw).contains("evilcraft:environmental_accumulator");

        assert CraftPriority.categoryTier("Crafting Table") < CraftPriority.categoryTier("Automatic Stirrer");
        assert CraftPriority.categoryTier("Crafting Table") < CraftPriority.categoryTier("Some Machine Processing");
        assert CraftPriority.isQuestCategory("Quest Rewards");
        assert !CraftPriority.isQuestCategory("Crafting Table");
        // No brand-specific tiers (Create / Mekanism / …) — unknown titles share default band
        assert CraftPriority.categoryTier("Create Mixing") == CraftPriority.categoryTier("Mekanism Crusher");
        assert ReplySources.ensure("hello", List.of("JEI"), "zh_tw").contains("【來源】JEI");
        assert ReplySources.ensure("hello", List.of("JEI"), "en_us").contains("[Sources] JEI");
        assert ReplySources.ensure("done\n\n【來源】JEI", List.of("任務書"), "zh_tw").equals("done\n\n【來源】JEI");
        assert ReplySources.build(true, false, false, false, false, "en_us").contains("JEI");
        assert ReplyLang.relatedQuest("book", "en_us").equals("book related quest");
        assert ReplyLang.relatedQuest("book", "zh_tw").equals("book相關任務");

        String kube = """
                BlockEvents.rightClicked('minecraft:dirt', event => {
                  if (event.item.id == 'minecraft:stick') {
                    event.player.give('minecraft:diamond')
                  }
                })
                """;
        var interact = PackIndex.parseRightClickFacts(kube);
        assert interact.stream().anyMatch(f ->
                f.contains("minecraft:diamond -[right_click]->")
                        && f.contains("held:minecraft:stick")
                        && f.contains("block:minecraft:dirt")
                        && f.contains("via:right_click"));
        assert interact.stream().anyMatch(f -> f.startsWith("item:minecraft:stick -[right_click_use]->"));

        String entity = """
                ItemEvents.entityInteracted('minecraft:bucket', event => {
                  if (event.target.type == 'minecraft:cow') {
                    event.player.giveInHand('minecraft:milk_bucket')
                  }
                })
                """;
        assert PackIndex.parseRightClickFacts(entity).stream().anyMatch(f ->
                f.contains("milk_bucket") && f.contains("entity:minecraft:cow") && f.contains("via:entity"));

        String legacy = """
                onEvent('block.right_click', event => {
                  if (event.block.id == 'minecraft:stone' && event.item.id == 'minecraft:flint') {
                    event.player.give('minecraft:iron_nugget')
                  }
                })
                """;
        assert PackIndex.parseRightClickFacts(legacy).stream().anyMatch(f -> f.contains("iron_nugget"));

        assert QuestGuide.displayTitle(
                new QuestGuide.Hit("c", "", "", "x", List.of("minecraft:book"), 0, false, "1", "ftbquests"),
                "en_us").contains("related quest");

        assert !SeasonContext.applies(List.of("minecraft", "create"), "how to craft iron", null);
        assert !SeasonContext.applies(List.of("sereneseasons"), "how to craft iron", null);
        assert SeasonContext.applies(List.of("sereneseasons"), "現在能種番茄嗎", null);
        assert SeasonContext.applies(List.of("sereneseasons", "farmersdelight"), "usage", "farmersdelight:tomato_seeds");
        assert PsiHelper.promptAddon("how to craft", List.of("psi")).isEmpty();
        assert PsiHelper.promptAddon("psi 術式", List.of("minecraft")).isEmpty();
        assert !PsiHelper.promptAddon("psi 術式", List.of("psi")).isEmpty();
        assert ModScanners.hasMod(List.of("KubeJS", "create"), "kubejs");

        System.out.println("RoadmapChecks OK");
    }
}

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

        // Named marker must not also add bare id from scanning the marker body.
        String named = "推薦<!--packai:items=minecraft:dirt|Dirt Block-->";
        var namedIds = ItemResolver.extractIds(named);
        assert namedIds.size() == 1;
        assert namedIds.get(0).equals("minecraft:dirt|Dirt Block");
        assert ItemResolver.extractIds("see minecraft:stick here").contains("minecraft:stick");

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

        var capped = com.skps9.packai.client.jei.JeiLookup.capListedDetails(
                List.of(
                        "a → short",
                        "very long ritual ingredients → cursed thing",
                        "mid → out",
                        "x → y",
                        "zzzzzzzz → altar spam"),
                3,
                "...and 2 more");
        assert capped.size() == 4;
        assert capped.get(3).startsWith("...and 2");
        assert capped.contains("a → short");
        assert capped.contains("x → y");
        assert capped.contains("mid → out");
        assert !String.join("\n", capped).contains("very long ritual");

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

        assert AskPurposeContext.isPurposeGraphFact("item:x -[right_click_use]-> held:y");
        assert AskPurposeContext.isPurposeGraphFact("item:x -[desc]-> portal");
        assert AskPurposeContext.isPurposeGraphFact(
                "item:kubejs:foo -[script_use]-> via:finish_using + gets:random + call:getLoot");
        assert !AskPurposeContext.isPurposeGraphFact("item:x -[recipe_needs]-> item:y");
        assert !AskPurposeContext.isPurposeGraphFact("item:x -[loot]-> chest");
        String purpose = AskPurposeContext.buildPurposeBlock(
                "Cursed Ingot\nRitual", List.of("Right-click altar"));
        assert purpose.startsWith(AskPurposeContext.PURPOSE_HEADER + "\n");
        assert purpose.contains("Ritual");
        assert AskPurposeContext.buildPurposeBlock("", List.of()).isEmpty();
        String withGuide = AskPurposeContext.buildPurposeBlock(
                "Tip", List.of(), "Book says: dark ritual fuel");
        assert withGuide.contains(AskPurposeContext.PURPOSE_HEADER);
        assert withGuide.contains(AskPurposeContext.GUIDE_HEADER);
        assert withGuide.contains("dark ritual");
        assert AskPurposeContext.buildPurposeBlock("", List.of(), "only guide")
                .startsWith(AskPurposeContext.GUIDE_HEADER + "\n");
        assert AskPurposeContext.formatFuelLine(0).isEmpty();
        assert AskPurposeContext.formatFuelLine(1600).equals("Furnace fuel: 1600 ticks (~80s)");
        assert AskPurposeContext.formatToolActionsLine(List.of()).isEmpty();
        assert AskPurposeContext.formatToolActionsLine(List.of("shovel_dig", "axe_dig"))
                .equals("Tool actions: axe_dig, shovel_dig");
        assert AskPurposeContext.formatFoodUseLine("drink", 0, 0f, false, false, List.of())
                .equals("Drinkable (hold right-click to drink)");
        assert AskPurposeContext.formatFoodUseLine(
                        "drink", 0, 0.1f, true, false, List.of("mod:soul@0 200t (100%)"))
                .contains("Drinkable food:");
        String merged = AskPurposeContext.withItemBehavior(
                "Coal", List.of(AskPurposeContext.formatFuelLine(1600)));
        assert merged.contains("Coal");
        assert merged.contains("Furnace fuel: 1600");
        assert AskPurposeContext.buildPurposeBlock(merged, List.of()).contains("Furnace fuel");
        // itemBehaviorLines(ItemStack) needs game CP on testCompile — covered by format* + python checks
        assert PatchouliEntryScan.idMentions("evilcraft:dark_gem{x:1}", "evilcraft:dark_gem");
        assert PatchouliEntryScan.normalizeItemKey("minecraft:dirt#0").equals("minecraft:dirt");
        assert PatchouliEntryScan.stripMacros("A$(br)B").equals("A\nB");
        assert PatchouliEntryScan.joinCapped(List.of("aaaa", "bbbb", "cccc"), 2, 3000)
                .equals("aaaa\n\nbbbb");
        assert GuideMePageScan.referencesItem("""
                ---
                item_ids:
                  - ae2:controller
                  - minecraft:stick
                navigation:
                  title: Channels
                ---
                # Channels
                Used for ME networks. <ItemLink id="ae2:controller" />
                """, "ae2:controller");
        assert !GuideMePageScan.referencesItem("""
                ---
                item_ids:
                  - minecraft:stick
                ---
                body
                """, "ae2:controller");
        String gmPlain = GuideMePageScan.extractPlainText("""
                ---
                item_ids:
                  - minecraft:stick
                navigation:
                  title: Stick tip
                ---
                # Hello
                Use the **stick** with <Recipe id="x" />.
                """);
        assert gmPlain.contains("Stick tip");
        assert gmPlain.contains("Hello");
        assert gmPlain.contains("stick");
        assert !gmPlain.contains("<Recipe");
        assert !gmPlain.contains("**");
        assert GuideMePageScan.joinCapped(List.of("aaaa", "bbbb", "cccc"), 2, 3000)
                .equals("aaaa\n\nbbbb");

        assert JarLightIndex.isRecipeEntry("data/minecraft/recipes/stick.json");
        assert !JarLightIndex.isRecipeEntry("data/minecraft/advancements/recipes/foo.json");
        assert JarLightIndex.isLootEntry("data/minecraft/loot_tables/chests/simple_dungeon.json");
        assert JarLightIndex.lootKeyFromPath("data/minecraft/loot_tables/chests/simple_dungeon.json")
                .equals("chests/simple_dungeon");
        java.util.Map<String, java.util.List<String>> jarFacts = new java.util.LinkedHashMap<>();
        JarLightIndex.parseRecipeJson("""
                {"type":"minecraft:crafting_shaped","result":"minecraft:stick","key":{"X":{"item":"minecraft:bamboo"}}}
                """, jarFacts);
        assert jarFacts.getOrDefault("minecraft:stick", List.of()).stream()
                .anyMatch(f -> f.startsWith("R|crafting_shaped|"));
        assert jarFacts.getOrDefault("minecraft:bamboo", List.of()).stream()
                .anyMatch(f -> f.startsWith("U|crafting_shaped|minecraft:stick"));
        JarLightIndex.parseLootJson(
                "data/minecraft/loot_tables/chests/simple_dungeon.json",
                "{\"pools\":[{\"entries\":[{\"name\":\"minecraft:iron_ingot\"}]}]}",
                jarFacts);
        assert jarFacts.getOrDefault("minecraft:iron_ingot", List.of()).contains("L|chests/simple_dungeon");
        assert ReplyLang.jarHeader("en_us").equals("[JAR]");
        assert ReplySources.build(false, false, false, false, false, true, "en_us")
                .contains("mod jar index");

        System.out.println("RoadmapChecks OK");
    }
}

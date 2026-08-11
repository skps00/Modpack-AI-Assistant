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

        // Issue #4: quest submit/obtain ≠ trade; inherit + ambiguous
        Path data = root.resolve("config/ftbquests/quests/data.snbt");
        Files.createDirectories(data.getParent());
        Files.writeString(data, "{\n\tdefault_consume_items: false\n}\n");

        Path chapDir = root.resolve("config/ftbquests/quests/chapters");
        Files.createDirectories(chapDir);
        Files.writeString(chapDir.resolve("acquire.snbt"), """
                {
                	id: "CHAPTERACQUIRE001"
                	quests: [
                		{
                			id: "QUESTSUBMIT000001"
                			title: "Submit Stick"
                			tasks: [{
                				id: "TASKSUBMIT0000001"
                				consume_items: true
                				item: { id: "minecraft:stick" }
                				type: "item"
                			}]
                		}
                		{
                			id: "QUESTOBTAIN000001"
                			title: "Hold Dirt"
                			tasks: [{
                				id: "TASKOBTAIN0000001"
                				item: { id: "minecraft:dirt" }
                				type: "item"
                			}]
                		}
                	]
                }
                """);

        Path ambDir = root.resolve("config/ftbquests/quests/chapters");
        Files.writeString(ambDir.resolve("ambiguous.snbt"), """
                {
                	id: "CHAPTERAMBIGUOUS01"
                	quests: [{
                		id: "QUESTAMBIGUOUS0001"
                		title: "Ambiguous Cobble"
                		tasks: [{
                			id: "TASKAMBIGUOUS00001"
                			item: { id: "minecraft:cobblestone" }
                			type: "item"
                		}]
                	}]
                }
                """);

        // rebuild so quest paths + file default are indexed
        idx.build(root, List.of("kubejs", "datapacks", "ftbquests"));

        assert PackIndex.isQuestPath("config/ftbquests/quests/chapters/acquire.snbt");
        assert Boolean.TRUE.equals(PackIndex.resolveConsume(true, false, false));
        assert Boolean.FALSE.equals(PackIndex.resolveConsume(null, null, false));
        assert PackIndex.resolveConsume(null, null, null) == null;

        List<String> stick = idx.acquireFactsFor("minecraft:stick");
        assert stick.stream().anyMatch(s -> s.contains("繳交") && s.contains("Submit Stick"))
                : "expected titled submit: " + stick;
        assert stick.stream().noneMatch(s -> s.contains("交易")) : "submit must not be trade: " + stick;
        String stickStatus = AskJeiHints.ensureQuestStatusVisible(
                "craft\n【來源】JEI", stick, "zh_tw");
        assert stickStatus.contains("Submit Stick") && stickStatus.contains("【任務】")
                : "titled status inject: " + stickStatus;

        List<String> dirt = idx.acquireFactsFor("minecraft:dirt");
        assert dirt.stream().anyMatch(s -> s.contains("取得") && s.contains("Hold Dirt"))
                : "expected titled obtain via file default: " + dirt;
        assert dirt.stream().noneMatch(s -> s.contains("交易")) : "obtain must not be trade: " + dirt;

        // Chapter hide_quest_details_until_startable → no quest facts / no 【任務】 inject
        Path rootHide = Files.createTempDirectory("packai-acquire-hide");
        Path dataHide = rootHide.resolve("config/ftbquests/quests/data.snbt");
        Files.createDirectories(dataHide.getParent());
        Files.writeString(dataHide, "{\n\tdefault_consume_items: false\n}\n");
        Path chapHide = rootHide.resolve("config/ftbquests/quests/chapters");
        Files.createDirectories(chapHide);
        Files.writeString(chapHide.resolve("kr_hide.snbt"), """
                {
                	hide_quest_details_until_startable: true
                	id: "CHAPTERHIDEDETAILS01"
                	quests: [{
                		id: "QUESTAZUREBLUET001"
                		title: "深埋的信"
                		tasks: [{
                			id: "TASKAZUREBLUET0001"
                			consume_items: true
                			item: "minecraft:azure_bluet"
                			type: "item"
                		}]
                	}]
                }
                """);
        PackIndex idxHide = new PackIndex();
        idxHide.build(rootHide, List.of("ftbquests"));
        List<String> bluet = idxHide.acquireFactsFor("minecraft:azure_bluet");
        assert bluet.stream().noneMatch(s -> s.contains("繳交") || s.contains("取得")
                || s.contains("quest_submit") || s.contains("quest_obtain"))
                : "chapter hide-details must suppress quest edges: " + bluet;
        String bluetBody = AskJeiHints.ensureQuestStatusVisible(
                "1. something\n【來源】JEI", bluet, "zh_tw");
        assert !bluetBody.contains("【任務】") : "no bare/titled quest status: " + bluetBody;

        // Quest-level hide_details_until_startable alone also suppresses
        Path rootQHide = Files.createTempDirectory("packai-acquire-qhide");
        Path dataQ = rootQHide.resolve("config/ftbquests/quests/data.snbt");
        Files.createDirectories(dataQ.getParent());
        Files.writeString(dataQ, "{\n\tdefault_consume_items: true\n}\n");
        Path chapQ = rootQHide.resolve("config/ftbquests/quests/chapters");
        Files.createDirectories(chapQ);
        Files.writeString(chapQ.resolve("quest_hide.snbt"), """
                {
                	id: "CHAPTERQUESTHIDE001"
                	quests: [{
                		hide_details_until_startable: true
                		id: "QUESTDETAILSHIDE01"
                		title: "Hidden Details Quest"
                		tasks: [{
                			id: "TASKDETAILSHIDE001"
                			item: "minecraft:poppy"
                			type: "item"
                		}]
                	}]
                }
                """);
        PackIndex idxQHide = new PackIndex();
        idxQHide.build(rootQHide, List.of("ftbquests"));
        List<String> poppy = idxQHide.acquireFactsFor("minecraft:poppy");
        assert poppy.stream().noneMatch(s -> s.contains("繳交") || s.contains("取得"))
                : "quest hide_details must suppress: " + poppy;

        String chapHead = "{\n\thide_quest_details_until_startable: true\n\tquests: [\n";
        assert Boolean.TRUE.equals(QuestGuide.chapterHideDetailsUntilStartable(
                chapHead, chapHead.indexOf("quests")))
                : "chapter hide-details field parse";
        assert QuestGuide.shouldSuppressQuestAdvertise(
                "{ id: \"X\" title: \"T\" }", Boolean.TRUE, false, null)
                : "chapter inherit hide-details";
        assert !QuestGuide.shouldSuppressQuestAdvertise(
                "{ hide_details_until_startable: false id: \"X\" title: \"T\" }",
                Boolean.TRUE, false, null)
                : "quest false overrides chapter hide-details";

        // Stale session graph: poison edge must not survive beginAskSession + acquire
        Path rootStale = Files.createTempDirectory("packai-acquire-stale");
        Path dataStale = rootStale.resolve("config/ftbquests/quests/data.snbt");
        Files.createDirectories(dataStale.getParent());
        Files.writeString(dataStale, "{\n\tdefault_consume_items: true\n}\n");
        Path chapStale = rootStale.resolve("config/ftbquests/quests/chapters");
        Files.createDirectories(chapStale);
        Files.writeString(chapStale.resolve("kr_stale.snbt"), """
                {
                	hide_quest_details_until_startable: true
                	id: "CHAPTERSTALEHIDE0001"
                	quests: [{
                		hide: true
                		hide_details_until_startable: true
                		id: "738DADDB375F97F5"
                		title: "深埋的信"
                		tasks: [{
                			consume_items: true
                			id: "TASKSTALEAZURE0001"
                			item: "minecraft:azure_bluet"
                			type: "item"
                		}]
                	}]
                }
                """);
        PackIndex idxStale = new PackIndex();
        idxStale.build(rootStale, List.of("ftbquests"));
        // Simulate prior Ask that somehow kept a spoiler edge in the session graph.
        idxStale.beginAskSession();
        List<String> staleBluet = idxStale.acquireFactsFor("minecraft:azure_bluet");
        assert staleBluet.stream().noneMatch(s -> s.contains("深埋") || s.contains("繳交") || s.contains("取得"))
                : "stale/session acquire must not name spoiler quest: " + staleBluet;
        String staleBody = AskJeiHints.ensureQuestStatusVisible(
                "1. flower\n【來源】JEI", staleBluet, "zh_tw");
        assert !staleBody.contains("深埋") && !staleBody.contains("【任務】")
                : "no QUEST_STATUS spoiler inject: " + staleBody;

        // No data.snbt default → ambiguous cobble must not get submit/obtain edges
        Path root2 = Files.createTempDirectory("packai-acquire-amb");
        Path chap2 = root2.resolve("config/ftbquests/quests/chapters");
        Files.createDirectories(chap2);
        Files.writeString(chap2.resolve("ambiguous.snbt"), """
                {
                	id: "CHAPTERAMBIGUOUS02"
                	quests: [{
                		id: "QUESTAMBIGUOUS0002"
                		title: "Ambiguous Cobble"
                		tasks: [{
                			id: "TASKAMBIGUOUS00002"
                			item: { id: "minecraft:cobblestone" }
                			type: "item"
                		}]
                	}]
                }
                """);
        PackIndex idx2 = new PackIndex();
        idx2.build(root2, List.of("ftbquests"));
        List<String> cobble = idx2.acquireFactsFor("minecraft:cobblestone");
        assert cobble.stream().noneMatch(s -> s.contains("繳交") || s.contains("取得"))
                : "ambiguous must not label: " + cobble;

        // NFWC-style: file default false + hold task; MAX_GRAPH full must still pin obtain (not invent submit)
        Path rootCap = Files.createTempDirectory("packai-acquire-cap");
        Path dataCap = rootCap.resolve("config/ftbquests/quests/data.snbt");
        Files.createDirectories(dataCap.getParent());
        Files.writeString(dataCap, "{\n\tdefault_consume_items: false\n}\n");
        Path chapCap = rootCap.resolve("config/ftbquests/quests/chapters");
        Files.createDirectories(chapCap);
        StringBuilder fill = new StringBuilder();
        fill.append("{\n\tid: \"CHAPTERFILLCAP0001\"\n\tquests: [\n");
        for (int i = 0; i < 220; i++) {
            if (i > 0) {
                fill.append(",\n");
            }
            fill.append("\t\t{ id: \"QF").append(String.format("%014d", i))
                    .append("\" title: \"Fill ").append(i)
                    .append("\" tasks: [{ id: \"TF").append(String.format("%014d", i))
                    .append("\" item: { id: \"packai_test:fill_").append(i)
                    .append("\" } type: \"item\" }] }");
        }
        fill.append("\n\t]\n}\n");
        Files.writeString(chapCap.resolve("aaa_fill.snbt"), fill.toString());
        Files.writeString(chapCap.resolve("zzz_mystery.snbt"), """
                {
                	id: "CHAPTERMYSTERY0001"
                	quests: [{
                		id: "QUESTMYSTERYDISAST"
                		title: "奥秘·回忆"
                		subtitle: "携灾者"
                		rewards: [{
                			id: "REWARDCOINNETHER"
                			item: "lightmanscurrency:coin_netherite"
                			type: "item"
                		}]
                		tasks: [{
                			id: "TASKMYSTERYDISAST"
                			item: "mrqx_extra_pack:mystery_disasters"
                			type: "item"
                		}]
                	}]
                }
                """);
        PackIndex idxCap = new PackIndex();
        idxCap.build(rootCap, List.of("ftbquests"));
        // Saturate MAX_GRAPH with filler chapter (same as retrieve() pre-fill)
        idxCap.ingestGraph(
                "config/ftbquests/quests/chapters/aaa_fill.snbt",
                Files.readString(chapCap.resolve("aaa_fill.snbt")));
        List<String> disasters = idxCap.acquireFactsFor("mrqx_extra_pack:mystery_disasters");
        assert disasters.stream().anyMatch(s -> s.contains("取得"))
                : "cap+file-default must obtain: " + disasters;
        assert disasters.stream().noneMatch(s -> s.contains("繳交"))
                : "must not submit when hold-only: " + disasters;
        assert AskJeiHints.isObtainOnlyQuestAcquire(disasters)
                : "hold-only acquire should be obtain-only: " + disasters;
        String scrubbedQuest = AskJeiHints.scrubObtainOnlyQuestWording(
                "在任務書兌換下界合金幣完成奧秘·回憶", true);
        assert scrubbedQuest.contains("背包持有即可完成") : scrubbedQuest;
        assert !scrubbedQuest.contains("任務書兌換") : scrubbedQuest;
        assert scrubbedQuest.contains("【任務】") : scrubbedQuest;
        String scrubbedConvert = AskJeiHints.scrubObtainOnlyQuestWording(
                "JEI 顯示可在任務書中轉換為下界合金幣", true);
        assert !scrubbedConvert.contains("轉換")
                && scrubbedConvert.contains("背包持有即可完成")
                && scrubbedConvert.contains("【任務】")
                : scrubbedConvert;

        String kubeLabel = PackIndex.humanAcquireLabel("kubejs/server_scripts/recipes.js");
        assert !kubeLabel.contains("交易") : "kubejs must not be trade: " + kubeLabel;

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

        // P0: Tetra scroll_rolled — strict Ask acquire; soft matchResult unchanged
        Path scrollRoot = Files.createTempDirectory("packai-scroll-acquire");
        Path scrollData = scrollRoot.resolve("config/ftbquests/quests/data.snbt");
        Files.createDirectories(scrollData.getParent());
        Files.writeString(scrollData, "{\n\tdefault_consume_items: false\n}\n");
        Path scrollChap = scrollRoot.resolve("config/ftbquests/quests/chapters");
        Files.createDirectories(scrollChap);
        Files.writeString(scrollChap.resolve("tetra_scrolls.snbt"), """
                {
                	id: "CHAPTERSCROLL00001"
                	quests: [
                		{
                			id: "QUESTHEAVYMACE0001"
                			title: "真正的重錘"
                			tasks: [{
                				id: "TASKHEAVYMACE00001"
                				item: {
                					id: "tetra:scroll_rolled"
                					tag: { s: ["tetra:heavy_mace"] }
                				}
                				type: "item"
                			}]
                		}
                		{
                			id: "QUESTGILD000000001"
                			title: "鍍金卷"
                			tasks: [{
                				id: "TASKGILD0000000001"
                				item: {
                					id: "tetra:scroll_rolled"
                					tag: { s: ["tetra:hone/gild_5"] }
                				}
                				type: "item"
                			}]
                		}
                		{
                			id: "QUESTENERGY0000001"
                			title: "能量瓶卷"
                			tasks: [{
                				id: "TASKENERGY00000001"
                				item: {
                					id: "tetra:scroll_rolled"
                					tag: { s: ["tetra:energy_bottle"] }
                				}
                				type: "item"
                			}]
                		}
                	]
                }
                """);
        PackIndex scrollIdx = new PackIndex();
        scrollIdx.build(scrollRoot, List.of("ftbquests"));
        List<String> gildToks = ItemVariantKeysText.expandTokens(List.of("tetra:hone/gild_5"));
        List<String> gildAcq = scrollIdx.acquireFactsFor("tetra:scroll_rolled", "zh_tw", gildToks);
        assert gildAcq.stream().noneMatch(s -> s.contains("真正的重錘"))
                : "gild must not attach mace quest: " + gildAcq;
        assert gildAcq.stream().noneMatch(s -> s.contains("能量瓶"))
                : "gild must not attach energy: " + gildAcq;
        assert gildAcq.stream().anyMatch(s -> s.contains("鍍金卷"))
                : "gild should keep gild quest: " + gildAcq;

        List<String> maceToks = ItemVariantKeysText.expandTokens(List.of("tetra:heavy_mace"));
        List<String> maceAcq = scrollIdx.acquireFactsFor("tetra:scroll_rolled", "zh_tw", maceToks);
        assert maceAcq.stream().anyMatch(s -> s.contains("真正的重錘")) : "mace keep: " + maceAcq;
        assert maceAcq.stream().noneMatch(s -> s.contains("鍍金卷")) : "mace not gild: " + maceAcq;

        List<String> mirrorToks = ItemVariantKeysText.expandTokens(List.of("tetra:mirror"));
        List<String> mirrorAcq = scrollIdx.acquireFactsFor("tetra:scroll_rolled", "zh_tw", mirrorToks);
        assert mirrorAcq.stream().noneMatch(s -> s.contains("真正的重錘")) : mirrorAcq;
        assert mirrorAcq.stream().noneMatch(s -> s.contains("鍍金卷") || s.contains("能量瓶"))
                : mirrorAcq;
        assert mirrorAcq.stream().anyMatch(s ->
                s.contains("schematic") || s.contains("變體") || s.contains("兄弟") || s.contains("sibling"))
                : "mirror unmatched should caution: " + mirrorAcq;

        List<String> bare = scrollIdx.acquireFactsFor("tetra:scroll_rolled", "zh_tw", List.of());
        assert bare.stream().anyMatch(s -> s.contains("真正的重錘")) : "bare id still lists: " + bare;

        // Soft matchResult / preferVariantHits: none mention mirror → keep id siblings
        List<QuestGuide.Hit> scrollHits = QuestGuide.index(scrollRoot, List.of("ftbquests"), null, false);
        var soft = QuestGuide.matchResult(
                scrollHits, "scroll", "tetra:scroll_rolled", List.of(), mirrorToks);
        assert soft.hits().stream().anyMatch(h -> h.title() != null && h.title().contains("真正的重錘"))
                : "soft matchResult must keep id siblings when none mention variant: " + soft.hits();
        assert QuestGuide.mentionsFocusItem(
                soft.hits().stream().filter(h -> h.title() != null && h.title().contains("真正的重錘"))
                        .findFirst().orElseThrow(),
                "tetra:scroll_rolled",
                mirrorToks) == false
                : "PURPOSE strict 3-arg must reject mace hit for mirror tokens";

        System.out.println("AcquireFactsCheck OK");
    }
}

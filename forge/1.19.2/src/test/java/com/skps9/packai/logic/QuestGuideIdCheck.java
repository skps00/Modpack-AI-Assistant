package com.skps9.packai.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Runnable check: open_book ids must be quest ids, not task/reward/previous quest.
 */
public final class QuestGuideIdCheck {
    private QuestGuideIdCheck() {}

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("packai-quest-id");
        Path chapDir = root.resolve("config/ftbquests/quests/chapters");
        Files.createDirectories(chapDir);
        Files.writeString(chapDir.resolve("demo.snbt"), """
                {
                	id: "CHAPTERID000001"
                	quests: [
                		{
                			id: "1111111111111111"
                			title: "First Quest"
                			rewards: [{
                				id: "REWARDAAAAAAAA01"
                				item: { id: "minecraft:stick" }
                				type: "item"
                			}]
                			tasks: [{
                				id: "TASKAAAAAAAAAA01"
                				item: { id: "minecraft:stick" }
                				type: "item"
                			}]
                		}
                		{
                			dependencies: ["1111111111111111"]
                			id: "2222222222222222"
                			title: "Second Quest"
                			tasks: [{
                				id: "TASKBBBBBBBBBB02"
                				item: { id: "minecraft:dirt" }
                				type: "item"
                			}]
                		}
                	]
                }
                """);

        Path langDir = root.resolve("config/ftbquests/quests/lang/en_us/chapters");
        Files.createDirectories(langDir);
        Files.writeString(langDir.resolve("demo.snbt"), """
                {
                	quest.1111111111111111.title: "&6First From Lang"
                	quest.3333333333333333.title: "Lang Only Quest"
                }
                """);

        // Explicit filterHidden=false so unit check is independent of PackAiConfig defaults.
        List<QuestGuide.Hit> hits = QuestGuide.index(root, List.of("ftbquests"), null, false);

        QuestGuide.Hit first = hits.stream()
                .filter(h -> "1111111111111111".equalsIgnoreCase(h.questId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing quest 1111…"));
        assert first.title().contains("First From Lang") : "lang title should win over chapter key/raw: " + first.title();
        assert first.items().stream().anyMatch(i -> i.contains("stick")) : "items from chapter must merge";

        assert hits.stream().anyMatch(h -> "2222222222222222".equalsIgnoreCase(h.questId()))
                : "second quest id missing";
        assert hits.stream().anyMatch(h -> "3333333333333333".equalsIgnoreCase(h.questId()))
                : "lang-only quest missing";

        assert hits.stream().noneMatch(h -> "TASKAAAAAAAAAA01".equalsIgnoreCase(h.questId()))
                : "must not open_book to task id";
        assert hits.stream().noneMatch(h -> "REWARDAAAAAAAA01".equalsIgnoreCase(h.questId()))
                : "must not open_book to reward id";
        assert hits.stream().noneMatch(h -> "CHAPTERID000001".equalsIgnoreCase(h.questId()))
                : "must not open_book to chapter id";

        // Anti-spoiler: hide / invisible / chapter deps gate
        Path spoilDir = root.resolve("config/ftbquests/quests/chapters");
        Files.writeString(spoilDir.resolve("secret.snbt"), """
                {
                	hide_quest_until_deps_visible: true
                	id: "CHAPTERSECRET0001"
                	quests: [
                		{
                			id: "AAAAAAAAAAAAAAA1"
                			title: "Visible Root"
                			tasks: [{ id: "TASKROOT00000001" type: "checkmark" }]
                		}
                		{
                			dependencies: ["AAAAAAAAAAAAAAA1"]
                			hide: true
                			id: "BBBBBBBBBBBBBBBB"
                			title: "Hidden Flag"
                			tasks: [{ id: "TASKHIDE00000001" type: "checkmark" }]
                		}
                		{
                			dependencies: ["AAAAAAAAAAAAAAA1"]
                			id: "CCCCCCCCCCCCCCCC"
                			invisible: true
                			title: "Invisible Flag"
                			tasks: [{ id: "TASKINV000000001" type: "checkmark" }]
                		}
                		{
                			dependencies: ["AAAAAAAAAAAAAAA1"]
                			id: "DDDDDDDDDDDDDDDD"
                			title: "Deps Gated"
                			tasks: [{ id: "TASKDEP000000001" type: "checkmark" }]
                		}
                	]
                }
                """);
        List<QuestGuide.Hit> filtered = QuestGuide.index(root, List.of("ftbquests"), null, true);
        assert filtered.stream().anyMatch(h -> "AAAAAAAAAAAAAAA1".equalsIgnoreCase(h.questId()))
                : "root quest must remain";
        assert filtered.stream().noneMatch(h -> "BBBBBBBBBBBBBBBB".equalsIgnoreCase(h.questId()))
                : "hide:true must be filtered";
        assert filtered.stream().noneMatch(h -> "CCCCCCCCCCCCCCCC".equalsIgnoreCase(h.questId()))
                : "invisible:true must be filtered";
        assert filtered.stream().noneMatch(h -> "DDDDDDDDDDDDDDDD".equalsIgnoreCase(h.questId()))
                : "chapter hide_quest_until_deps_visible + deps must be filtered";
        assert QuestGuide.isSpoilerHiddenQuestObject("{ hide: true id: \"X\" }") : "hide detector";
        assert !QuestGuide.isSpoilerHiddenQuestObject("{ hide_dependency_lines: true id: \"X\" }")
                : "must not treat hide_dependency_lines as spoiler";

        System.out.println("QuestGuideIdCheck OK (" + hits.size() + " quests, filter ok)");
    }
}

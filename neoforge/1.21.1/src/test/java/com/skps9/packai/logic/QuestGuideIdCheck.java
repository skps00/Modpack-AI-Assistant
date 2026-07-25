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

        List<QuestGuide.Hit> hits = QuestGuide.index(root, List.of("ftbquests"));

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

        System.out.println("QuestGuideIdCheck OK (" + hits.size() + " quests)");
    }
}

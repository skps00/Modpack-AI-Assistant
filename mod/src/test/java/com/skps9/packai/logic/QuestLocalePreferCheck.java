package com.skps9.packai.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Runnable check: Spanish FTB lang must not win over English when preferred is zh/en.
 */
public final class QuestLocalePreferCheck {
    private QuestLocalePreferCheck() {}

    public static void main(String[] args) throws Exception {
        assert QuestGuide.langCodeFromPath("config/ftbquests/quests/lang/es_es/chapters/x.snbt")
                .equals("es_es");
        assert !QuestGuide.keepLangFile(
                "config/ftbquests/quests/lang/es_es/chapters/x.snbt", "zh_tw");
        assert QuestGuide.keepLangFile(
                "config/ftbquests/quests/lang/en_us/chapters/x.snbt", "zh_tw");
        assert QuestGuide.keepLangFile(
                "config/ftbquests/quests/lang/es_mx/chapters/x.snbt", "es_es");

        Path root = Files.createTempDirectory("packai-quest-locale");
        Path chap = root.resolve("config/ftbquests/quests/chapters");
        Files.createDirectories(chap);
        Files.writeString(chap.resolve("demo.snbt"), """
                {
                	quests: [
                		{
                			id: "AAAAAAAAAAAA0001"
                			title: "{quest.AAAAAAAAAAAA0001.title}"
                			tasks: [{ id: "TASK0001", item: { id: "minecraft:dirt" }, type: "item" }]
                		}
                	]
                }
                """);

        Path en = root.resolve("config/ftbquests/quests/lang/en_us/chapters");
        Path es = root.resolve("config/ftbquests/quests/lang/es_es/chapters");
        Files.createDirectories(en);
        Files.createDirectories(es);
        Files.writeString(en.resolve("demo.snbt"), """
                {
                	quest.AAAAAAAAAAAA0001.title: "Dirt"
                }
                """);
        Files.writeString(es.resolve("demo.snbt"), """
                {
                	quest.AAAAAAAAAAAA0001.title: "Tierra y mucho más texto largo"
                }
                """);

        List<QuestGuide.Hit> zhHits = QuestGuide.index(root, List.of("ftbquests"), "zh_tw");
        QuestGuide.Hit h = zhHits.stream()
                .filter(q -> "AAAAAAAAAAAA0001".equalsIgnoreCase(q.questId()))
                .findFirst()
                .orElseThrow();
        assert "Dirt".equals(h.title()) : "expected English fallback, got: " + h.title();

        QuestGuide.Hit merged = QuestGuide.mergeHits(
                new QuestGuide.Hit("", "Tierra y mucho más texto largo", "",
                        "lang/es_es/chapters/demo.snbt", List.of(), 0, false, "AAAAAAAAAAAA0001", "ftbquests"),
                new QuestGuide.Hit("", "Dirt", "",
                        "lang/en_us/chapters/demo.snbt", List.of(), 0, false, "AAAAAAAAAAAA0001", "ftbquests"),
                "zh_tw");
        assert "Dirt".equals(merged.title()) : "merge must prefer en over es: " + merged.title();

        System.out.println("QuestLocalePreferCheck OK");
    }
}

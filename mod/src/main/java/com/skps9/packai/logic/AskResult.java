package com.skps9.packai.logic;

import java.util.List;

import com.skps9.packai.logic.ItemResolver;

/** Answer text plus optional quests and suggested item ids for the UI. */
public record AskResult(String answer, List<QuestGuide.Hit> quests, List<String> suggestedItemIds) {
    public static AskResult text(String answer) {
        return fromRaw(answer, List.of());
    }

    public static AskResult of(String answer, List<QuestGuide.Hit> quests) {
        return fromRaw(answer, quests);
    }

    public static AskResult of(String answer, List<QuestGuide.Hit> quests, List<String> suggestedItemIds) {
        String raw = answer == null ? "" : answer;
        String clean = ItemResolver.stripMarker(raw);
        List<String> ids = suggestedItemIds != null && !suggestedItemIds.isEmpty()
                ? List.copyOf(suggestedItemIds)
                : ItemResolver.extractIds(raw);
        return new AskResult(
                Plainify.forMinecraftUi(clean),
                quests == null || quests.isEmpty() ? List.of() : List.copyOf(quests),
                ids);
    }

    private static AskResult fromRaw(String answer, List<QuestGuide.Hit> quests) {
        List<String> ids = ItemResolver.extractIds(answer);
        String clean = ItemResolver.stripMarker(answer == null ? "" : answer);
        return new AskResult(
                Plainify.forMinecraftUi(clean),
                quests == null || quests.isEmpty() ? List.of() : List.copyOf(quests),
                ids);
    }
}

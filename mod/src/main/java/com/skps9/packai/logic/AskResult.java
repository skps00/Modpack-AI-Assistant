package com.skps9.packai.logic;

import java.util.List;

/** Answer text plus optional quests the UI can open. */
public record AskResult(String answer, List<QuestGuide.Hit> quests) {
    public static AskResult text(String answer) {
        return new AskResult(answer == null ? "" : answer, List.of());
    }

    public static AskResult of(String answer, List<QuestGuide.Hit> quests) {
        return new AskResult(
                answer == null ? "" : answer,
                quests == null || quests.isEmpty() ? List.of() : List.copyOf(quests)
        );
    }
}

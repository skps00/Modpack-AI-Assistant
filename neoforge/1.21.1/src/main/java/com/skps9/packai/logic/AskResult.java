package com.skps9.packai.logic;

import java.util.List;

/** Answer text plus optional quests, suggested items, and JEI recipe cards for the UI. */
public record AskResult(
        String answer,
        List<QuestGuide.Hit> quests,
        List<String> suggestedItemIds,
        List<RecipeCard> recipeCards
) {
    public static AskResult text(String answer) {
        return fromRaw(answer, List.of(), List.of());
    }

    public static AskResult of(String answer, List<QuestGuide.Hit> quests) {
        return fromRaw(answer, quests, List.of());
    }

    public static AskResult of(String answer, List<QuestGuide.Hit> quests, List<String> suggestedItemIds) {
        return of(answer, quests, suggestedItemIds, List.of());
    }

    public static AskResult of(
            String answer,
            List<QuestGuide.Hit> quests,
            List<String> suggestedItemIds,
            List<RecipeCard> recipeCards
    ) {
        String raw = answer == null ? "" : answer;
        String clean = ItemResolver.stripMarker(raw);
        List<String> ids = suggestedItemIds != null && !suggestedItemIds.isEmpty()
                ? List.copyOf(suggestedItemIds)
                : ItemResolver.extractIds(raw);
        return new AskResult(
                Plainify.forMinecraftUi(clean),
                quests == null || quests.isEmpty() ? List.of() : List.copyOf(quests),
                ids,
                recipeCards == null || recipeCards.isEmpty() ? List.of() : List.copyOf(recipeCards));
    }

    public AskResult withRecipeCards(List<RecipeCard> cards) {
        return new AskResult(
                answer,
                quests,
                suggestedItemIds,
                cards == null || cards.isEmpty() ? List.of() : List.copyOf(cards));
    }

    private static AskResult fromRaw(String answer, List<QuestGuide.Hit> quests, List<RecipeCard> cards) {
        List<String> ids = ItemResolver.extractIds(answer);
        String clean = ItemResolver.stripMarker(answer == null ? "" : answer);
        return new AskResult(
                Plainify.forMinecraftUi(clean),
                quests == null || quests.isEmpty() ? List.of() : List.copyOf(quests),
                ids,
                cards == null || cards.isEmpty() ? List.of() : List.copyOf(cards));
    }
}

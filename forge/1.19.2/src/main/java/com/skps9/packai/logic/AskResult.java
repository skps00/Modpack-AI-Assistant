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
        List<String> ids = suggestedItemIds != null && !suggestedItemIds.isEmpty()
                ? List.copyOf(suggestedItemIds)
                : ItemResolver.extractIds(raw);
        return new AskResult(
                finalizeAnswer(raw),
                quests == null || quests.isEmpty() ? List.of() : List.copyOf(quests),
                ids,
                recipeCards == null || recipeCards.isEmpty() ? List.of() : List.copyOf(recipeCards));
    }

    public AskResult withRecipeCards(List<RecipeCard> cards) {
        List<RecipeCard> copy = cards == null || cards.isEmpty() ? List.of() : List.copyOf(cards);
        boolean hasCards = !copy.isEmpty();
        String scrubbed = AskJeiHints.scrubAbsenceClaimsWhenCards(answer, hasCards);
        return new AskResult(AskReplyScrub.scrubPromptEcho(scrubbed), quests, suggestedItemIds, copy);
    }

    /** Replace answer text (keeps quests / suggestions / cards). Used for post-LLM inject. */
    public AskResult withAnswer(String newAnswer) {
        return new AskResult(finalizeAnswer(newAnswer), quests, suggestedItemIds, recipeCards);
    }

    private static AskResult fromRaw(String answer, List<QuestGuide.Hit> quests, List<RecipeCard> cards) {
        List<String> ids = ItemResolver.extractIds(answer);
        return new AskResult(
                finalizeAnswer(answer),
                quests == null || quests.isEmpty() ? List.of() : List.copyOf(quests),
                ids,
                cards == null || cards.isEmpty() ? List.of() : List.copyOf(cards));
    }

    /** Strip hidden markers + PURPOSE tag echoes, then Minecraft-safe UI text. */
    private static String finalizeAnswer(String answer) {
        String clean = ItemResolver.stripMarker(answer == null ? "" : answer);
        clean = AskReplyScrub.scrubPromptEcho(clean);
        return Plainify.forMinecraftUi(clean);
    }
}

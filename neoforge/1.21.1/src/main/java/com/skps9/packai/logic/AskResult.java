package com.skps9.packai.logic;

import java.util.List;

/** Answer text plus optional quests, suggested items, JEI recipe cards, and LLM token usage. */
public record AskResult(
        String answer,
        List<QuestGuide.Hit> quests,
        List<String> suggestedItemIds,
        List<RecipeCard> recipeCards,
        TokenUsage tokenUsage,
        boolean cardStrip
) {
    public AskResult {
        tokenUsage = tokenUsage == null ? TokenUsage.NONE : tokenUsage;
    }

    /** Backward-compatible 5-arg construction (cardStrip=false). */
    public AskResult(
            String answer,
            List<QuestGuide.Hit> quests,
            List<String> suggestedItemIds,
            List<RecipeCard> recipeCards,
            TokenUsage tokenUsage
    ) {
        this(answer, quests, suggestedItemIds, recipeCards, tokenUsage, false);
    }

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
                recipeCards == null || recipeCards.isEmpty() ? List.of() : List.copyOf(recipeCards),
                TokenUsage.NONE,
                false);
    }

    public AskResult withRecipeCards(List<RecipeCard> cards) {
        return withRecipeCards(cards, this.cardStrip);
    }

    /** Attach display cards; {@code strip=true} = AI tool-emission strip (no inline markers). */
    public AskResult withRecipeCards(List<RecipeCard> cards, boolean strip) {
        List<RecipeCard> copy = cards == null || cards.isEmpty() ? List.of() : List.copyOf(cards);
        boolean hasCards = !copy.isEmpty();
        String scrubbed = AskJeiHints.scrubAbsenceClaimsWhenCards(answer, hasCards);
        scrubbed = RecipeCardsMode.scrubMarker(scrubbed);
        return new AskResult(
                AskReplyScrub.scrubPromptEcho(scrubbed), quests, suggestedItemIds, copy, tokenUsage, strip);
    }

    /** Replace answer text (keeps quests / suggestions / cards / usage / strip flag). */
    public AskResult withAnswer(String newAnswer) {
        return new AskResult(
                finalizeAnswer(newAnswer), quests, suggestedItemIds, recipeCards, tokenUsage, cardStrip);
    }

    public AskResult withTokenUsage(TokenUsage usage) {
        return new AskResult(
                answer, quests, suggestedItemIds, recipeCards,
                usage == null ? TokenUsage.NONE : usage, cardStrip);
    }

    private static AskResult fromRaw(String answer, List<QuestGuide.Hit> quests, List<RecipeCard> cards) {
        List<String> ids = ItemResolver.extractIds(answer);
        return new AskResult(
                finalizeAnswer(answer),
                quests == null || quests.isEmpty() ? List.of() : List.copyOf(quests),
                ids,
                cards == null || cards.isEmpty() ? List.of() : List.copyOf(cards),
                TokenUsage.NONE,
                false);
    }

    /** Strip hidden markers + PURPOSE tag echoes, then Minecraft-safe UI text. */
    private static String finalizeAnswer(String answer) {
        String clean = ItemResolver.stripMarker(answer == null ? "" : answer);
        clean = AskReplyScrub.scrubPromptEcho(clean);
        return Plainify.forMinecraftUi(clean);
    }
}

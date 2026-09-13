package com.skps9.packai.logic;

import java.util.List;

/** Answer text plus optional quests, suggested items, JEI recipe cards, and LLM token usage. */
public record AskResult(
        String answer,
        List<QuestGuide.Hit> quests,
        List<String> suggestedItemIds,
        List<RecipeCard> recipeCards,
        TokenUsage tokenUsage,
        boolean cardStrip,
        String displaySrc
) {
    public static final String DISPLAY_SRC_UNKNOWN = "UNKNOWN";

    public AskResult {
        tokenUsage = tokenUsage == null ? TokenUsage.NONE : tokenUsage;
        if (displaySrc == null || displaySrc.isBlank()) {
            displaySrc = DISPLAY_SRC_UNKNOWN;
        }
    }

    /** Backward-compatible 5-arg construction (cardStrip=false, displaySrc=UNKNOWN). */
    public AskResult(
            String answer,
            List<QuestGuide.Hit> quests,
            List<String> suggestedItemIds,
            List<RecipeCard> recipeCards,
            TokenUsage tokenUsage
    ) {
        this(answer, quests, suggestedItemIds, recipeCards, tokenUsage, false, DISPLAY_SRC_UNKNOWN);
    }

    /** Backward-compatible 6-arg construction (displaySrc=UNKNOWN). */
    public AskResult(
            String answer,
            List<QuestGuide.Hit> quests,
            List<String> suggestedItemIds,
            List<RecipeCard> recipeCards,
            TokenUsage tokenUsage,
            boolean cardStrip
    ) {
        this(answer, quests, suggestedItemIds, recipeCards, tokenUsage, cardStrip, DISPLAY_SRC_UNKNOWN);
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
                false,
                DISPLAY_SRC_UNKNOWN);
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
                AskReplyScrub.scrubPromptEcho(scrubbed),
                quests,
                suggestedItemIds,
                copy,
                tokenUsage,
                strip,
                displaySrc);
    }

    /** Replace answer text (keeps quests / suggestions / cards / usage / strip / displaySrc). */
    public AskResult withAnswer(String newAnswer) {
        return new AskResult(
                finalizeAnswer(newAnswer),
                quests,
                suggestedItemIds,
                recipeCards,
                tokenUsage,
                cardStrip,
                displaySrc);
    }

    public AskResult withTokenUsage(TokenUsage usage) {
        return new AskResult(
                answer,
                quests,
                suggestedItemIds,
                recipeCards,
                usage == null ? TokenUsage.NONE : usage,
                cardStrip,
                displaySrc);
    }

    /** Tag how the player-visible body was chosen. Blank/null → UNKNOWN. */
    public AskResult withDisplaySrc(String src) {
        return new AskResult(
                answer, quests, suggestedItemIds, recipeCards, tokenUsage, cardStrip, src);
    }

    /**
     * Loaded mod version for display-body log lines; {@code "dev"} if ModList is unavailable
     * (unit tests / both Forge and NeoForge FQCNs tried).
     */
    public static String displayBuildId() {
        for (String cn : new String[] {
                "net.minecraftforge.fml.ModList",
                "net.neoforged.fml.ModList"}) {
            try {
                Object list = Class.forName(cn).getMethod("get").invoke(null);
                @SuppressWarnings("unchecked")
                java.util.Optional<?> box = (java.util.Optional<?>) list.getClass()
                        .getMethod("getModContainerById", String.class)
                        .invoke(list, "packai");
                if (box != null && box.isPresent()) {
                    Object info = box.get().getClass().getMethod("getModInfo").invoke(box.get());
                    Object ver = info.getClass().getMethod("getVersion").invoke(info);
                    if (ver != null) {
                        String s = ver.toString().trim();
                        if (!s.isEmpty()) {
                            return s;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // try next loader
            }
        }
        return "dev";
    }

    /**
     * Single-line, bounded dump (newlines → {@code \\n}). Same helper as LlmClient raw-reply log.
     */
    public static String oneLineForLog(String body) {
        return LlmClient.rawReplyForLog(body);
    }

    private static AskResult fromRaw(String answer, List<QuestGuide.Hit> quests, List<RecipeCard> cards) {
        List<String> ids = ItemResolver.extractIds(answer);
        return new AskResult(
                finalizeAnswer(answer),
                quests == null || quests.isEmpty() ? List.of() : List.copyOf(quests),
                ids,
                cards == null || cards.isEmpty() ? List.of() : List.copyOf(cards),
                TokenUsage.NONE,
                false,
                DISPLAY_SRC_UNKNOWN);
    }

    /** Strip hidden markers + PURPOSE tag echoes, then Minecraft-safe UI text. */
    private static String finalizeAnswer(String answer) {
        String clean = ItemResolver.stripMarker(answer == null ? "" : answer);
        clean = AskReplyScrub.scrubPromptEcho(clean);
        return Plainify.forMinecraftUi(clean);
    }
}

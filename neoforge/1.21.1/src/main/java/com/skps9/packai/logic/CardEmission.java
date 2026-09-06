package com.skps9.packai.logic;

import java.util.Locale;

/**
 * One recipe card scheduled for the AI-mode card strip (tool emission channel).
 * Dedupe key = item + category + primary output.
 * {@code refId} is the ask-scope {@code [card:N]} id (1-based; 0 = unset).
 */
public record CardEmission(String itemId, String role, RecipeCard card, int refId) {
    public CardEmission {
        itemId = itemId == null ? "" : itemId.trim();
        role = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        if (refId < 0) {
            refId = 0;
        }
    }

    /** Backward-compatible 3-arg (refId unset until {@link AskToolEnv#offerEmission}). */
    public CardEmission(String itemId, String role, RecipeCard card) {
        this(itemId, role, card, 0);
    }

    public String dedupeKey() {
        String cat = card == null ? "" : (card.categoryTitle() == null ? "" : card.categoryTitle());
        String out = card == null ? "" : card.primaryOutputId();
        return itemId.toLowerCase(Locale.ROOT) + "|" + cat + "|" + (out == null ? "" : out);
    }
}

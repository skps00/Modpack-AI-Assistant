package com.skps9.packai.logic;

import java.util.Locale;

/**
 * One recipe card scheduled for the AI-mode card strip (tool emission channel).
 * Dedupe key = item + category + primary output.
 */
public record CardEmission(String itemId, String role, RecipeCard card) {
    public CardEmission {
        itemId = itemId == null ? "" : itemId.trim();
        role = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
    }

    public String dedupeKey() {
        String cat = card == null ? "" : (card.categoryTitle() == null ? "" : card.categoryTitle());
        String out = card == null ? "" : card.primaryOutputId();
        return itemId.toLowerCase(Locale.ROOT) + "|" + cat + "|" + (out == null ? "" : out);
    }
}

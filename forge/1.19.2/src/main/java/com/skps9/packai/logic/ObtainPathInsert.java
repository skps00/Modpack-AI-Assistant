package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.world.item.Item;

/**
 * Obtain-path body insert: skip recipe lines already shown as recipe cards.
 */
public final class ObtainPathInsert {
    private ObtainPathInsert() {}

    /**
     * When {@code cardsOut > 0}, drop {@code R|recipeId} lines whose id is in {@code cardRecipeIds}.
     * Zero cards → input unchanged.
     */
    public static List<ObtainPathText.Line> filterLinesForCards(
            List<ObtainPathText.Line> lines, int cardsOut, Set<String> cardRecipeIds) {
        if (lines == null || lines.isEmpty() || cardsOut <= 0) {
            return lines == null ? List.of() : lines;
        }
        if (cardRecipeIds == null || cardRecipeIds.isEmpty()) {
            return lines;
        }
        List<ObtainPathText.Line> out = new ArrayList<>(lines.size());
        for (ObtainPathText.Line ln : lines) {
            if (ln == null) {
                continue;
            }
            String key = ln.key();
            if (key != null && key.startsWith("R|")) {
                String rid = key.substring(2).trim();
                if (!rid.isEmpty() && cardRecipeIds.contains(rid)) {
                    continue;
                }
            }
            out.add(ln);
        }
        return List.copyOf(out);
    }

    /** Harness negative control — no dedupe. */
    static List<ObtainPathText.Line> filterLinesForCardsDisabled(
            List<ObtainPathText.Line> lines, int cardsOut, Set<String> cardRecipeIds) {
        return lines == null ? List.of() : lines;
    }

    /**
     * Recipe ids for obtain lines covered by catalog cards: match card primary output to path steps.
     */
    public static Set<String> recipeIdsFromCards(
            List<RecipeCard> cards, List<ObtainPaths.Path> paths) {
        if (cards == null || cards.isEmpty() || paths == null || paths.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (RecipeCard card : cards) {
            if (card == null || card.isEmpty()) {
                continue;
            }
            String wantOut = card.primaryOutputId();
            for (ObtainPaths.Path path : paths) {
                if (path == null || path.steps() == null) {
                    continue;
                }
                for (ObtainPaths.Step step : path.steps()) {
                    if (step == null || step.edge() == null || step.edge().recipeId() == null) {
                        continue;
                    }
                    Item outItem = step.edge().output();
                    String stepOut = outItem == null ? "" : ObtainGraph.itemKey(outItem);
                    if (!wantOut.isEmpty() && !wantOut.equalsIgnoreCase(stepOut)) {
                        continue;
                    }
                    out.add(step.edge().recipeId().toString());
                }
            }
        }
        return Set.copyOf(out);
    }

    public static List<String> lineTexts(List<ObtainPathText.Line> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(lines.size());
        for (ObtainPathText.Line ln : lines) {
            if (ln != null && ln.text() != null && !ln.text().isBlank()) {
                out.add(ln.text());
            }
        }
        return out;
    }
}

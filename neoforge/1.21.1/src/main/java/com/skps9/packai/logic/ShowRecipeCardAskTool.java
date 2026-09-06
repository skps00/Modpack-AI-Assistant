package com.skps9.packai.logic;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;

/**
 * Retired — replaced by {@link RenderRecipeCardsAskTool}. Kept as a compile stub so
 * old references/docs do not break; not registered in {@link AskEngine}.
 */
public final class ShowRecipeCardAskTool implements AskTool {
    @Override
    public String name() {
        return "show_recipe_card";
    }

    @Override
    public String description() {
        return "RETIRED — use render_recipe_cards instead.";
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"],\"additionalProperties\":false}";
    }

    @Override
    public String run(AskToolArgs args) {
        return "";
    }
}

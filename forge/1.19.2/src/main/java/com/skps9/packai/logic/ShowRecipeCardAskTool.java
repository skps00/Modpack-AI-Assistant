package com.skps9.packai.logic;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;

/** Side-write {@code [[recipe_card:N]]} for the recipe the model is talking about. */
public final class ShowRecipeCardAskTool implements AskTool {
    @Override
    public String name() {
        return "show_recipe_card";
    }

    @Override
    public String description() {
        return "Attach the catalog JEI card for the recipe you are describing. "
                + "query=station or output name; card_index=N. Repeat per recipe. "
                + "Do not pick a generic Crafting use when you named a machine or other output.";
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"item\":{\"type\":\"string\"},\"variant_keys\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"dump_level\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"},\"card_index\":{\"type\":\"string\"}},\"required\":[\"query\"],\"additionalProperties\":false}";
    }

    @Override
    public String run(AskToolArgs args) {
        String q = firstNonBlank(args == null ? null : args.dumpLevel,
                args == null ? null : args.itemId,
                args == null ? null : args.question);
        AskToolEnv env = AskToolEnv.current();
        java.util.List<String> lines = env == null || env.recipeCardLines == null
                ? java.util.List.of() : env.recipeCardLines;
        int n = RecipeCardAlign.bestLineIndex(q, lines);
        if (n < 0 && q != null && q.matches("\\d+")) {
            try {
                n = Integer.parseInt(q.trim());
            } catch (NumberFormatException ignored) {
                n = -1;
            }
        }
        if (n < 0) {
            return "";
        }
        return "[[recipe_card:" + n + "]]";
    }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return c == null ? "" : c.trim();
    }
}

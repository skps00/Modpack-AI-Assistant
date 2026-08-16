package com.skps9.packai.logic;

/** Side-write {@code [[recipe_card:N]]} for the recipe the model is talking about. */
public final class ShowRecipeCardAskTool implements AskTool {
    @Override
    public String name() {
        return "show_recipe_card";
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

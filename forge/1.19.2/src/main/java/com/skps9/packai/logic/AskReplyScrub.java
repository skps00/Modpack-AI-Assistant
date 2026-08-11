package com.skps9.packai.logic;

import java.util.regex.Pattern;

/**
 * Post-LLM scrub: strip PURPOSE / prompt section tags echoed into the player answer.
 * Keeps intentional UI markers ({@code [[item:]]} / {@code [[recipe:]]} / {@code {{item:}}} /
 * {@code {{RECIPE}}}) for {@link RecipeEmbed}.
 * {@code [[recipe_cards:on|off]]} is scrubbed in {@link RecipeCardsMode#scrubMarker} /
 * {@link AskResult#withRecipeCards}.
 */
public final class AskReplyScrub {
    /**
     * PURPOSE / fact headers injected into prompts — never player-facing.
     * Matches {@code [SCROLL_EFFECT]}, {@code [PURPOSE]}, etc. (optional spaces).
     */
    private static final Pattern PROMPT_SECTION_TAG = Pattern.compile(
            "\\[\\s*(?:SCROLL_[A-Z0-9_]+|PURPOSE|GUIDE|VARIANT|AS_INGREDIENT|CONTAINED)\\s*\\]",
            Pattern.CASE_INSENSITIVE);

    private AskReplyScrub() {}

    /**
     * Remove leaked prompt section tags. Safe to run before {@link RecipeEmbed}
     * (does not touch recipe/item UI markers). Does not trim — callers tidy whitespace.
     */
    public static String scrubPromptEcho(String answer) {
        if (answer == null || answer.isEmpty()) {
            return "";
        }
        String t = PROMPT_SECTION_TAG.matcher(answer).replaceAll("");
        return t.replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n");
    }
}

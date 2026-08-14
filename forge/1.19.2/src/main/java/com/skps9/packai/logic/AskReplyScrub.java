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
            "\\[\\s*(?:SCROLL_[A-Z0-9_]+|PURPOSE|GUIDE|VARIANT|AS_INGREDIENT|CONTAINED|CONSUME_USE|TOOL_BUILD|TETRA_USE)\\s*\\]",
            Pattern.CASE_INSENSITIVE);

    /**
     * Lone How-to-get header with no obtain prose before 【来源】 / [Sources].
     * INPUT as-ingredient cards live in other parts — they do not fill this header.
     */
    private static final Pattern EMPTY_HOW_TO_GET = Pattern.compile(
            "(?im)^[ \\t]*(?:##[ \\t]*)?(?:怎么来|怎麼來|How to get)[ \\t]*[:：]?[ \\t]*\\r?\\n(?:[ \\t]*\\r?\\n)*(?=【来源】|【來源】|\\[Sources\\]|\\z)");

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
        t = EMPTY_HOW_TO_GET.matcher(t).replaceAll("");
        return t.replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n");
    }
}

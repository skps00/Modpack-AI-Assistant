package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Post-LLM scrub: strip PURPOSE / prompt section tags echoed into the player answer.
 * Also strips Pack AI tooltip overlay chrome before it enters {@code [PURPOSE]}.
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
            "\\[\\s*(?:SCROLL_[A-Z0-9_]+|PURPOSE|GUIDE|VARIANT|AS_INGREDIENT|CONTAINED|CONSUME_USE|TOOL_BUILD|TETRA_USE|WORLDGEN)\\s*\\]",
            Pattern.CASE_INSENSITIVE);

    /**
     * Lone How-to-get header with no obtain prose before 【来源】 / [Sources].
     * INPUT as-ingredient cards live in other parts — they do not fill this header.
     */
    private static final Pattern EMPTY_HOW_TO_GET = Pattern.compile(
            "(?im)^[ \\t]*(?:##[ \\t]*)?(?:怎么来|怎麼來|How to get)[ \\t]*[:：]?[ \\t]*\\r?\\n(?:[ \\t]*\\r?\\n)*(?=【来源】|【來源】|\\[Sources\\]|\\z)");

    /** Tetra / mod "Hold [shift] +" expand-more chrome — not in-game use. */
    private static final Pattern SHIFT_PLUS_CHROME = Pattern.compile("(?i)\\[shift\\]\\s*\\+");

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

    /**
     * Drop Pack AI GUI overlay / keybind chrome from captured item tooltips
     * before they enter {@code [PURPOSE]}. Keeps real lore, stats, mod use text.
     */
    public static String scrubPackAiTooltipChrome(String tooltip) {
        if (tooltip == null || tooltip.isBlank()) {
            return tooltip == null ? "" : tooltip;
        }
        String[] lines = tooltip.split("\\R", -1);
        List<String> keep = new ArrayList<>(lines.length);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || isPackAiTooltipChromeLine(line)) {
                continue;
            }
            keep.add(line);
        }
        return String.join("\n", keep);
    }

    static boolean isPackAiTooltipChromeLine(String line) {
        if (line.length() >= 2) {
            boolean allBars = true;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) != '|') {
                    allBars = false;
                    break;
                }
            }
            if (allBars) {
                return true;
            }
        }
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("packai.screen.") || lower.contains("packai.tooltip.")) {
            return true;
        }
        if (SHIFT_PLUS_CHROME.matcher(line).find()) {
            return true;
        }
        if (lower.contains("ask pack ai") || lower.contains("clears multi-select")) {
            return true;
        }
        if (line.contains("单独询问") || line.contains("單獨詢問")
                || line.contains("清除多选") || line.contains("清除多選")
                || line.contains("来用 Pack AI") || line.contains("來用 Pack AI")) {
            return true;
        }
        if (line.contains("AI 正在思考") || lower.contains("ai is thinking")) {
            return true;
        }
        return false;
    }
}

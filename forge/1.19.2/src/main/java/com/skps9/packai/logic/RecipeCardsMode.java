package com.skps9.packai.logic;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.skps9.packai.config.PackAiConfig;

/**
 * When Ask attaches JEI recipe cards. Config key {@code recipeCardsMode}.
 * Card bodies always come from JEI — modes only gate show/hide.
 */
public enum RecipeCardsMode {
    /** Craft / acquire keyword gate (historical behavior). */
    KEYWORDS,
    /**
     * Default. Collect when LLM expected; attach only if reply has {@code [[recipe_cards:on]]}
     * (or implicit on via {@code [[recipe_card:N]]}). Offline / no cloud key → {@link #KEYWORDS}.
     */
    AI,
    /** Attach whenever JEI cards were collected (ignore keywords). */
    ALWAYS,
    /** Never collect or attach. */
    NEVER;

    /** Exact marker the LLM must emit (case-insensitive value). */
    public static final String MARKER_ON = "[[recipe_cards:on]]";
    public static final String MARKER_OFF = "[[recipe_cards:off]]";

    private static final Pattern MARKER = Pattern.compile(
            "\\[\\[\\s*recipe_cards\\s*:\\s*(on|off)\\s*\\]\\]",
            Pattern.CASE_INSENSITIVE);
    /** Per-card interleave marker — implies show when gate marker omitted. */
    private static final Pattern CARD_INDEX = Pattern.compile(
            "\\[\\[\\s*recipe_card\\s*:\\s*\\d+\\s*\\]\\]",
            Pattern.CASE_INSENSITIVE);

    public static RecipeCardsMode current() {
        return parse(PackAiConfig.recipeCardsMode());
    }

    public static RecipeCardsMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AI;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "keywords" -> KEYWORDS;
            case "ai" -> AI;
            case "always" -> ALWAYS;
            case "never" -> NEVER;
            default -> AI;
        };
    }

    public String configId() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether to collect JEI cards / recipe-get prompt text for this Ask.
     * AI + offline/no-key → keywords. AI + LLM expected → always collect (LLM decides attach).
     */
    public boolean shouldCollect(String question) {
        return switch (this) {
            case NEVER -> false;
            case ALWAYS -> true;
            case KEYWORDS -> PackIndex.shouldAttachAskRecipeCards(question);
            case AI -> llmExpected()
                    ? true
                    : PackIndex.shouldAttachAskRecipeCards(question);
        };
    }

    /**
     * @param marker {@link Boolean#TRUE}=on, {@link Boolean#FALSE}=off, {@code null}=absent
     */
    public List<RecipeCard> resolveAttach(
            List<RecipeCard> collected,
            Boolean marker,
            String question
    ) {
        if (collected == null || collected.isEmpty()) {
            return List.of();
        }
        return switch (this) {
            case NEVER -> List.of();
            case ALWAYS, KEYWORDS -> List.copyOf(collected);
            case AI -> {
                if (Boolean.TRUE.equals(marker)) {
                    yield List.copyOf(collected);
                }
                if (Boolean.FALSE.equals(marker)) {
                    yield List.of();
                }
                // No marker: offline / no-LLM → keywords; craft/acquire asks → still attach
                // (LLM often forgets [[recipe_cards:on]] when purpose_first leads with 用途).
                if (!llmExpected()
                        || PackIndex.isCraftOrientedQuestion(question)
                        || PackIndex.isAcquireOrientedQuestion(question)) {
                    yield PackIndex.shouldAttachAskRecipeCards(question)
                            ? List.copyOf(collected)
                            : List.of();
                }
                yield List.of();
            }
        };
    }

    /** {@code true}=on, {@code false}=off, {@code null}=no marker. Last match wins. */
    public static Boolean parseMarker(String answer) {
        if (answer == null || answer.isEmpty()) {
            return null;
        }
        Matcher m = MARKER.matcher(answer);
        Boolean last = null;
        while (m.find()) {
            last = "on".equalsIgnoreCase(m.group(1));
        }
        return last;
    }

    /** Whether answer places at least one {@code [[recipe_card:N]]}. */
    public static boolean hasCardIndexMarker(String answer) {
        return answer != null && !answer.isEmpty() && CARD_INDEX.matcher(answer).find();
    }

    /**
     * Gate for AI attach: explicit on/off, else implicit on when {@code [[recipe_card:N]]} present.
     * Explicit off always wins.
     */
    public static Boolean resolveGateMarker(String answer) {
        Boolean gate = parseMarker(answer);
        if (gate != null) {
            return gate;
        }
        return hasCardIndexMarker(answer) ? Boolean.TRUE : null;
    }

    public static String scrubMarker(String answer) {
        if (answer == null || answer.isEmpty()) {
            return "";
        }
        String t = MARKER.matcher(answer).replaceAll("");
        return t.replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n");
    }

    /**
     * Offline mode, or Auto/Cloud with empty API key (no LLM expected).
     * Ollama-forced mode still expects LLM (may fail later → no marker → no cards).
     */
    public static boolean llmExpected() {
        String mode = PackAiConfig.resolvedMode();
        if ("offline".equals(mode)) {
            return false;
        }
        if ("ollama".equals(mode)) {
            return true;
        }
        // cloud or auto: need a key for cloud path; auto without key may use Ollama —
        // treat no-key Auto as keywords fallback (tooltip documents this).
        if ("cloud".equals(mode) || "auto".equals(mode)) {
            String key = LlmClient.resolveApiKey();
            if ("cloud".equals(mode)) {
                return key != null && !key.isBlank();
            }
            // auto
            return key != null && !key.isBlank();
        }
        return false;
    }
}

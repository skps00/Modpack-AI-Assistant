package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
        return resolveAttach(collected, marker, question, null);
    }

    /**
     * Same as {@link #resolveAttach(List, Boolean, String)}. Align may match reply to
     * cards but must not reorder — marker N is collected index. Specific reply + no
     * match → omit (no Crafting dump).
     */
    public List<RecipeCard> resolveAttach(
            List<RecipeCard> collected,
            Boolean marker,
            String question,
            String answer
    ) {
        List<RecipeCard> raw = resolveAttachRaw(collected, marker, question);
        if (raw.isEmpty()) {
            return raw;
        }
        raw = dropUnreferencedMaintenance(raw, answer);
        if (raw.isEmpty()) {
            return raw;
        }
        // Match reply to cards for the empty-guard only — NEVER reorder via take().
        // Marker N == collected index (ensureCards writes it); reordering the attached
        // list breaks RecipeEmbed.resolveCardIndex cards.get(N). So pickIndices is used
        // ONLY as a yes/no "does reply mention any card" gate; the returned order is
        // deliberately ignored (we return raw, collected original order).
        List<Integer> aligned = RecipeCardAlign.pickIndices(answer, fingerprints(collected));
        if (!aligned.isEmpty()) {
            return raw;  // keep collected original order — marker N == attached[N]
        }
        if (RecipeCardAlign.replyLooksSpecific(answer)) {
            return List.of();
        }
        return raw;
    }

    private List<RecipeCard> resolveAttachRaw(
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

    static List<RecipeCardAlign.Fingerprint> fingerprints(List<RecipeCard> cards) {
        List<RecipeCardAlign.Fingerprint> out = new ArrayList<>();
        if (cards == null) {
            return out;
        }
        for (int i = 0; i < cards.size(); i++) {
            RecipeCard c = cards.get(i);
            if (c == null) {
                continue;
            }
            out.add(new RecipeCardAlign.Fingerprint(
                    i,
                    Plainify.stripMcFormat(c.categoryTitle()),
                    stackNames(c.outputs()),
                    stackNames(c.catalysts()),
                    extraNames(c)));
        }
        return out;
    }

    private static List<RecipeCard> take(List<RecipeCard> cards, List<Integer> idx) {
        if (cards == null || idx == null || idx.isEmpty()) {
            return List.of();
        }
        List<RecipeCard> out = new ArrayList<>();
        LinkedHashSet<Integer> seen = new LinkedHashSet<>();
        for (int i : idx) {
            if (i < 0 || i >= cards.size() || !seen.add(i)) {
                continue;
            }
            RecipeCard c = cards.get(i);
            if (c != null && !c.isEmpty()) {
                out.add(c);
            }
        }
        return List.copyOf(out);
    }

    private static List<String> stackNames(List<net.minecraft.world.item.ItemStack> stacks) {
        List<String> out = new ArrayList<>();
        if (stacks == null) {
            return out;
        }
        for (net.minecraft.world.item.ItemStack st : stacks) {
            if (st == null || st.isEmpty()) {
                continue;
            }
            try {
                String n = Plainify.stripMcFormat(st.getHoverName().getString());
                if (n != null && !n.isBlank()) {
                    out.add(n);
                }
            } catch (Throwable ignored) {
                // headless
            }
            try {
                var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem());
                if (key != null) {
                    out.add(key.toString());
                }
            } catch (Throwable ignored) {
                // headless
            }
        }
        return out;
    }

    private static List<String> extraNames(RecipeCard c) {
        List<String> out = new ArrayList<>();
        if (c.otherOutputs() != null) {
            for (RecipeExtra e : c.otherOutputs()) {
                if (e == null) {
                    continue;
                }
                if (!e.label().isBlank()) {
                    out.add(e.label());
                }
                if (!e.uniqueId().isBlank()) {
                    out.add(e.uniqueId());
                }
            }
        }
        if (c.fluidOutputs() != null) {
            for (var f : c.fluidOutputs()) {
                if (f == null || f.isEmpty()) {
                    continue;
                }
                try {
                    String n = Plainify.stripMcFormat(f.getDisplayName().getString());
                    if (n != null && !n.isBlank()) {
                        out.add(n);
                    }
                } catch (Throwable ignored) {
                    // headless
                }
            }
        }
        return out;
    }

    /**
     * Optional anvil-style trailing cards (repair MAINTENANCE / upgrade UPGRADE) attach
     * only when the final answer references their [[recipe_card:N]] marker. Trailing
     * unreferenced optional suffix is removed; normal prefix stays stable so marker N
     * == attached[N]. When answer is null, no marker can reference them — drop the
     * whole trailing optional suffix.
     */
    private static List<RecipeCard> dropUnreferencedMaintenance(
            List<RecipeCard> raw, String answer
    ) {
        boolean anyMaintenance = false;
        for (RecipeCard c : raw) {
            if (c != null && c.isTrailingOptional()) {
                anyMaintenance = true;
                break;
            }
        }
        if (!anyMaintenance) {
            return raw;
        }
        java.util.Set<Integer> referenced = new java.util.LinkedHashSet<>();
        if (answer != null) {
            Matcher m = CARD_INDEX.matcher(answer);
            while (m.find()) {
                String token = m.group();
                int colon = token.indexOf(':');
                int close = token.lastIndexOf(']') - 1; // first of the two ]] — end-exclusive
                if (colon >= 0 && close > colon + 1) {
                    try {
                        referenced.add(Integer.parseInt(token.substring(colon + 1, close).trim()));
                    } catch (NumberFormatException ignored) {
                        // malformed marker — not a card reference
                    }
                }
            }
        }
        int keepEnd = raw.size();
        while (keepEnd > 0) {
            RecipeCard c = raw.get(keepEnd - 1);
            if (c == null || !c.isTrailingOptional() || referenced.contains(keepEnd - 1)) {
                break;
            }
            keepEnd--;
        }
        return keepEnd == raw.size() ? raw : List.copyOf(raw.subList(0, keepEnd));
    }
}

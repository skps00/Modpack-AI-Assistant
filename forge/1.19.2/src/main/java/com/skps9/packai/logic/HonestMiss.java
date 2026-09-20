package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * WP2 — honest Gate/Loot miss UX. When pack index has no obtain edges and JEI did not
 * supply a craft path, pin a fixed unknown line so the LLM does not invent drops / stages /
 * advancement lists. No pack id hardcodes.
 *
 * <p>Keep acquire-oriented detection self-contained (no {@link PackIndex} class load) so
 * headless {@code -ea} checks stay free of Minecraft.
 */
public final class HonestMiss {
    private HonestMiss() {}

    /**
     * Pin acquire-index miss FACT when: held item known, acquire empty, no JEI how-to-get,
     * and the question looks obtain-oriented.
     */
    public static boolean shouldPinAcquireMiss(
            List<String> acquire,
            boolean hasRecipeGet,
            String question,
            String heldItemId
    ) {
        if (heldItemId == null || heldItemId.isBlank()) {
            return false;
        }
        if (acquire != null && !acquire.isEmpty()) {
            return false;
        }
        if (hasRecipeGet) {
            return false;
        }
        return isAcquireOrientedQuestion(question);
    }

    /** Mirror {@link PackIndex#isAcquireOrientedQuestion} without loading PackIndex. */
    static boolean isAcquireOrientedQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String q = question.toLowerCase(Locale.ROOT);
        return q.contains("如何取得")
                || q.contains("怎麼取得")
                || q.contains("怎么取得")
                || q.contains("如何獲得")
                || q.contains("如何获得")
                || q.contains("怎麼獲得")
                || q.contains("怎么获得")
                || q.contains("怎样获得")
                || q.contains("怎樣獲得")
                || q.contains("怎样取得")
                || q.contains("怎樣取得")
                || q.contains("怎麼來")
                || q.contains("怎么来")
                || q.contains("怎样来")
                || q.contains("怎樣來")
                || q.contains("如何得到")
                || q.contains("怎麼得到")
                || q.contains("怎么得到")
                || q.contains("怎样得到")
                || q.contains("怎樣得到")
                || q.contains("how to get")
                || q.contains("how do i get")
                || q.contains("where to get")
                || q.contains("where can i get")
                || q.contains("obtain")
                || q.contains("how to summon")
                || q.contains("summon")
                || q.contains("召唤")
                || q.contains("召喚");
    }

    /** Summon ask with no local JEI / summon FACT — do not let web invent a ritual. */
    public static boolean shouldPinSummonMiss(boolean hasJei, boolean hasSummonFact, String question) {
        if (hasJei || hasSummonFact) {
            return false;
        }
        return SummonRecipeLookup.isSummonQuestion(question);
    }

    public static List<String> summonMissFacts(String lang, List<String> closestNames) {
        String code = lang == null || lang.isBlank() ? ReplyLang.current() : lang.trim();
        List<String> out = new ArrayList<>();
        out.add(ReplyLang.summonIndexMiss(code));
        if (closestNames != null && !closestNames.isEmpty()) {
            String joined = String.join(ReplyLang.sourceJoin(code), closestNames);
            out.add(ReplyLang.summonClosest(code, joined));
        }
        return List.copyOf(out);
    }

    /** Player-visible summon miss — no model commands. */
    public static List<String> summonMissFactsPlayer(String lang, List<String> closestNames) {
        String code = lang == null || lang.isBlank() ? ReplyLang.current() : lang.trim();
        List<String> out = new ArrayList<>();
        out.add(ReplyLang.askMissSummonPlayer(code));
        if (closestNames != null && !closestNames.isEmpty()) {
            String joined = String.join(ReplyLang.sourceJoin(code), closestNames);
            out.add(ReplyLang.summonClosest(code, joined));
        }
        return List.copyOf(out);
    }

    /** Header + fixed miss line (localized). Empty if item id blank. */
    public static List<String> acquireMissFacts(String itemId, String lang) {
        if (itemId == null || itemId.isBlank()) {
            return List.of();
        }
        String code = lang == null || lang.isBlank() ? ReplyLang.current() : lang.trim();
        return List.of(
                ReplyLang.localAcquireHeader(code, Plainify.displayName(itemId)),
                ReplyLang.acquireIndexMiss(code)
        );
    }

    /** Player-visible acquire miss — no model commands. Empty if item id blank. */
    public static List<String> acquireMissFactsPlayer(String itemId, String lang) {
        if (itemId == null || itemId.isBlank()) {
            return List.of();
        }
        String code = lang == null || lang.isBlank() ? ReplyLang.current() : lang.trim();
        return List.of(
                ReplyLang.localAcquireHeader(code, Plainify.displayName(itemId)),
                ReplyLang.askMissAcquirePlayer(code)
        );
    }

    /**
     * Post-LLM: force player-visible acquire-miss line into the answer when {@code force}.
     * No-op when force=false, body already contains the line, or lang line blank.
     * Precedence mirrors {@link RecipeGetMarks#ensureVisibleInReply} (insert before 【來源】).
     */
    public static String ensureAskMissAcquirePlayerVisible(String body, String replyLang, boolean force) {
        if (!force) {
            return body == null ? "" : body;
        }
        String code = replyLang == null || replyLang.isBlank() ? ReplyLang.current() : replyLang.trim();
        String line = ReplyLang.askMissAcquirePlayer(code);
        return insertLineBeforeSources(body, line);
    }

    /**
     * Deterministic blank-frame craft line for STANDARD frames.
     * Shapeless vs shaped + multi-variant clause from recipe metadata. Empty on miss.
     */
    public static String frameStandardRecipeLine(String replyLang, ModularFrameStandard.FrameRecipe recipe) {
        if (recipe == null || !recipe.hasCraftLine()) {
            return "";
        }
        String code = replyLang == null || replyLang.isBlank() ? ReplyLang.current() : replyLang.trim();
        String materials = joinItemLabels(recipe.ingredientItemIds());
        String result = itemLabel(recipe.resultItemId());
        if (materials.isBlank() || result.isBlank()) {
            return "";
        }
        String key = recipe.shapeless()
                ? "packai.reply.frame_standard_recipe"
                : "packai.reply.frame_standard_recipe_shaped";
        String line = ReplyLang.tr(code, key, materials, result);
        if (line == null || line.isBlank() || line.equals(key)) {
            return "";
        }
        if (recipe.variantCount() > 1) {
            String variants = ReplyLang.tr(
                    code, "packai.reply.frame_standard_recipe_variants", String.valueOf(recipe.variantCount()));
            if (variants != null && !variants.isBlank()
                    && !variants.equals("packai.reply.frame_standard_recipe_variants")) {
                line = line + variants;
            }
        }
        return line;
    }

    /**
     * Post-LLM: force blank-frame craft line when STANDARD frame matched a jar recipe.
     * No-op when recipe lacks craft metadata, body already has materials+result names,
     * or lang template blank. Precedence mirrors {@link #ensureAskMissAcquirePlayerVisible}.
     */
    public static String ensureFrameStandardRecipeVisible(
            String body,
            String replyLang,
            ModularFrameStandard.FrameRecipe recipe
    ) {
        if (recipe == null || !recipe.hasCraftLine()) {
            return body == null ? "" : body;
        }
        // Already has core names → model (or prior insert) covered the craft line.
        if (body != null && bodyContainsAllLabels(body, recipe.ingredientItemIds(), recipe.resultItemId())) {
            return body;
        }
        String line = frameStandardRecipeLine(replyLang, recipe);
        if (line == null || line.isBlank()) {
            return body == null ? "" : body;
        }
        return insertLineBeforeSources(body, line);
    }

    /** Language-aware item label: official hover when available, else path-token fallback. */
    static String itemLabel(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "";
        }
        String official = OfficialDisplay.officialName(itemId);
        if (official != null && !official.isBlank()) {
            return official;
        }
        return Plainify.displayName(itemId);
    }

    static String joinItemLabels(List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String id : itemIds) {
            String label = itemLabel(id);
            if (label.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(label);
        }
        return sb.toString();
    }

    static boolean bodyContainsAllLabels(String body, List<String> ingredientIds, String resultId) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String result = itemLabel(resultId);
        if (result.isBlank() || !body.contains(result)) {
            return false;
        }
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return false;
        }
        for (String id : ingredientIds) {
            String label = itemLabel(id);
            if (label.isBlank() || !body.contains(label)) {
                return false;
            }
        }
        return true;
    }

    static String insertLineBeforeSources(String body, String line) {
        if (line == null || line.isBlank()) {
            return body == null ? "" : body;
        }
        if (body != null && body.contains(line)) {
            return body;
        }
        if (body == null || body.isBlank()) {
            return line;
        }
        var m = ReplySources.HEADER.matcher(body);
        if (m.find()) {
            int at = m.start();
            String before = body.substring(0, at).stripTrailing();
            String after = body.substring(at);
            return before + "\n\n" + line + "\n\n" + after;
        }
        return body.stripTrailing() + "\n\n" + line;
    }
}

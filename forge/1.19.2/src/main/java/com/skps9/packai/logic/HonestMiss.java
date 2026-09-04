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
}

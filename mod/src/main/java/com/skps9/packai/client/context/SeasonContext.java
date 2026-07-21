package com.skps9.packai.client.context;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.skps9.packai.logic.ModScanners;
import com.skps9.packai.logic.ReplyLang;

import net.minecraft.client.player.LocalPlayer;

/**
 * Season hints only when the loaded mod list includes a season mod and the ask looks crop-related.
 */
public final class SeasonContext {
    private static final Pattern CROP_TOPIC = Pattern.compile(
            "種|農|作物|季節|收成|播種|season|crop|plant|farm|harvest|grow|seed",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private SeasonContext() {}

    /**
     * Plain-text block for LLM, or empty when no season mod in this pack or topic unrelated.
     */
    public static String summary(LocalPlayer player, List<String> modIds, String question, String heldItemId) {
        return summary(player, modIds, question, heldItemId, ReplyLang.current());
    }

    /**
     * Plain-text block for LLM, or empty when no season mod in this pack or topic unrelated.
     */
    public static String summary(
            LocalPlayer player,
            List<String> modIds,
            String question,
            String heldItemId,
            String replyLang
    ) {
        if (player == null || !applies(modIds, question, heldItemId)) {
            return "";
        }
        String lang = replyLang == null || replyLang.isBlank() ? "zh_tw" : replyLang.trim();
        StringBuilder sb = new StringBuilder();
        if (ModScanners.hasMod(modIds, "sereneseasons")) {
            long day = player.level().getDayTime() / 24000L;
            int sub = (int) ((day / 8L) % 12L);
            String[] subs = ReplyLang.seasonSubs(lang);
            String subName = subs[Math.floorMod(sub, subs.length)];
            sb.append(ReplyLang.seasonSerene(lang, subName, day));
        }
        if (ModScanners.hasMod(modIds, "farmersdelight")
                && ModScanners.hasAnyMod(modIds, "sereneseasons")) {
            sb.append(ReplyLang.seasonFarmersDelight(lang));
        }
        return sb.toString().trim();
    }

    /** Pack has a season mod and the question or held item looks crop-related. */
    public static boolean applies(List<String> modIds, String question, String heldItemId) {
        if (!ModScanners.hasAnyMod(modIds, "sereneseasons")) {
            return false;
        }
        if (question != null && CROP_TOPIC.matcher(question).find()) {
            return true;
        }
        if (heldItemId != null && !heldItemId.isBlank()) {
            String ns = heldItemId.contains(":")
                    ? heldItemId.substring(0, heldItemId.indexOf(':')).toLowerCase(Locale.ROOT)
                    : "";
            return ModScanners.hasMod(modIds, ns)
                    && ("farmersdelight".equals(ns) || "sereneseasons".equals(ns));
        }
        return false;
    }
}

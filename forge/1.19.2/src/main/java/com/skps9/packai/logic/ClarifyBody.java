package com.skps9.packai.logic;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Pure string assemble for clarify replies. Display names / mod labels must already
 * be resolved by the MC-aware caller ({@code AskEngine.resolvedDisplayNames}).
 * Body never includes recipes, {@code [Sources]}, or {@code <!--packai:items=}.
 */
public final class ClarifyBody {
    private ClarifyBody() {}

    /**
     * Build from {@link AskLoopState#pendingClarify()} + resolved display map
     * (raw guess/candidate → player-visible label).
     */
    public static String build(AskLoopState loop, Map<String, String> displayByRaw) {
        if (loop == null) {
            return buildUnclear(ReplyLang.normalize(null));
        }
        String lang = loop.lang();
        ClarifyArgs.PendingClarify pending = loop.pendingClarify();
        if (pending == null) {
            return buildUnclear(lang);
        }
        return buildFrom(pending.guess(), pending.candidates(), displayByRaw, lang);
    }

    /**
     * Second {@code ask_clarify} in one chat sets {@link AskLoopState#clarifyRefused()}
     * and returns no pending body. Blank model text must still use the clarify fallback,
     * not the generic langfallback line.
     */
    public static boolean clarifyRefusedBlank(AskLoopState loop, String answer) {
        return loop != null && loop.clarifyRefused()
                && (answer == null || answer.isBlank());
    }

    /** Miss / early-exit fallback — no guess or candidates. */
    public static String buildFallback(AskLoopState loop, Map<String, String> displayByRaw) {
        String lang = loop == null ? ReplyLang.normalize(null) : loop.lang();
        return buildUnclear(lang);
    }

    static String buildFrom(
            String guess,
            List<String> candidates,
            Map<String, String> displayByRaw,
            String lang
    ) {
        String g = guess == null ? "" : guess.trim();
        List<String> cands = candidates == null ? List.of() : candidates;
        Map<String, String> names = displayByRaw == null ? Map.of() : displayByRaw;

        if (!g.isBlank()) {
            String label = names.getOrDefault(g, g);
            return ReplyLang.tr(lang, "packai.reply.clarify.confirm", label).trim();
        }
        if (!cands.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(ReplyLang.tr(lang, "packai.reply.clarify.which").trim());
            int n = 0;
            for (String raw : cands) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                if (n >= ClarifyArgs.MAX_CANDIDATES) {
                    break;
                }
                n++;
                String label = names.getOrDefault(raw.trim(), raw.trim());
                String mod = ClarifyArgs.modOf(raw);
                if (mod.isBlank()) {
                    // Free-text candidate (e.g. mod display name) — repeat as second column.
                    mod = label;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(String.format(java.util.Locale.ROOT, "%d) %s — %s", n, label, mod));
            }
            return sb.toString().trim();
        }
        return buildUnclear(lang);
    }

    static String buildUnclear(String lang) {
        return ReplyLang.tr(lang, "packai.reply.clarify.unclear").trim();
    }

    /** Appended when session clarify limit already used and miss path still fires. */
    public static String uncertainMark(String lang) {
        return ReplyLang.tr(lang, "packai.reply.clarify.uncertain").trim();
    }

    /** Append uncertain mark to an existing miss body (newline if needed). */
    public static String appendUncertain(String body, String lang) {
        String mark = uncertainMark(lang);
        if (mark.isBlank()) {
            return body == null ? "" : body;
        }
        String base = body == null ? "" : body.trim();
        if (base.isBlank()) {
            return mark;
        }
        if (base.contains(mark)) {
            return base;
        }
        return base + "\n" + mark;
    }

    /**
     * Limit used, online, clarify was asked, and the answer has no pack-local material.
     */
    public static boolean shouldMarkUncertain(
            boolean clarifyEnabled, boolean offline, boolean clarifyAsked, boolean hasLocalMaterial) {
        return clarifyEnabled && !offline && clarifyAsked && !hasLocalMaterial;
    }

    /**
     * Insert {@link #uncertainMark} immediately before the sources footer
     * ({@link ReplySources#HEADER}). No footer → append. Mark already present → unchanged.
     */
    public static String appendUncertainBeforeFooter(String body, String lang) {
        String mark = uncertainMark(lang);
        if (mark.isBlank()) {
            return body == null ? "" : body;
        }
        String base = body == null ? "" : body;
        if (base.contains(mark)) {
            return base;
        }
        Matcher footer = ReplySources.HEADER.matcher(base);
        if (!footer.find()) {
            String trimmed = base.trim();
            if (trimmed.isEmpty()) {
                return mark;
            }
            return trimmed + "\n" + mark;
        }
        int at = footer.start();
        String pre = base.substring(0, at).stripTrailing();
        String post = base.substring(at);
        StringBuilder out = new StringBuilder();
        if (!pre.isEmpty()) {
            out.append(pre).append('\n');
        }
        out.append(mark);
        if (!post.startsWith("\n")) {
            out.append('\n');
        }
        out.append(post);
        return out.toString();
    }
}

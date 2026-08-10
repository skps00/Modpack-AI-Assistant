package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure helpers for NBT/schematic identity (Tetra scrolls etc.) — no Minecraft types.
 */
final class ItemVariantKeysText {
    static final String HEADER = "[VARIANT]";
    static final String SCROLL_EFFECT_HEADER = "[SCROLL_EFFECT]";
    static final String SCROLL_MECH_HEADER = "[SCROLL_MECH]";
    static final String SCROLL_UNLOCK_HEADER = TetraSchematicText.SCROLL_UNLOCK_HEADER;
    static final String SCROLL_MATERIALS_HEADER = TetraSchematicText.SCROLL_MATERIALS_HEADER;

    /** Tetra lang lines that describe how scrolls unlock workbench content (placement, not RMB learn). */
    private static final List<String> SCROLL_MECH_LANG_KEYS = List.of(
            "item.tetra.scroll.schematics.description",
            "item.tetra.scroll.range.description",
            "item.tetra.scroll.effects.description",
            "item.tetra.scroll.intricate.description"
    );

    private ItemVariantKeysText() {}

    /**
     * Tetra scroll {@code key} values: path-like ({@code hone/gild_2}), namespaced, or bare
     * treatise/pack ids ({@code fabric_expertise}, {@code energy_bottle}).
     */
    static boolean acceptKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String s = raw.trim();
        if (s.length() < 2 || s.length() > 64) {
            return false;
        }
        if (s.contains(":") || s.contains("/")) {
            return true;
        }
        // Bare scroll ids: underscore (energy_bottle) or short pack keys (terra); min len 3.
        if (s.length() < 3) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c >= 'a' && c <= 'z')
                    && !(c >= 'A' && c <= 'Z')
                    && !(c >= '0' && c <= '9')
                    && c != '_'
                    && c != '-'
                    && c != '.') {
                return false;
            }
        }
        return true;
    }

    /** Path used in {@code item.tetra.scroll.<path>.description}. */
    static String scrollLangPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String id = raw.trim();
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    /**
     * PURPOSE block from Tetra lang keys for scroll variant ids. Empty when none translate.
     * Skips placeholder "shift + rmb read more" extended lines (details live in ScrollScreen).
     */
    static String scrollEffectPurposeLines(List<String> variantIds, java.util.function.Function<String, String> translate) {
        if (variantIds == null || variantIds.isEmpty() || translate == null) {
            return "";
        }
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        for (String raw : variantIds) {
            String path = scrollLangPath(raw);
            if (path.isBlank()) {
                continue;
            }
            for (String suf : List.of(".description", ".description_extended")) {
                String langKey = "item.tetra.scroll." + path + suf;
                String t = translate.apply(langKey);
                if (t == null || t.isBlank() || t.equals(langKey)) {
                    continue;
                }
                String plain = t.trim();
                String low = plain.toLowerCase(Locale.ROOT);
                if (low.contains("shift") && (low.contains("read more") || low.contains("rmb")) && plain.length() < 48) {
                    continue;
                }
                lines.add(plain);
            }
        }
        if (lines.isEmpty()) {
            return "";
        }
        return SCROLL_EFFECT_HEADER + "\n" + String.join("\n", lines);
    }

    /**
     * Canonical Tetra scroll use: place near workbench to unlock — not RMB "learn".
     * Prefers packai pin + Tetra I18n placement/effect lines when translated.
     */
    static String scrollMechanicsPurposeLines(java.util.function.Function<String, String> translate) {
        if (translate == null) {
            return "";
        }
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        String pin = translate.apply("packai.reply.tetra_scroll_mech");
        if (pin != null && !pin.isBlank() && !pin.equals("packai.reply.tetra_scroll_mech")) {
            lines.add(pin.trim());
        }
        for (String langKey : SCROLL_MECH_LANG_KEYS) {
            String t = translate.apply(langKey);
            if (t == null || t.isBlank() || t.equals(langKey)) {
                continue;
            }
            String plain = t.trim();
            if (!plain.isBlank()) {
                lines.add(plain);
            }
        }
        if (lines.isEmpty()) {
            return "";
        }
        return SCROLL_MECH_HEADER + "\n" + String.join("\n", lines);
    }

    /**
     * Tooltip keywords that mean Tetra schematic/treatise scroll placement (any locale).
     * Used when NBT extract missed but Shift tooltip already states workbench unlock.
     */
    static boolean tooltipHintsTetraScroll(String tip) {
        if (tip == null || tip.isBlank()) {
            return false;
        }
        String t = tip.toLowerCase(Locale.ROOT);
        if (t.contains("5x5x5") || t.contains("5×5×5")) {
            return true;
        }
        if (t.contains("nearby workbench") || t.contains("near a workbench")) {
            return true;
        }
        if (tip.contains("附近工作台") || tip.contains("解锁附近") || tip.contains("解鎖附近")) {
            return true;
        }
        if (tip.contains("图纸") || tip.contains("圖紙") || tip.contains("示意圖") || tip.contains("示意图")) {
            return true;
        }
        if (t.contains("schematic") && (t.contains("unlock") || t.contains("workbench"))) {
            return true;
        }
        return false;
    }

    /** {@code [VARIANT] schematic: tetra:mirror} (comma-joined when several). */
    static String purposeLine(List<String> schematics) {
        if (schematics == null || schematics.isEmpty()) {
            return "";
        }
        List<String> clean = new ArrayList<>();
        for (String s : schematics) {
            if (s != null && !s.isBlank()) {
                clean.add(s.trim());
            }
        }
        if (clean.isEmpty()) {
            return "";
        }
        return HEADER + " schematic: " + String.join(", ", clean);
    }

    /**
     * Expand schematic ids into match tokens: full id, path after {@code :}, last path segment.
     */
    static List<String> expandTokens(List<String> schematics) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (schematics == null) {
            return List.of();
        }
        for (String raw : schematics) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String id = raw.trim().toLowerCase(Locale.ROOT);
            out.add(id);
            int colon = id.indexOf(':');
            String path = colon >= 0 ? id.substring(colon + 1) : id;
            if (!path.isBlank()) {
                out.add(path);
            }
            int slash = path.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < path.length()) {
                out.add(path.substring(slash + 1));
            }
        }
        return new ArrayList<>(out);
    }

    /** True when blob/items mention any disambiguator token (length ≥ 2). */
    static boolean mentionsAny(String blob, List<String> itemIds, List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return true;
        }
        String b = blob == null ? "" : blob.toLowerCase(Locale.ROOT);
        Set<String> items = new LinkedHashSet<>();
        if (itemIds != null) {
            for (String it : itemIds) {
                if (it != null && !it.isBlank()) {
                    items.add(it.toLowerCase(Locale.ROOT));
                }
            }
        }
        for (String tok : tokens) {
            if (tok == null) {
                continue;
            }
            String t = tok.trim().toLowerCase(Locale.ROOT);
            if (t.length() < 2) {
                continue;
            }
            if (b.contains(t)) {
                return true;
            }
            for (String it : items) {
                if (it.contains(t)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Soft prefer: if any hit mentions a variant token, drop the rest; if none do, keep all
     * (quest files may only store bare registry id).
     */
    static <T> List<T> preferMentioning(
            List<T> hits, List<String> tokens, java.util.function.Function<T, Boolean> mentions
    ) {
        if (hits == null || hits.isEmpty() || tokens == null || tokens.isEmpty()) {
            return hits == null ? List.of() : hits;
        }
        List<T> ok = new ArrayList<>();
        for (T h : hits) {
            if (Boolean.TRUE.equals(mentions.apply(h))) {
                ok.add(h);
            }
        }
        return ok.isEmpty() ? hits : ok;
    }
}

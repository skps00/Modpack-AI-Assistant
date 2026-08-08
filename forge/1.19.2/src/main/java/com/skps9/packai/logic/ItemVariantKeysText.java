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

    private ItemVariantKeysText() {}

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

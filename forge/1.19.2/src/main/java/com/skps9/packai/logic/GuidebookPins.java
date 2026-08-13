package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;

/**
 * Ask-path helpers for guidebook pins: scope, format, quest dedupe, title search, related hop.
 * Pure logic — no ResourceManager / Patchouli.
 */
public final class GuidebookPins {
    public static final String SCOPE_SAME_MOD = "same_mod";
    public static final String SCOPE_ANY_MOD = "any_mod";

    public static final double QUEST_OVERLAP_DROP = 0.55;

    /** Item-miss title search minimum overlapping query tokens. */
    public static final int HIGH_TITLE_SCORE = 2;
    /** No-focus title search — stricter. */
    public static final int HIGH_NO_ITEM_SCORE = 3;
    /** Max related extras to merge before format cap. */
    public static final int MAX_RELATED_EXTRA = 2;

    private GuidebookPins() {}

    /** Patchouli JSON often stores lang keys — resolve on client before pin. */
    public static String resolveDisplayString(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim();
        if (!looksLikeTranslationKey(s)) {
            return s;
        }
        try {
            String tr = net.minecraft.client.resources.language.I18n.get(s);
            if (tr != null && !tr.isBlank() && !tr.equals(s)) {
                return tr.trim();
            }
        } catch (Throwable ignored) {
            // ponytail: no client lang yet — keep key
        }
        return s;
    }

    /** Resolve each line that looks like an i18n key (API / index pins). */
    public static String resolveGuideBody(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] lines = raw.split("\n", -1);
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(resolveDisplayString(lines[i]));
        }
        return sb.toString().trim();
    }

    static String pinHeaderResolved(GuidebookEntry e) {
        if (e == null) {
            return "";
        }
        String left = e.bookId().isEmpty() ? e.entryId() : e.bookId() + "/" + e.entryId();
        String title = resolveDisplayString(e.title()).trim();
        if (title.isBlank()) {
            return left;
        }
        return left + " | " + title;
    }

    private static boolean looksLikeTranslationKey(String s) {
        if (s == null || s.isBlank() || s.contains("\n") || s.contains(" ")) {
            return false;
        }
        if (!s.contains(".")) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_') {
                continue;
            }
            return false;
        }
        return true;
    }

    public static String normalizeScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return SCOPE_SAME_MOD;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (SCOPE_ANY_MOD.equals(s) || "any".equals(s) || "cross_mod".equals(s)) {
            return SCOPE_ANY_MOD;
        }
        return SCOPE_SAME_MOD;
    }

    public static String itemNamespace(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "";
        }
        String id = PatchouliEntryScan.normalizeItemKey(itemId);
        int colon = id.indexOf(':');
        return colon > 0 ? id.substring(0, colon) : "";
    }

    public static boolean passesScope(GuidebookEntry entry, String scope, String itemNs) {
        if (entry == null) {
            return false;
        }
        if (!SCOPE_SAME_MOD.equals(normalizeScope(scope))) {
            return true;
        }
        if (itemNs == null || itemNs.isBlank()) {
            return true;
        }
        return itemNs.trim().equalsIgnoreCase(entry.bookNs());
    }

    public static List<GuidebookEntry> filterScope(
            List<GuidebookEntry> entries, String scope, String itemNs
    ) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<GuidebookEntry> out = new ArrayList<>();
        for (GuidebookEntry e : entries) {
            if (passesScope(e, scope, itemNs)) {
                out.add(e);
            }
        }
        return out;
    }

    public static String formatPins(
            List<GuidebookEntry> entries, String itemId, int maxEntries, int maxChars
    ) {
        if (entries == null || entries.isEmpty() || maxEntries <= 0 || maxChars <= 0) {
            return "";
        }
        List<Scored> ranked = new ArrayList<>();
        for (GuidebookEntry e : entries) {
            if (e == null) {
                continue;
            }
            int score = scoreEntry(e, itemId);
            if (score <= 0) {
                score = 1;
            }
            ranked.add(new Scored(score, e));
        }
        ranked.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparing(s -> s.entry().stableKey()));
        List<String> bodies = new ArrayList<>();
        for (Scored s : ranked) {
            GuidebookEntry e = s.entry();
            String clip = resolveDisplayString(e.textClip()).trim();
            if (clip.length() > PatchouliEntryScan.MAX_TEXT_CLIP) {
                clip = clip.substring(0, PatchouliEntryScan.MAX_TEXT_CLIP);
            }
            String header = pinHeaderResolved(e);
            String body = clip.isEmpty() ? header : header + "\n" + clip;
            if (body.isBlank()) {
                continue;
            }
            bodies.add(body.trim());
        }
        return PatchouliEntryScan.joinCapped(bodies, maxEntries, maxChars);
    }

    public static String formatPins(List<GuidebookEntry> entries, String itemId) {
        return formatPins(
                entries,
                itemId,
                PatchouliEntryScan.DEFAULT_MAX_ENTRIES,
                PatchouliEntryScan.DEFAULT_MAX_CHARS);
    }

    public static String dedupeAgainstQuest(String guideBody, String questBlob) {
        if (guideBody == null || guideBody.isBlank()) {
            return "";
        }
        if (questBlob == null || questBlob.isBlank()) {
            return guideBody.trim();
        }
        Set<String> questTokens = tokens(questBlob);
        if (questTokens.isEmpty()) {
            return guideBody.trim();
        }
        String[] chunks = guideBody.trim().split("\n\n+");
        List<String> kept = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk == null || chunk.isBlank()) {
                continue;
            }
            if (overlapRatio(tokens(chunk), questTokens) >= QUEST_OVERLAP_DROP) {
                continue;
            }
            kept.add(chunk.trim());
        }
        return String.join("\n\n", kept).trim();
    }

    public static int scoreEntry(GuidebookEntry entry, String itemId) {
        if (entry == null || itemId == null || itemId.isBlank()) {
            return 0;
        }
        String want = PatchouliEntryScan.normalizeItemKey(itemId);
        if (want.isEmpty() || entry.linkedItems() == null) {
            return 0;
        }
        List<String> linked = entry.linkedItems();
        if (!linked.isEmpty() && want.equals(linked.get(0))) {
            return 3;
        }
        for (String id : linked) {
            if (want.equals(id)) {
                return 2;
            }
        }
        return 0;
    }

    /**
     * PURPOSE／手冊／guide keywords — required for no-focus title search.
     */
    public static boolean hasGuideIntent(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String q = question.toLowerCase(Locale.ROOT);
        return q.contains("guide")
                || q.contains("handbook")
                || q.contains("patchouli")
                || q.contains("lexica")
                || q.contains("manual")
                || q.contains("guidebook")
                || q.contains("手冊")
                || q.contains("手册")
                || q.contains("指南")
                || q.contains("圖鑑")
                || q.contains("图鉴")
                || q.contains("說明書")
                || q.contains("说明书")
                || q.contains("[purpose]")
                || q.contains("purpose");
    }

    /** Score = count of query tokens present in entry titleTokens (and title substring boost). */
    public static int titleMatchScore(GuidebookEntry entry, String query) {
        if (entry == null || query == null || query.isBlank()) {
            return 0;
        }
        List<String> qToks = PatchouliEntryScan.tokenizeTitle(query, "");
        if (qToks.isEmpty()) {
            return 0;
        }
        Set<String> entryToks = new HashSet<>();
        if (entry.titleTokens() != null) {
            entryToks.addAll(entry.titleTokens());
        }
        int hit = 0;
        for (String t : qToks) {
            if (entryToks.contains(t)) {
                hit++;
            }
        }
        String title = entry.title() == null ? "" : entry.title().toLowerCase(Locale.ROOT);
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.length() >= 4 && title.contains(q)) {
            hit += 2;
        }
        return hit;
    }

    /**
     * Rank entries by titleMatchScore ≥ minScore. Does not apply scope (caller does).
     */
    public static List<GuidebookEntry> rankByTitle(
            List<GuidebookEntry> candidates, String query, int minScore
    ) {
        if (candidates == null || candidates.isEmpty() || minScore <= 0) {
            return List.of();
        }
        List<Scored> ranked = new ArrayList<>();
        for (GuidebookEntry e : candidates) {
            int s = titleMatchScore(e, query);
            if (s >= minScore) {
                ranked.add(new Scored(s, e));
            }
        }
        ranked.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparing(x -> x.entry().stableKey()));
        List<GuidebookEntry> out = new ArrayList<>();
        for (Scored s : ranked) {
            out.add(s.entry());
        }
        return out;
    }

    /**
     * One-hop related: linksOut targets + same category (capped). Re-apply scope after.
     */
    public static List<GuidebookEntry> expandRelated(
            List<GuidebookEntry> seeds,
            Map<String, GuidebookEntry> byKey,
            Map<String, List<String>> categoryMap,
            String scope,
            String itemNs,
            int maxExtra
    ) {
        if (seeds == null || seeds.isEmpty() || byKey == null || byKey.isEmpty() || maxExtra <= 0) {
            return seeds == null ? List.of() : List.copyOf(seeds);
        }
        LinkedHashMap<String, GuidebookEntry> merged = new LinkedHashMap<>();
        for (GuidebookEntry e : seeds) {
            if (e != null) {
                merged.put(e.stableKey(), e);
            }
        }
        int added = 0;
        for (GuidebookEntry seed : seeds) {
            if (seed == null || added >= maxExtra) {
                break;
            }
            for (String to : seed.linksOut()) {
                if (added >= maxExtra) {
                    break;
                }
                GuidebookEntry t = byKey.get(to);
                if (t == null || merged.containsKey(t.stableKey())) {
                    continue;
                }
                if (!passesScope(t, scope, itemNs)) {
                    continue;
                }
                merged.put(t.stableKey(), t);
                added++;
            }
            if (added >= maxExtra) {
                break;
            }
            String cat = seed.categoryId() == null ? "" : seed.categoryId().trim().toLowerCase(Locale.ROOT);
            if (!cat.isEmpty() && categoryMap != null) {
                List<String> peers = categoryMap.get(cat);
                if (peers != null) {
                    for (String k : peers) {
                        if (added >= maxExtra) {
                            break;
                        }
                        GuidebookEntry t = byKey.get(k);
                        if (t == null || merged.containsKey(t.stableKey())) {
                            continue;
                        }
                        if (!passesScope(t, scope, itemNs)) {
                            continue;
                        }
                        merged.put(t.stableKey(), t);
                        added++;
                    }
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    public static GuidebookEntry apiFallbackEntry(
            String bookNs, String bookId, String title, String text, String itemId
    ) {
        List<String> linked = new ArrayList<>();
        String nid = PatchouliEntryScan.normalizeItemKey(itemId);
        if (!nid.isEmpty()) {
            linked.add(nid);
        }
        String clip = text == null ? "" : text.trim();
        if (clip.length() > PatchouliEntryScan.MAX_TEXT_CLIP) {
            clip = clip.substring(0, PatchouliEntryScan.MAX_TEXT_CLIP);
        }
        String t = title == null ? "" : title;
        return new GuidebookEntry(
                bookNs == null ? "" : bookNs,
                bookId == null ? "api" : bookId,
                "live",
                "",
                t,
                clip,
                linked,
                "patchouli:api",
                "",
                List.of(),
                List.of(),
                PatchouliEntryScan.tokenizeTitle(t, "live"));
    }

    public static boolean isSpotlightPage(JsonObject page) {
        if (page == null) {
            return false;
        }
        String type = "";
        if (page.has("type") && page.get("type").isJsonPrimitive()) {
            type = page.get("type").getAsString().toLowerCase(Locale.ROOT);
        }
        return type.equals("spotlight")
                || type.equals("patchouli:spotlight")
                || type.endsWith(":spotlight");
    }

    private static double overlapRatio(Set<String> guideTokens, Set<String> questTokens) {
        if (guideTokens.isEmpty()) {
            return 0;
        }
        int hit = 0;
        for (String t : guideTokens) {
            if (questTokens.contains(t)) {
                hit++;
            }
        }
        return (double) hit / (double) guideTokens.size();
    }

    private static Set<String> tokens(String raw) {
        Set<String> out = new HashSet<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        String norm = raw.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ");
        for (String t : norm.split("\\s+")) {
            if (t.length() >= 3) {
                out.add(t);
            }
        }
        return out;
    }

    private record Scored(int score, GuidebookEntry entry) {}
}

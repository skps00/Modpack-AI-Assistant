package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advisory chain DATA LAYER (S-A1): deterministic facts for
 * 需求 → 每 tick 數字 → 取得途徑 → band 排序. No engine wiring.
 *
 * Band order mirrors {@code PackIndex} {@code RankedAcquire}
 * ({@code PackIndex.java:1442} sort, {@code :1490-1491} definition):
 * fish → loot → interact → trade → script → quest(repeat) → quest(once).
 * 「最省料」排序 is NOT supported in S-A1.
 */
public final class AdvisoryChain {
    public static final int BAND_FISH = 0;
    public static final int BAND_LOOT = 1;
    public static final int BAND_INTERACT = 2;
    public static final int BAND_TRADE = 3;
    public static final int BAND_SCRIPT = 4;
    public static final int BAND_QUEST_REPEAT = 5;
    public static final int BAND_QUEST_ONCE = 6;
    public static final int BAND_QUEST_VARIANT = 7;

    private static final int MAX_LINES = 12;
    /**
     * Rate signal (D1 / 3b). 「每 tick」 only when key/unit has a rate word —
     * not bare {@code tick}/{@code ticks} (those are often durations).
     * Rate-ish nouns near tick, unit suffixes (/t, FE/t…), per-second, or
     * consumption nouns (fuel/burn/…); bare {@code rate} alone is NOT enough
     * (avoids {@code updaterate} intervals).
     */
    static final Pattern RATE_MARKER = Pattern.compile(
            "per\\s*tick|pertick|per_tick|"
                    + "(?:fuel|usage|consume|consumption|generat|emit|output|burn|power|energy|heat|charge|rate)"
                    + ".{0,4}ticks?|"
                    + "ticks?.{0,4}"
                    + "(?:fuel|usage|consume|consumption|generat|emit|output|burn|power|energy|heat|charge|rate)|"
                    + "/t\\b|mb/t|fe/t|rf/t|eu/t|j/t|"
                    + "per\\s*second|(?<![a-zA-Z])/s(?![a-zA-Z])|"
                    + "(?:fuel|usage|consum(?:e|ption)|generat|emit|output|burn|power|energy|heat|charge)"
                    + "[a-z_]*rate",
            Pattern.CASE_INSENSITIVE);
    /**
     * Duration / interval words — never label these as 「每 tick」 even if a
     * weak rate token appears nearby.
     */
    static final Pattern DURATION_MARKER = Pattern.compile(
            "lifetime|lifespan|duration|cooldown|timeout|interval|delay|despawn|sleep",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCLUDE_MEK = Pattern.compile(
            "唔用\\s*mekanism|不用\\s*mekanism|no\\s*mek(?:anism)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCLUDE_NC = Pattern.compile(
            "唔用\\s*nuclearcraft|不用\\s*nuclearcraft|no\\s*nuclearcraft", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCLUDE_MINE = Pattern.compile(
            "唔想挖礦|不想挖矿|no\\s*mining|不挖礦|不挖矿", Pattern.CASE_INSENSITIVE);
    private static final Pattern EDGE = Pattern.compile(
            "^item:([a-z0-9_]+:[a-z0-9_./-]+) -\\[([a-z_]+)\\]-> (.*)$",
            Pattern.CASE_INSENSITIVE);

    private AdvisoryChain() {}

    /** Runtime context for {@link #build}. */
    public record PackContext(Path gameDir, PackIndex index, PackConfigFacts configFacts) {
        public static PackContext of(Path gameDir, PackIndex index) {
            PackConfigFacts facts = new PackConfigFacts(gameDir);
            facts.load();
            return new PackContext(gameDir, index == null ? new PackIndex() : index, facts);
        }
    }

    /** Ranked acquire route with PackIndex-compatible band. */
    public record Route(int band, int seq, String line) {
        public Route {
            line = line == null ? "" : line;
        }
    }

    /**
     * Build ≤12 fact lines. Never invents numeric values.
     */
    public static List<String> build(String itemId, String question, PackContext ctx) {
        List<String> lines = new ArrayList<>();
        if (ctx == null) {
            lines.add("需求: 未收錄（無 PackContext）");
            lines.add("每 tick: 未收錄（已查: A1/A2/A3）");
            lines.add("取得途徑: 未收錄");
            lines.add("band 排序: 無途徑；省料排序未支援");
            return trim(lines);
        }
        String id = itemId == null ? "" : itemId.trim();
        String q = question == null ? "" : question.trim();
        PackConfigFacts facts = ctx.configFacts();
        if (facts == null) {
            facts = new PackConfigFacts(ctx.gameDir());
            facts.load();
        }
        PackIndex index = ctx.index() == null ? new PackIndex() : ctx.index();

        // ① 需求
        List<String> needs = collectNeeds(id, q, index, facts);
        if (needs.isEmpty()) {
            lines.add("需求: 未收錄（已查 recipe_needs／config key；item="
                    + (id.isEmpty() ? "?" : id) + "）");
        } else {
            lines.add("需求: " + String.join("；", needs));
        }

        // ② 每 tick 數字（rate markers only）＋ 其他數值（非每 tick）
        List<PackConfigFacts.NumberFact> nums = PackConfigFacts.isNonsenseQuery(id, q)
                ? List.of()
                : facts.lookup(id, q);
        List<PackConfigFacts.NumberFact> rates = new ArrayList<>();
        List<PackConfigFacts.NumberFact> others = new ArrayList<>();
        for (PackConfigFacts.NumberFact n : nums) {
            if (n.tier() == null || n.tier().isBlank() || n.source() == null || n.source().isBlank()) {
                continue; // never emit unattributed numbers
            }
            if (isRateFact(n)) {
                rates.add(n);
            } else {
                others.add(n);
            }
        }
        if (rates.isEmpty()) {
            lines.add("每 tick: 未收錄（已查: " + String.join(", ", facts.searchedSources()) + "）");
        } else {
            int shown = 0;
            for (PackConfigFacts.NumberFact n : rates) {
                lines.add(formatFactLine("每 tick", n));
                shown++;
                if (shown >= 3) {
                    break;
                }
            }
        }
        int otherShown = 0;
        for (PackConfigFacts.NumberFact n : others) {
            lines.add(formatFactLine("其他數值（非每 tick）", n));
            otherShown++;
            if (otherShown >= 3) {
                break;
            }
        }

        // ③ 取得途徑 + ④ band 排序
        List<Route> routes = collectRoutes(id, q, index, facts);
        List<String> excluded = new ArrayList<>();
        routes = applyPlayerFilters(routes, q, excluded);
        if (routes.isEmpty()) {
            lines.add("取得途徑: 未收錄（PackIndex RankedAcquire 無途徑）");
            lines.add("band 排序: 無途徑；省料排序未支援");
        } else {
            Route best = pickFastest(routes);
            lines.add("取得途徑: [band=" + best.band() + "] " + best.line());
            if (routes.size() >= 2) {
                Route second = routes.get(1);
                lines.add("取得途徑#2: [band=" + second.band() + "] " + second.line());
            }
            String tie = tieBreakNote(routes);
            if (tie != null) {
                lines.add("band 排序: " + tie + "；省料排序未支援");
            } else {
                lines.add("band 排序: 選 band=" + best.band() + "（RankedAcquire 最低 band）；省料排序未支援");
            }
        }
        if (!excluded.isEmpty()) {
            lines.add("已按你條件排除: " + String.join("、", excluded));
        }
        return trim(lines);
    }

    /** Pick lowest band; stable seq tie-break (mirrors PackIndex sort). */
    public static Route pickFastest(List<Route> routes) {
        if (routes == null || routes.isEmpty()) {
            return null;
        }
        return routes.stream()
                .min(Comparator.comparingInt(Route::band).thenComparingInt(Route::seq))
                .orElse(null);
    }

    static String tieBreakNote(List<Route> sorted) {
        if (sorted == null || sorted.size() < 2) {
            return null;
        }
        Route a = sorted.get(0);
        Route b = sorted.get(1);
        if (a.band() == b.band()) {
            return "tie-break: 同 band=" + a.band() + " → 較早 seq=" + a.seq() + " 勝出";
        }
        return null;
    }

    static List<Route> applyPlayerFilters(List<Route> routes, String question, List<String> excludedOut) {
        if (routes == null || routes.isEmpty()) {
            return List.of();
        }
        String q = question == null ? "" : question;
        boolean dropMek = EXCLUDE_MEK.matcher(q).find();
        boolean dropNc = EXCLUDE_NC.matcher(q).find();
        boolean dropMine = EXCLUDE_MINE.matcher(q).find();
        List<Route> out = new ArrayList<>();
        for (Route r : routes) {
            String low = r.line().toLowerCase(Locale.ROOT);
            if (dropMek && (low.contains("mekanism") || low.contains("mek:"))) {
                excludedOut.add("Mekanism 途徑");
                continue;
            }
            if (dropNc && low.contains("nuclearcraft")) {
                excludedOut.add("NuclearCraft 途徑");
                continue;
            }
            if (dropMine && (low.contains("挖礦") || low.contains("mining") || low.contains("quarry")
                    || low.contains("礦") || low.contains("ore"))) {
                excludedOut.add("挖礦途徑");
                continue;
            }
            out.add(r);
        }
        // de-dupe exclusion labels
        Set<String> uniq = new LinkedHashSet<>(excludedOut);
        excludedOut.clear();
        excludedOut.addAll(uniq);
        out.sort(Comparator.comparingInt(Route::band).thenComparingInt(Route::seq));
        return out;
    }

    static List<Route> collectRoutes(String itemId, String question, PackIndex index) {
        return collectRoutes(itemId, question, index, null);
    }

    static List<Route> collectRoutes(
            String itemId, String question, PackIndex index, PackConfigFacts facts
    ) {
        List<Route> routes = new ArrayList<>();
        if (itemId == null || itemId.isBlank()) {
            return routes;
        }
        String id = itemId.toLowerCase(Locale.ROOT).trim();
        String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "";
        List<String> focus = ns.isEmpty() ? List.of() : List.of(ns);
        int seq = 0;
        if (index != null) {
            PackIndex.RetrieveResult rr = index.retrieve(question == null ? "" : question, id, focus);
            if (rr != null && rr.graphFacts() != null) {
                for (String f : rr.graphFacts()) {
                    Route r = routeFromEdge(f, id, seq);
                    if (r != null) {
                        routes.add(r);
                        seq++;
                    }
                }
            }
            // Also ingest acquire-ranked human lines (already band-sorted by PackIndex).
            PackIndex.AcquireFacts af = index.acquireFactsDetailed(id, "zh_tw", List.of());
            if (af != null && af.lines() != null) {
                boolean header = true;
                for (String line : af.lines()) {
                    if (header) {
                        header = false;
                        continue;
                    }
                    int band = inferBandFromLabel(line);
                    routes.add(new Route(band, seq++, line));
                }
            }
        }
        // D2: jar datapack craft routes. Band = SCRIPT (4) — same ladder slot as
        // RankedAcquire recipe_needs / script (PackIndex.java:1386-1387 emit band 4;
        // :1490-1491 fish→loot→interact→trade→script→quest…).
        if (facts != null) {
            for (String src : facts.findCraftRecipeSources(id)) {
                routes.add(new Route(
                        BAND_SCRIPT,
                        seq++,
                        "配方（玩家合成／datapack）→ " + id + " source=" + src));
            }
        }
        // Prefer edges with explicit bands; drop duplicate blank.
        routes.sort(Comparator.comparingInt(Route::band).thenComparingInt(Route::seq));
        List<Route> dedup = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Route r : routes) {
            String sig = r.band() + "|" + r.line();
            if (seen.add(sig)) {
                dedup.add(r);
            }
        }
        return dedup;
    }

    /**
     * True when fact key/source is a per-tick/per-second rate (rate signal AND
     * not a duration/interval key).
     */
    static boolean isRateFact(PackConfigFacts.NumberFact n) {
        if (n == null) {
            return false;
        }
        String blob = n.key() + " " + n.source();
        if (DURATION_MARKER.matcher(blob).find()) {
            return false;
        }
        return RATE_MARKER.matcher(blob).find();
    }

    static String formatFactLine(String label, PackConfigFacts.NumberFact n) {
        String mark = "B".equals(n.tier()) ? "（文字描述數字）" : "";
        String note = (n.note() == null || n.note().isBlank()) ? "" : " note=" + n.note();
        return label + ": " + n.key() + "=" + n.valueRaw()
                + " tier=" + n.tier() + " source=" + n.source() + mark + note;
    }

    static Route routeFromEdge(String fact, String focusId, int seq) {
        if (fact == null) {
            return null;
        }
        Matcher m = EDGE.matcher(fact);
        if (!m.matches()) {
            return null;
        }
        String item = m.group(1).toLowerCase(Locale.ROOT);
        if (!item.equals(focusId)) {
            // also keep recipe_needs → resource routes for the focus machine's outputs later
            if (!(fact.contains("-[recipe_needs]->") && item.equals(focusId))) {
                // allow loot/fish/trade for focus only
                return null;
            }
        }
        String kind = m.group(2).toLowerCase(Locale.ROOT);
        String rest = m.group(3);
        int band = switch (kind) {
            case "fish" -> BAND_FISH;
            case "loot" -> BAND_LOOT;
            case "right_click", "right_click_use", "right_click_as_block", "js_produce" -> BAND_INTERACT;
            case "trade" -> BAND_TRADE;
            case "recipe_needs", "removed" -> BAND_SCRIPT;
            case "quest_submit", "quest_obtain" ->
                    PackIndex.hasQuestRepeatMark(rest) ? BAND_QUEST_REPEAT : BAND_QUEST_ONCE;
            default -> -1;
        };
        if (band < 0) {
            return null;
        }
        return new Route(band, seq, kind + " → " + rest);
    }

    static int inferBandFromLabel(String line) {
        if (line == null) {
            return BAND_SCRIPT;
        }
        String s = line.toLowerCase(Locale.ROOT);
        if (s.contains("釣魚") || s.contains("fishing")) {
            return BAND_FISH;
        }
        if (s.contains("掉落") || s.contains("loot") || s.contains("寶箱") || s.contains("gateway")) {
            return BAND_LOOT;
        }
        if (s.contains("右鍵") || s.contains("互動") || s.contains("interact") || s.contains("腳本產出")) {
            return BAND_INTERACT;
        }
        if (s.contains("交易") || s.contains("trade") || s.contains("村民")) {
            return BAND_TRADE;
        }
        if (s.contains("任務") || s.contains("quest")) {
            return s.contains("可重複") || s.contains("repeat") ? BAND_QUEST_REPEAT : BAND_QUEST_ONCE;
        }
        if (s.contains("腳本") || s.contains("配方") || s.contains("script")) {
            return BAND_SCRIPT;
        }
        return BAND_SCRIPT;
    }

    static List<String> collectNeeds(
            String itemId, String question, PackIndex index, PackConfigFacts facts
    ) {
        LinkedHashSet<String> needs = new LinkedHashSet<>();
        if (index != null && itemId != null && !itemId.isBlank()) {
            String id = itemId.toLowerCase(Locale.ROOT);
            String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "";
            PackIndex.RetrieveResult rr = index.retrieve(question == null ? "" : question, id,
                    ns.isEmpty() ? List.of() : List.of(ns));
            if (rr != null && rr.graphFacts() != null) {
                String pref = "item:" + id + " -[recipe_needs]-> item:";
                for (String f : rr.graphFacts()) {
                    if (f.startsWith(pref)) {
                        needs.add(f.substring(pref.length()));
                    }
                }
            }
        }
        if (facts != null) {
            for (PackConfigFacts.NumberFact n : facts.lookup(itemId, question)) {
                String k = n.key().toLowerCase(Locale.ROOT);
                if (k.contains("fuel") || k.contains("coolant") || k.contains("burn")
                        || k.contains("energy") || k.contains("power") || k.contains("consum")) {
                    needs.add(n.key() + "（config）");
                }
                if (needs.size() >= 4) {
                    break;
                }
            }
        }
        // Domain hints (labels only — not invented numbers).
        String blob = ((itemId == null ? "" : itemId) + " " + (question == null ? "" : question))
                .toLowerCase(Locale.ROOT);
        if (blob.contains("fission") || blob.contains("反應爐") || blob.contains("核")) {
            needs.add("燃料／冷卻（機器運行）");
        }
        if (blob.contains("quarry") || blob.contains("挖礦") || blob.contains("builder")) {
            needs.add("能量（FE/RF）");
        }
        return new ArrayList<>(needs);
    }

    private static List<String> trim(List<String> lines) {
        if (lines.size() <= MAX_LINES) {
            return List.copyOf(lines);
        }
        return List.copyOf(lines.subList(0, MAX_LINES));
    }
}

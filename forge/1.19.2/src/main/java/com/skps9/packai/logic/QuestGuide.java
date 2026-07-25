package com.skps9.packai.logic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** FTB Quests / Heracles file matching and guide text. */
public final class QuestGuide {
    public static final int MAX_HITS = 3;
    private static final Pattern OVERRIDE = Pattern.compile(
            "(任務書?\\s*(好像)?(不對|錯了|有誤|過時)|quest\\s*wrong|quest\\s*outdated|wrong\\s*quest)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TITLE = Pattern.compile("(?:title|Title)\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DESC = Pattern.compile("(?:description|Description)\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ITEM = Pattern.compile("\\b([a-z0-9_]+:[a-z0-9_./-]+)\\b", Pattern.CASE_INSENSITIVE);
    /** Modern FTB lang: {@code quest.<HEX>.title: "..."}. */
    private static final Pattern LANG_QUEST_TITLE = Pattern.compile(
            "quest\\.([0-9A-Fa-f]+)\\.title\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern LANG_QUEST_DESC = Pattern.compile(
            "quest\\.([0-9A-Fa-f]+)\\.quest_desc\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern QUESTS_ARRAY = Pattern.compile("\\bquests\\s*:\\s*\\[");
    private static final Pattern FTB_CODES = Pattern.compile("[&§][0-9a-fk-or]", Pattern.CASE_INSENSITIVE);

    /**
     * @param questId FTB/Heracles id for open_book (may be blank)
     * @param system  ftbquests | heracles
     */
    public record Hit(
            String chapter,
            String title,
            String description,
            String source,
            List<String> items,
            int score,
            boolean active,
            String questId,
            String system
    ) {
        Hit withScore(int s) {
            return new Hit(chapter, title, description, source, items, s, active, questId, system);
        }
    }

    private QuestGuide() {}

    public static boolean isOverride(String question, boolean flag) {
        if (flag) {
            return true;
        }
        return question != null && OVERRIDE.matcher(question).find();
    }

    public static List<Hit> indexAndMatch(Path gameDir, List<String> scanners, String question, String heldItemId) {
        return match(index(gameDir, scanners), question, heldItemId);
    }

    public static List<Hit> index(Path gameDir, List<String> scanners) {
        return index(gameDir, scanners, null);
    }

    /**
     * @param preferredLang Minecraft language code (e.g. {@code zh_tw}); null → {@code en_us}
     */
    public static List<Hit> index(Path gameDir, List<String> scanners, String preferredLang) {
        String pref = normalizeLang(preferredLang);
        Map<String, Hit> byId = new LinkedHashMap<>();
        List<Hit> noId = new ArrayList<>();
        if (gameDir == null || !Files.isDirectory(gameDir)) {
            return List.of();
        }
        List<Path> roots = new ArrayList<>();
        if (scanners.contains("ftbquests")) {
            roots.add(gameDir.resolve("config/ftbquests"));
        }
        if (scanners.contains("heracles")) {
            roots.add(gameDir.resolve("config/heracles"));
        }
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!(name.endsWith(".snbt") || name.endsWith(".json") || name.endsWith(".txt"))) {
                        return;
                    }
                    String pathLower = p.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                    if (isSkippedQuestPath(pathLower, name)) {
                        return;
                    }
                    // Skip FTB lang packs that are neither preferred nor English fallback
                    // (otherwise es_* often wins via longer title strings).
                    if (!keepLangFile(pathLower, pref)) {
                        return;
                    }
                    try {
                        if (Files.size(p) > 500_000) {
                            return;
                        }
                        String text = Files.readString(p, StandardCharsets.UTF_8);
                        for (Hit h : parseFile(gameDir, p, text)) {
                            String qid = h.questId() == null ? "" : h.questId().trim();
                            if (qid.isEmpty()) {
                                noId.add(h);
                            } else {
                                byId.merge(qid.toUpperCase(Locale.ROOT), h, (a, b) -> mergeHits(a, b, pref));
                            }
                        }
                    } catch (IOException ignored) {
                        // skip
                    }
                });
            } catch (IOException ignored) {
                // skip
            }
        }
        List<Hit> hits = new ArrayList<>(byId.values());
        hits.addAll(noId);
        return hits;
    }

    /** FTB Quests reward_tables / book meta — ignore for guide & open buttons. */
    static boolean isRewardTablePath(String pathLower, String fileNameLower) {
        return isSkippedQuestPath(pathLower, fileNameLower);
    }

    static boolean isSkippedQuestPath(String pathLower, String fileNameLower) {
        return pathLower.contains("/reward_tables/")
                || pathLower.contains("/reward_table/")
                || pathLower.contains("\\reward_tables\\")
                || pathLower.contains("\\reward_table\\")
                || fileNameLower.contains("reward_table")
                || fileNameLower.equals("data.snbt")
                || fileNameLower.equals("chapter_groups.snbt")
                || fileNameLower.equals("chapter_group.snbt")
                || fileNameLower.equals("chapter.snbt")
                || fileNameLower.equals("reward_table.snbt");
    }

    static Hit mergeHits(Hit a, Hit b) {
        return mergeHits(a, b, "en_us");
    }

    static Hit mergeHits(Hit a, Hit b, String preferredLang) {
        String pref = normalizeLang(preferredLang);
        String title;
        int sa = titleLocaleScore(a.source(), pref);
        int sb = titleLocaleScore(b.source(), pref);
        if (sa > sb && a.title() != null && !a.title().isBlank()) {
            title = a.title();
        } else if (sb > sa && b.title() != null && !b.title().isBlank()) {
            title = b.title();
        } else {
            title = betterTitle(a.title(), b.title());
        }
        String desc = longerText(a.description(), b.description());
        if (sa > sb && a.description() != null && !a.description().isBlank()) {
            desc = a.description();
        } else if (sb > sa && b.description() != null && !b.description().isBlank()) {
            desc = b.description();
        }
        String chapter = longerText(a.chapter(), b.chapter());
        String source = sa >= sb ? a.source() : b.source();
        if (source == null || source.isBlank()) {
            source = b.source();
        }
        LinkedHashSet<String> items = new LinkedHashSet<>();
        if (a.items() != null) {
            items.addAll(a.items());
        }
        if (b.items() != null) {
            items.addAll(b.items());
        }
        String system = a.system() == null || a.system().isBlank() ? b.system() : a.system();
        String id = a.questId() == null || a.questId().isBlank() ? b.questId() : a.questId();
        return new Hit(chapter, title, desc, source, new ArrayList<>(items), 0, false, id, system);
    }

    static String normalizeLang(String code) {
        if (code == null || code.isBlank()) {
            return "en_us";
        }
        return code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    /** e.g. {@code .../lang/es_es/chapters/x.snbt} → {@code es_es}. */
    static String langCodeFromPath(String pathLower) {
        if (pathLower == null) {
            return "";
        }
        String s = pathLower.replace('\\', '/').toLowerCase(Locale.ROOT);
        int i = s.indexOf("/lang/");
        if (i < 0) {
            return "";
        }
        String rest = s.substring(i + 6);
        int slash = rest.indexOf('/');
        String seg = slash < 0 ? rest : rest.substring(0, slash);
        int dot = seg.indexOf('.');
        if (dot > 0) {
            seg = seg.substring(0, dot);
        }
        return seg.replace('-', '_');
    }

    static String langFamily(String code) {
        String c = normalizeLang(code);
        int u = c.indexOf('_');
        return u < 0 ? c : c.substring(0, u);
    }

    /**
     * Keep preferred language family + English fallback; drop other FTB lang packs
     * so Spanish/French never win on title length.
     */
    static boolean keepLangFile(String pathLower, String preferredLang) {
        String loc = langCodeFromPath(pathLower);
        if (loc.isEmpty()) {
            return true;
        }
        String pref = normalizeLang(preferredLang);
        if (loc.equals(pref) || langFamily(loc).equals(langFamily(pref))) {
            return true;
        }
        return loc.startsWith("en");
    }

    /** Prefer client language, then same family, then English, over other FTB lang packs. */
    static int titleLocaleScore(String source, String preferredLang) {
        if (source == null) {
            return 0;
        }
        String s = source.replace('\\', '/').toLowerCase(Locale.ROOT);
        String loc = langCodeFromPath(s);
        if (loc.isEmpty()) {
            return 5; // chapter / raw SNBT
        }
        String pref = normalizeLang(preferredLang);
        if (loc.equals(pref)) {
            return 100;
        }
        if (langFamily(loc).equals(langFamily(pref))) {
            return 80;
        }
        if (loc.startsWith("en")) {
            return 40;
        }
        return 10;
    }

    private static String betterTitle(String a, String b) {
        if (isBadDisplayTitle(a)) {
            return isBadDisplayTitle(b) ? "" : b;
        }
        if (isBadDisplayTitle(b)) {
            return a;
        }
        boolean aKey = a.startsWith("{") && a.contains("}");
        boolean bKey = b.startsWith("{") && b.contains("}");
        if (aKey && !bKey) {
            return b;
        }
        if (bKey && !aKey) {
            return a;
        }
        // Same score: prefer longer readable title (same locale / chapter merge).
        return a.length() >= b.length() ? a : b;
    }

    /** Player-facing quest name — never a hex quest id. */
    public static String displayTitle(Hit h) {
        return displayTitle(h, ReplyLang.current());
    }

    public static String displayTitle(Hit h, String replyLang) {
        if (h == null) {
            return ReplyLang.unnamedQuest(replyLang);
        }
        String t = refinePlayerText(h.title());
        if (!t.isBlank() && !looksLikeQuestId(t)) {
            return t;
        }
        if (h.items() != null && !h.items().isEmpty()) {
            return ReplyLang.relatedQuest(Plainify.displayName(h.items().get(0)), replyLang);
        }
        String ch = refinePlayerText(h.chapter());
        if (!ch.isBlank() && !looksLikeQuestId(ch)) {
            return ReplyLang.chapterQuest(ch, replyLang);
        }
        return ReplyLang.unnamedQuest(replyLang);
    }

    public static String displayChapter(Hit h) {
        return displayChapter(h, ReplyLang.current());
    }

    public static String displayChapter(Hit h, String replyLang) {
        if (h == null) {
            return ReplyLang.unnamedChapter(replyLang);
        }
        String ch = refinePlayerText(h.chapter());
        if (!ch.isBlank() && !looksLikeQuestId(ch)) {
            return ch;
        }
        return ReplyLang.unnamedChapter(replyLang);
    }

    public static String refinePlayerText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String t = cleanTitle(raw.trim());
        if (t.startsWith("{") && t.endsWith("}") && t.length() > 2) {
            String inner = t.substring(1, t.length() - 1);
            int dot = inner.lastIndexOf('.');
            String leaf = dot >= 0 && dot < inner.length() - 1 ? inner.substring(dot + 1) : inner;
            t = leaf.replace('_', ' ').trim();
        }
        t = Plainify.humanizeText(t).trim();
        if (looksLikeQuestId(t)) {
            return "";
        }
        return t;
    }

    public static boolean looksLikeQuestId(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        String t = s.trim();
        return t.matches("(?i)^[0-9A-F]{11,16}$");
    }

    static boolean isBadDisplayTitle(String s) {
        if (s == null || s.isBlank()) {
            return true;
        }
        return looksLikeQuestId(s.trim());
    }

    private static String longerText(String a, String b) {
        if (a == null || a.isBlank()) {
            return b == null ? "" : b;
        }
        if (b == null || b.isBlank()) {
            return a;
        }
        return a.length() >= b.length() ? a : b;
    }

    public static List<Hit> match(List<Hit> all, String question, String heldItemId) {
        return matchResult(all, question, heldItemId, List.of()).hits();
    }

    public record MatchResult(List<Hit> hits, int totalMatched) {}

    public static MatchResult matchResult(
            List<Hit> all,
            String question,
            String heldItemId,
            List<String> extraItemIds
    ) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        String held = heldItemId == null ? "" : heldItemId.toLowerCase(Locale.ROOT);
        List<String> extras = extraItemIds == null ? List.of() : extraItemIds;
        List<Hit> scored = new ArrayList<>();
        for (Hit h : all) {
            int score = 0;
            String blob = (h.chapter + " " + h.title + " " + h.description).toLowerCase(Locale.ROOT);
            if (!held.isEmpty() && h.items.stream().anyMatch(i -> i.equalsIgnoreCase(held))) {
                score += 10;
            }
            if (!held.isEmpty() && blob.contains(held)) {
                score += 6;
            }
            for (String extra : extras) {
                if (extra == null || extra.isBlank()) {
                    continue;
                }
                String el = extra.toLowerCase(Locale.ROOT);
                if (h.items.stream().anyMatch(i -> i.equalsIgnoreCase(el))) {
                    score += 8;
                } else if (blob.contains(el) || blob.contains(el.replace(':', '_'))) {
                    score += 3;
                }
            }
            for (String tok : q.split("[^a-z0-9_\\u4e00-\\u9fff]+")) {
                if (!isUsefulQuestToken(tok)) {
                    continue;
                }
                if (blob.contains(tok)) {
                    score += 2;
                }
            }
            // Weak name-only hits (e.g. "gold" / "apple") create junk side-quest buttons.
            if (score >= 8) {
                scored.add(h.withScore(score));
            }
        }
        scored.sort(Comparator.comparingInt(Hit::score).reversed().thenComparing(Hit::title));
        int total = scored.size();
        List<Hit> top = total > MAX_HITS ? new ArrayList<>(scored.subList(0, MAX_HITS)) : scored;
        return new MatchResult(top, total);
    }

    private static boolean isUsefulQuestToken(String tok) {
        if (tok == null || tok.length() < 3) {
            return false;
        }
        // Skip common English filler that appears in item questions / tooltips.
        return switch (tok) {
            case "the", "and", "for", "with", "from", "this", "that", "item", "block",
                 "minecraft", "mod", "pack", "how", "what", "use", "used", "recipe",
                 "recipes", "obtain", "craft", "golden", "enchanted" -> false;
            default -> true;
        };
    }

    public static boolean conflict(List<Hit> hits, Set<String> removedItems) {
        if (removedItems == null || removedItems.isEmpty()) {
            return false;
        }
        for (Hit h : hits) {
            for (String item : h.items) {
                if (removedItems.contains(item.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String formatGuide(List<Hit> hits, boolean conflict, String localPlain, int totalHint) {
        return formatGuide(hits, conflict, localPlain, totalHint, false, ReplyLang.current());
    }

    /**
     * @param rich fuller plain-language description (no ids / paths)
     */
    public static String formatGuide(List<Hit> hits, boolean conflict, String localPlain, int totalHint, boolean rich) {
        return formatGuide(hits, conflict, localPlain, totalHint, rich, ReplyLang.current());
    }

    /**
     * @param rich fuller plain-language description (no ids / paths)
     */
    public static String formatGuide(
            List<Hit> hits,
            boolean conflict,
            String localPlain,
            int totalHint,
            boolean rich,
            String replyLang
    ) {
        String lang = replyLang == null || replyLang.isBlank() ? "zh_tw" : replyLang.trim();
        StringBuilder sb = new StringBuilder();
        sb.append(ReplyLang.guideHeader(lang, rich));
        int i = 1;
        int descCap = rich ? 400 : 120;
        for (Hit h : hits) {
            String chapter = displayChapter(h);
            String title = displayTitle(h);
            sb.append(ReplyLang.guideChapterQuest(lang, i++, chapter, title));
            if (h.description != null && !h.description.isBlank()) {
                String d = refinePlayerText(h.description);
                if (d.length() > descCap) {
                    d = d.substring(0, descCap) + "…";
                }
                if (!d.isBlank() && !looksLikeQuestId(d)) {
                    sb.append(ReplyLang.guideDesc(lang, d));
                }
            } else if (rich) {
                sb.append(ReplyLang.guideDescFallback(lang));
            }
            if (rich && h.items != null && !h.items.isEmpty()) {
                int n = Math.min(6, h.items.size());
                sb.append(ReplyLang.guideNeeds(lang));
                for (int j = 0; j < n; j++) {
                    if (j > 0) {
                        sb.append(ReplyLang.sourceJoin(lang));
                    }
                    sb.append(ReplyLang.quote(lang, Plainify.displayName(h.items.get(j))));
                }
                if (h.items.size() > n) {
                    sb.append(ReplyLang.guideEtc(lang));
                }
                sb.append('\n');
            }
        }
        if (totalHint > hits.size()) {
            sb.append(ReplyLang.guideMore(lang));
        }
        if (!rich) {
            sb.append(ReplyLang.guideStuckHint(lang));
        }
        sb.append(ReplyLang.sourceHeader(lang))
                .append(ReplyLang.labelQuestBook(lang));
        if (conflict) {
            sb.append(ReplyLang.guideConflict(lang));
            if (localPlain != null) {
                sb.append(localPlain);
            }
        }
        return sb.toString();
    }

    public static List<Hit> matchForOffline(List<Hit> all, String question, String heldItemId) {
        return matchForOfflineResult(all, question, heldItemId, List.of()).hits();
    }

    public static MatchResult matchForOfflineResult(
            List<Hit> all,
            String question,
            String heldItemId,
            List<String> extraItemIds
    ) {
        MatchResult scored = matchResult(all, question, heldItemId, extraItemIds);
        if (!scored.hits().isEmpty()) {
            return scored;
        }
        List<String> bag = new ArrayList<>();
        if (heldItemId != null && !heldItemId.isBlank()) {
            bag.add(heldItemId);
        }
        if (extraItemIds != null) {
            bag.addAll(extraItemIds);
        }
        if (!bag.isEmpty()) {
            List<Hit> byHeld = new ArrayList<>();
            for (Hit h : all) {
                int score = 0;
                String blob = (h.chapter + " " + h.title + " " + h.description).toLowerCase(Locale.ROOT);
                for (String item : bag) {
                    if (item == null || item.isBlank()) {
                        continue;
                    }
                    String el = item.toLowerCase(Locale.ROOT);
                    boolean itemHit = h.items.stream().anyMatch(i -> i.equalsIgnoreCase(el));
                    if (itemHit) {
                        score += 5;
                    } else if (blob.contains(el) || blob.contains(el.replace(':', '_'))) {
                        score += 2;
                    }
                }
                if (score > 0) {
                    byHeld.add(h.withScore(score));
                }
            }
            byHeld.sort(Comparator.comparingInt(Hit::score).reversed());
            int total = byHeld.size();
            List<Hit> top = total > MAX_HITS ? new ArrayList<>(byHeld.subList(0, MAX_HITS)) : byHeld;
            if (!top.isEmpty()) {
                return new MatchResult(top, total);
            }
        }
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        boolean questAsk = q.contains("任務") || q.contains("quest") || q.contains("下一步");
        if (questAsk && !all.isEmpty()) {
            int n = Math.min(MAX_HITS, all.size());
            List<Hit> sample = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                sample.add(all.get(i).withScore(1));
            }
            return new MatchResult(sample, all.size());
        }
        return new MatchResult(List.of(), 0);
    }

    static List<Hit> parseFile(Path gameDir, Path file, String text) {
        List<Hit> out = new ArrayList<>();
        String rel;
        try {
            rel = gameDir.relativize(file).toString().replace('\\', '/');
        } catch (Exception e) {
            rel = file.getFileName().toString();
        }
        String pathLower = rel.toLowerCase(Locale.ROOT);
        String system = pathLower.contains("heracles") ? "heracles" : "ftbquests";
        String chapter = file.getParent() == null ? "" : file.getParent().getFileName().toString();
        if ("chapters".equalsIgnoreCase(chapter)) {
            chapter = file.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        }

        if (pathLower.contains("/lang/") || pathLower.contains("\\lang\\")) {
            return parseLangQuests(chapter, rel, text, system);
        }

        List<Hit> fromQuests = parseQuestsArray(chapter, rel, text, system);
        if (!fromQuests.isEmpty()) {
            return fromQuests;
        }

        // Heracles / odd single-quest files: one title + one id if clearly paired
        return parseLooseFallback(chapter, rel, text, system);
    }

    private static List<Hit> parseLangQuests(String chapter, String rel, String text, String system) {
        Map<String, String> titles = new LinkedHashMap<>();
        Map<String, String> descs = new LinkedHashMap<>();
        Matcher tm = LANG_QUEST_TITLE.matcher(text);
        while (tm.find()) {
            titles.put(tm.group(1).toUpperCase(Locale.ROOT), cleanTitle(tm.group(2)));
        }
        Matcher dm = LANG_QUEST_DESC.matcher(text);
        while (dm.find()) {
            descs.put(dm.group(1).toUpperCase(Locale.ROOT), cleanTitle(dm.group(2)));
        }
        List<Hit> out = new ArrayList<>();
        for (Map.Entry<String, String> e : titles.entrySet()) {
            String id = e.getKey();
            out.add(new Hit(chapter, e.getValue(), descs.getOrDefault(id, ""),
                    rel, List.of(), 0, false, id, system));
        }
        return out;
    }

    private static List<Hit> parseQuestsArray(String chapter, String rel, String text, String system) {
        Matcher am = QUESTS_ARRAY.matcher(text);
        if (!am.find()) {
            return List.of();
        }
        int bracket = am.end() - 1; // '['
        List<int[]> objects = topLevelObjects(text, bracket);
        List<Hit> out = new ArrayList<>();
        for (int[] span : objects) {
            String slice = text.substring(span[0], span[1]);
            String id = depth1Field(slice, "id");
            if (id.isEmpty() || id.contains(":")) {
                // skip malformed / item-shaped objects
                continue;
            }
            String title = cleanTitle(depth1Field(slice, "title"));
            // Never fall back to hex quest id for display — resolve via displayTitle() later
            String desc = cleanTitle(depth1Field(slice, "subtitle"));
            if (desc.isEmpty()) {
                desc = firstDescriptionLine(slice);
            }
            List<String> items = new ArrayList<>(itemsInRange(slice, 0, slice.length()));
            out.add(new Hit(chapter, title, desc, rel, items, 0, false, id.toUpperCase(Locale.ROOT), system));
        }
        return out;
    }

    private static List<Hit> parseLooseFallback(String chapter, String rel, String text, String system) {
        // Avoid inventing open_book targets from nested reward/task ids.
        if (!rel.toLowerCase(Locale.ROOT).contains("heracles")) {
            return List.of();
        }
        Matcher tm = TITLE.matcher(text);
        if (!tm.find()) {
            return List.of();
        }
        String title = cleanTitle(tm.group(1));
        String id = depth1Field(text, "id");
        if (id.isEmpty()) {
            id = fileStemFromRel(rel);
        }
        List<String> items = new ArrayList<>(itemsInRange(text, 0, text.length()));
        String desc = "";
        Matcher dm = DESC.matcher(text);
        if (dm.find()) {
            desc = cleanTitle(dm.group(1));
        }
        return List.of(new Hit(chapter, title, desc, rel, items, 0, false, id, system));
    }

    private static String fileStemFromRel(String rel) {
        int slash = Math.max(rel.lastIndexOf('/'), rel.lastIndexOf('\\'));
        String name = slash >= 0 ? rel.substring(slash + 1) : rel;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Objects directly inside a SNBT/JSON array starting at {@code openBracket} ('['). */
    static List<int[]> topLevelObjects(String text, int openBracket) {
        List<int[]> out = new ArrayList<>();
        int depth = 0;
        int objStart = -1;
        boolean inString = false;
        boolean escape = false;
        for (int i = openBracket + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                if (depth == 0) {
                    objStart = i;
                }
                depth++;
            } else if (c == '}') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && objStart >= 0) {
                        out.add(new int[]{objStart, i + 1});
                        objStart = -1;
                    }
                }
            } else if (c == ']' && depth == 0) {
                break;
            }
        }
        return out;
    }

    /** Read {@code key: "value"} only at brace-depth 1 inside {@code objectSlice}. */
    static String depth1Field(String objectSlice, String key) {
        Pattern p = Pattern.compile("\\b" + Pattern.quote(key) + "\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(objectSlice);
        while (m.find()) {
            if (braceDepthAt(objectSlice, m.start()) == 1) {
                return m.group(1);
            }
        }
        return "";
    }

    /** Brace depth at {@code index} (0 = outside root object). */
    static int braceDepthAt(String text, int index) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        int limit = Math.min(Math.max(0, index), text.length());
        for (int i = 0; i < limit; i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
        }
        return depth;
    }

    private static String firstDescriptionLine(String slice) {
        Matcher m = Pattern.compile("description\\s*:\\s*\\[\\s*\"([^\"]+)\"").matcher(slice);
        if (m.find()) {
            return cleanTitle(m.group(1));
        }
        return "";
    }

    private static String cleanTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        return FTB_CODES.matcher(title).replaceAll("").trim();
    }

    private static LinkedHashSet<String> itemsInRange(String text, int start, int end) {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        int a = Math.max(0, start);
        int b = Math.min(text.length(), Math.max(a, end));
        Matcher im = ITEM.matcher(text.substring(a, b));
        while (im.find()) {
            items.add(im.group(1).toLowerCase(Locale.ROOT));
        }
        return items;
    }

    private static String empty(String s, String fb) {
        return s == null || s.isBlank() ? fb : s;
    }
}

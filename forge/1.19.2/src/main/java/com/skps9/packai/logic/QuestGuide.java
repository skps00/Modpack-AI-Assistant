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

import com.skps9.packai.config.PackAiConfig;

/** FTB Quests / Heracles file matching and guide text. */
public final class QuestGuide {
    public static final int MAX_HITS = 3;
    private static final Pattern OVERRIDE = Pattern.compile(
            "(任務書?\\s*(好像)?(不對|錯了|有誤|過時)|quest\\s*wrong|quest\\s*outdated|wrong\\s*quest)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TITLE = Pattern.compile("(?:title|Title)\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ITEM = Pattern.compile("\\b([a-z0-9_]+:[a-z0-9_./-]+)\\b", Pattern.CASE_INSENSITIVE);
    /** Modern FTB lang: {@code quest.<HEX>.title: "..."}. */
    private static final Pattern LANG_QUEST_TITLE = Pattern.compile(
            "quest\\.([0-9A-Fa-f]+)\\.title\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern LANG_QUEST_DESC = Pattern.compile(
            "quest\\.([0-9A-Fa-f]+)\\.quest_desc\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern QUESTS_ARRAY = Pattern.compile("\\bquests\\s*:\\s*\\[");
    private static final Pattern FTB_CODES = Pattern.compile("[&§][0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    /**
     * Quest-level flags that hide the quest from the player book / spoil Pack AI (NFWC + classic FTB).
     * Note: chapter uses {@code hide_quest_details_until_startable} (different key) — see
     * {@link #shouldSuppressQuestAdvertise}.
     */
    private static final String[] SPOILER_BOOL_KEYS = {
            "hide",
            "invisible",
            "secret",
            "hide_until_deps_visible",
            "hide_until_deps_complete",
            "hide_quest_until_deps_visible",
            "hide_details_until_startable",
            "hidden" // Heracles
    };

    /**
     * @param questId FTB/Heracles id for open_book (may be blank)
     * @param system  ftbquests | heracles
     * @param canRepeat FTB {@code can_repeat: true}; absent/false = one-shot (lower Ask priority)
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
            String system,
            boolean canRepeat
    ) {
        Hit withScore(int s) {
            return new Hit(chapter, title, description, source, items, s, active, questId, system, canRepeat);
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
        return index(gameDir, scanners, preferredLang, !showHiddenQuestsConfig());
    }

    /**
     * @param filterHidden when true, skip FTB/Heracles quests the book hides (anti-spoiler)
     */
    public static List<Hit> index(
            Path gameDir, List<String> scanners, String preferredLang, boolean filterHidden
    ) {
        String pref = normalizeLang(preferredLang);
        Map<String, Hit> byId = new LinkedHashMap<>();
        List<Hit> noId = new ArrayList<>();
        Set<String> spoilerIds = new LinkedHashSet<>();
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
                        for (Hit h : parseFile(gameDir, p, text, spoilerIds, filterHidden)) {
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
        if (filterHidden && !spoilerIds.isEmpty()) {
            byId.keySet().removeIf(spoilerIds::contains);
            noId.removeIf(h -> {
                String id = h.questId() == null ? "" : h.questId().trim().toUpperCase(Locale.ROOT);
                return !id.isEmpty() && spoilerIds.contains(id);
            });
        }
        List<Hit> hits = new ArrayList<>(byId.values());
        hits.addAll(noId);
        return hits;
    }

    /** Config default false = do not surface hidden quests. Safe if config not loaded yet. */
    public static boolean showHiddenQuestsConfig() {
        try {
            return PackAiConfig.showHiddenQuests();
        } catch (Throwable t) {
            return false;
        }
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
        boolean canRepeat = a.canRepeat() || b.canRepeat();
        return new Hit(chapter, title, desc, source, new ArrayList<>(items), 0, false, id, system, canRepeat);
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
            String label = Plainify.displayName(h.items().get(0));
            if (!looksLikeRegistryPathLabel(label, h.items().get(0))) {
                return ReplyLang.relatedQuest(label, replyLang);
            }
        }
        String ch = refinePlayerText(h.chapter());
        if (!ch.isBlank() && !looksLikeQuestId(ch)) {
            return ReplyLang.chapterQuest(ch, replyLang);
        }
        return ReplyLang.unnamedQuest(replyLang);
    }

    /** True when label is just humanized registry path (e.g. scroll_rolled → "Scroll Rolled"). */
    static boolean looksLikeRegistryPathLabel(String label, String itemId) {
        if (label == null || label.isBlank() || itemId == null || itemId.isBlank()) {
            return false;
        }
        String path = itemId.trim().toLowerCase(Locale.ROOT);
        int brace = path.indexOf('{');
        if (brace > 0) {
            path = path.substring(0, brace);
        }
        int colon = path.indexOf(':');
        if (colon >= 0) {
            path = path.substring(colon + 1);
        }
        String human = path.replace('_', ' ').replace('/', ' ').trim();
        String lab = label.trim().toLowerCase(Locale.ROOT);
        return lab.equals(human) || lab.equals(path) || lab.equals(itemId.trim().toLowerCase(Locale.ROOT));
    }

    /** True when hit has a player-facing title (not blank / hex id). */
    static boolean hasReadableTitle(Hit h) {
        if (h == null) {
            return false;
        }
        String t = refinePlayerText(h.title());
        return !t.isBlank() && !looksLikeQuestId(t);
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
        if (t.matches("(?i)^[0-9A-F]{11,16}$")) {
            return true;
        }
        // File-id style titles (e.g. goldenagetetra): long lowercase alnum blob, no spaces.
        if (t.length() >= 10
                && t.equals(t.toLowerCase(Locale.ROOT))
                && !t.contains(" ")
                && t.matches("^[a-z][a-z0-9_]{9,}$")) {
            return true;
        }
        return false;
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
        return matchResult(all, question, heldItemId, extraItemIds, List.of());
    }

    /**
     * @param variantTokens schematic / distinctive-name tokens — soft-prefer hits that mention them
     *                      when several quests share the same bare registry id
     */
    public static MatchResult matchResult(
            List<Hit> all,
            String question,
            String heldItemId,
            List<String> extraItemIds,
            List<String> variantTokens
    ) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        String held = heldItemId == null ? "" : heldItemId.toLowerCase(Locale.ROOT);
        List<String> extras = extraItemIds == null ? List.of() : extraItemIds;
        List<Hit> scored = new ArrayList<>();
        for (Hit h : all) {
            int heldScore = 0;
            int extraScore = 0;
            int tokenScore = 0;
            String blob = (h.chapter + " " + h.title + " " + h.description).toLowerCase(Locale.ROOT);
            if (!held.isEmpty() && h.items.stream().anyMatch(i -> i.equalsIgnoreCase(held))) {
                heldScore += 10;
            }
            if (!held.isEmpty() && (blob.contains(held) || blob.contains(held.replace(':', '_')))) {
                heldScore += 6;
            }
            for (String extra : extras) {
                if (extra == null || extra.isBlank()) {
                    continue;
                }
                String el = extra.toLowerCase(Locale.ROOT);
                if (h.items.stream().anyMatch(i -> i.equalsIgnoreCase(el))) {
                    // Below threshold alone — hotbar proximity must not list unrelated quests.
                    extraScore += 4;
                } else if (blob.contains(el) || blob.contains(el.replace(':', '_'))) {
                    extraScore += 2;
                }
            }
            for (String tok : q.split("[^a-z0-9_\\u4e00-\\u9fff]+")) {
                if (!isUsefulQuestToken(tok)) {
                    continue;
                }
                if (blob.contains(tok)) {
                    tokenScore += 2;
                }
            }
            int score = heldScore + extraScore + tokenScore;
            // Reject pure-extra hits (hotbar-only) and weak name-only token noise.
            // With a concrete focus registry id: must reference that id (items list or full id
            // in text). Title/display-name token overlap alone (e.g. 扳手) must not attach
            // unrelated same-name quests (create:wrench vs 「压力发条扳手」).
            if (!held.isEmpty() && heldScore <= 0) {
                continue;
            }
            // Full id in quest text alone is +6 (<8) — still a hard id hit; promote.
            if (!held.isEmpty() && heldScore >= 6 && score < 8) {
                score = 8;
            }
            boolean admit = score >= 8 && (held.isEmpty() ? tokenScore > 0 : heldScore > 0);
            if (admit) {
                // One-shot (no can_repeat): mild demote vs recompletable siblings.
                int adj = h.canRepeat() ? score : Math.max(0, score - 2);
                scored.add(h.withScore(adj));
            }
        }
        scored.sort(Comparator.comparingInt(Hit::score).reversed()
                .thenComparing(h -> h.canRepeat() ? 0 : 1)
                .thenComparing(h -> hasReadableTitle(h) ? 0 : 1)
                .thenComparing(Hit::title));
        scored = preferFocusIdHits(scored, held);
        scored = preferVariantHits(scored, variantTokens);
        scored = preferReadableTitleHits(scored);
        int total = scored.size();
        List<Hit> top = total > MAX_HITS ? new ArrayList<>(scored.subList(0, MAX_HITS)) : scored;
        return new MatchResult(top, total);
    }

    /**
     * Soft-prefer quests that list the focus registry id in tasks/rewards when focus is set.
     * If any do, drop blob/title-only siblings; if none list it, keep remaining (full-id-in-text).
     */
    static List<Hit> preferFocusIdHits(List<Hit> scored, String heldItemId) {
        if (scored == null || scored.isEmpty() || heldItemId == null || heldItemId.isBlank()) {
            return scored == null ? List.of() : scored;
        }
        List<Hit> listed = new ArrayList<>();
        for (Hit h : scored) {
            if (mentionsFocusItem(h, heldItemId)) {
                listed.add(h);
            }
        }
        return listed.isEmpty() ? scored : listed;
    }

    /** Soft-prefer quests whose text/items mention NBT schematic / distinctive name tokens. */
    static List<Hit> preferVariantHits(List<Hit> scored, List<String> variantTokens) {
        return ItemVariantKeysText.preferMentioning(
                scored, variantTokens, h -> hitMentionsVariant(h, variantTokens));
    }

    /**
     * Soft-prefer hits with a real title when mixed with title-less id-only siblings
     * (avoids button label「scroll rolled相關任務」).
     */
    static List<Hit> preferReadableTitleHits(List<Hit> scored) {
        if (scored == null || scored.isEmpty()) {
            return scored == null ? List.of() : scored;
        }
        List<Hit> titled = new ArrayList<>();
        for (Hit h : scored) {
            if (hasReadableTitle(h)) {
                titled.add(h);
            }
        }
        return titled.isEmpty() ? scored : titled;
    }

    /** True when chapter/title/description/items mention a variant disambiguator. */
    public static boolean hitMentionsVariant(Hit h, List<String> variantTokens) {
        if (h == null) {
            return false;
        }
        String blob = (h.chapter + " " + h.title + " " + h.description);
        return ItemVariantKeysText.mentionsAny(blob, h.items(), variantTokens);
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
        return matchForOfflineResult(all, question, heldItemId, extraItemIds, List.of());
    }

    public static MatchResult matchForOfflineResult(
            List<Hit> all,
            String question,
            String heldItemId,
            List<String> extraItemIds,
            List<String> variantTokens
    ) {
        MatchResult scored = matchResult(all, question, heldItemId, extraItemIds, variantTokens);
        if (!scored.hits().isEmpty()) {
            return scored;
        }
        // Offline soften: held/focus only — hotbar extras already scored above (and cannot solo-match).
        List<String> bag = new ArrayList<>();
        if (heldItemId != null && !heldItemId.isBlank()) {
            bag.add(heldItemId);
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
            byHeld.sort(Comparator.comparingInt(Hit::score).reversed()
                    .thenComparing(h -> h.canRepeat() ? 0 : 1)
                    .thenComparing(h -> hasReadableTitle(h) ? 0 : 1)
                    .thenComparing(Hit::title));
            byHeld = preferFocusIdHits(byHeld, heldItemId);
            byHeld = preferVariantHits(byHeld, variantTokens);
            byHeld = preferReadableTitleHits(byHeld);
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
        return parseFile(gameDir, file, text, new LinkedHashSet<>(), false);
    }

    static List<Hit> parseFile(
            Path gameDir, Path file, String text, Set<String> spoilerIds, boolean filterHidden
    ) {
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

        List<Hit> fromQuests = parseQuestsArray(chapter, rel, text, system, spoilerIds, filterHidden);
        if (!fromQuests.isEmpty()) {
            return fromQuests;
        }

        // Heracles / odd single-quest files: one title + one id if clearly paired
        return parseLooseFallback(chapter, rel, text, system, spoilerIds, filterHidden);
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
                    rel, List.of(), 0, false, id, system, false));
        }
        return out;
    }

    private static List<Hit> parseQuestsArray(
            String chapter,
            String rel,
            String text,
            String system,
            Set<String> spoilerIds,
            boolean filterHidden
    ) {
        Matcher am = QUESTS_ARRAY.matcher(text);
        if (!am.find()) {
            return List.of();
        }
        boolean chapterGate = chapterHidesUntilDepsVisible(text, am.start());
        Boolean chapterHideDetails = chapterHideDetailsUntilStartable(text, am.start());
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
            String idKey = id.toUpperCase(Locale.ROOT);
            boolean spoiler = shouldSuppressQuestAdvertise(
                    slice, chapterHideDetails, chapterGate, null);
            if (spoiler) {
                spoilerIds.add(idKey);
                if (filterHidden) {
                    continue;
                }
            }
            String title = cleanTitle(depth1Field(slice, "title"));
            // Never fall back to hex quest id for display — resolve via displayTitle() later
            // Full description[] body (skip empty / {image:} lines) — not only subtitle/first line.
            String desc = questBodyText(slice);
            List<String> items = new ArrayList<>(itemsInRange(slice, 0, slice.length()));
            addVariantHintsFromSlice(slice, items);
            boolean canRepeat = depth1BoolTrue(slice, "can_repeat");
            out.add(new Hit(chapter, title, desc, rel, items, 0, false, idKey, system, canRepeat));
        }
        return out;
    }

    private static List<Hit> parseLooseFallback(
            String chapter,
            String rel,
            String text,
            String system,
            Set<String> spoilerIds,
            boolean filterHidden
    ) {
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
        String idKey = id.toUpperCase(Locale.ROOT);
        if (isSpoilerHiddenQuestObject(text)) {
            spoilerIds.add(idKey);
            if (filterHidden) {
                return List.of();
            }
        }
        List<String> items = new ArrayList<>(itemsInRange(text, 0, text.length()));
        // Same full-body rules as FTB parseQuestsArray (skip blank / {image:} lines).
        String desc = questBodyText(text);
        boolean canRepeat = depth1BoolTrue(text, "can_repeat");
        return List.of(new Hit(chapter, title, desc, rel, items, 0, false, idKey, system, canRepeat));
    }

    /**
     * Strip spoiler-hidden quest objects from chapter SNBT/JSON so PackIndex clips
     * cannot leak secret titles/items when anti-spoiler is on.
     */
    public static String redactHiddenQuestObjects(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        Matcher am = QUESTS_ARRAY.matcher(text);
        if (!am.find()) {
            return isSpoilerHiddenQuestObject(text) ? "" : text;
        }
        boolean chapterGate = chapterHidesUntilDepsVisible(text, am.start());
        Boolean chapterHideDetails = chapterHideDetailsUntilStartable(text, am.start());
        List<int[]> objects = topLevelObjects(text, am.end() - 1);
        if (objects.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text);
        for (int i = objects.size() - 1; i >= 0; i--) {
            int[] span = objects.get(i);
            String slice = text.substring(span[0], span[1]);
            if (shouldSuppressQuestAdvertise(slice, chapterHideDetails, chapterGate, null)) {
                sb.replace(span[0], span[1], "{}");
            }
        }
        return sb.toString();
    }

    /**
     * True when Pack AI must not advertise this quest (facts / 【任務】 status / guide hits).
     * Prefer wrong-null over spoiling.
     *
     * <p>Hide-details inherit (FTB): quest {@code hide_details_until_startable} → chapter
     * {@code hide_quest_details_until_startable} → fileDefault.
     * Hide-until-deps: quest spoiler keys, or chapter {@code hide_quest_until_deps_visible}
     * when the quest has dependencies.
     */
    static boolean shouldSuppressQuestAdvertise(
            String questSlice,
            Boolean chapterHideDetails,
            boolean chapterHideUntilDeps,
            Boolean fileHideDetails
    ) {
        if (questSlice == null || questSlice.isBlank()) {
            return true;
        }
        if (isSpoilerHiddenQuestObject(questSlice)) {
            return true;
        }
        Boolean questDetails = depth1ExplicitBool(questSlice, "hide_details_until_startable");
        // Quest-level true already caught by SPOILER_BOOL_KEYS; still resolve so chapter/file
        // apply when quest flag is absent, and quest false overrides chapter true.
        if (questDetails != null) {
            if (questDetails) {
                return true;
            }
            // explicit false → do not inherit chapter/file details hide
        } else if (Boolean.TRUE.equals(resolveTristate(null, chapterHideDetails, fileHideDetails))) {
            return true;
        }
        if (chapterHideUntilDeps && hasQuestDependencies(questSlice)) {
            return true;
        }
        return false;
    }

    /** Chapter root {@code hide_quest_details_until_startable} (NFWC session/chapter default). */
    static Boolean chapterHideDetailsUntilStartable(String fileText, int questsKeyStart) {
        if (fileText == null || questsKeyStart <= 0) {
            return null;
        }
        return depth1ExplicitBool(fileText.substring(0, questsKeyStart), "hide_quest_details_until_startable");
    }

    /** quest → chapter → file (same shape as consume_items inherit). */
    static Boolean resolveTristate(Boolean quest, Boolean chapter, Boolean fileDefault) {
        if (quest != null) {
            return quest;
        }
        if (chapter != null) {
            return chapter;
        }
        return fileDefault;
    }

    /** {@code key: true|false} at brace-depth 1; absent → null. */
    static Boolean depth1ExplicitBool(String objectSlice, String key) {
        if (objectSlice == null || key == null || key.isBlank()) {
            return null;
        }
        Pattern p = Pattern.compile(
                "\\b" + Pattern.quote(key) + "\\s*:\\s*(true|false)\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(objectSlice);
        while (m.find()) {
            if (braceDepthAt(objectSlice, m.start()) == 1) {
                return Boolean.parseBoolean(m.group(1));
            }
        }
        return null;
    }

    /** True when quest object is hidden/invisible/deps-gated in FTB/Heracles configs. */
    static boolean isSpoilerHiddenQuestObject(String objectSlice) {
        if (objectSlice == null || objectSlice.isBlank()) {
            return false;
        }
        for (String key : SPOILER_BOOL_KEYS) {
            if (depth1BoolTrue(objectSlice, key)) {
                return true;
            }
        }
        // invisible until N tasks complete — without progress, treat as hidden
        Pattern invTasks = Pattern.compile("\\binvisible_until_tasks\\s*:\\s*([1-9]\\d*)\\b");
        Matcher m = invTasks.matcher(objectSlice);
        while (m.find()) {
            if (braceDepthAt(objectSlice, m.start()) == 1) {
                return true;
            }
        }
        return false;
    }

    /** Chapter root {@code hide_quest_until_deps_visible: true} (NFWC). */
    static boolean chapterHidesUntilDepsVisible(String fileText, int questsKeyStart) {
        if (fileText == null || questsKeyStart <= 0) {
            return false;
        }
        String head = fileText.substring(0, questsKeyStart);
        return depth1BoolTrue(head, "hide_quest_until_deps_visible");
    }

    static boolean hasQuestDependencies(String objectSlice) {
        Pattern deps = Pattern.compile("\\bdependencies\\s*:\\s*\\[");
        Matcher m = deps.matcher(objectSlice);
        while (m.find()) {
            if (braceDepthAt(objectSlice, m.start()) != 1) {
                continue;
            }
            int i = m.end();
            while (i < objectSlice.length() && Character.isWhitespace(objectSlice.charAt(i))) {
                i++;
            }
            return i < objectSlice.length() && objectSlice.charAt(i) != ']';
        }
        Pattern one = Pattern.compile("\\bdependency\\s*:\\s*\"");
        Matcher m2 = one.matcher(objectSlice);
        while (m2.find()) {
            if (braceDepthAt(objectSlice, m2.start()) == 1) {
                return true;
            }
        }
        return false;
    }

    /** {@code key: true} at brace-depth 1 (does not match {@code hide_dependency_lines}). */
    static boolean depth1BoolTrue(String objectSlice, String key) {
        Pattern p = Pattern.compile("\\b" + Pattern.quote(key) + "\\s*:\\s*true\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(objectSlice);
        while (m.find()) {
            if (braceDepthAt(objectSlice, m.start()) == 1) {
                return true;
            }
        }
        return false;
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

    /** Cap quest body text stored on {@link Hit} / fed to LLM. */
    static final int MAX_DESC_CHARS = 500;

    /**
     * Subtitle + all meaningful {@code description} lines (array or single string).
     * Skips empty strings and FTB image/item embeds so drink/effect text is not lost
     * when the array starts with {@code ""} or {@code {image:…}}.
     */
    static String questBodyText(String slice) {
        if (slice == null || slice.isBlank()) {
            return "";
        }
        String subtitle = cleanTitle(depth1Field(slice, "subtitle"));
        List<String> lines = descriptionLines(slice);
        String body = String.join(" ", lines).trim();
        String merged;
        if (!subtitle.isEmpty() && !body.isEmpty()) {
            if (body.contains(subtitle)) {
                merged = body;
            } else {
                merged = subtitle + " — " + body;
            }
        } else if (!body.isEmpty()) {
            merged = body;
        } else {
            merged = subtitle;
        }
        if (merged.length() > MAX_DESC_CHARS) {
            return merged.substring(0, MAX_DESC_CHARS) + "…";
        }
        return merged;
    }

    /** True when hit task/reward item ids include the focused stack id. */
    public static boolean mentionsFocusItem(Hit h, String heldItemId) {
        return mentionsFocusItem(h, heldItemId, List.of());
    }

    /**
     * Same as {@link #mentionsFocusItem(Hit, String)}, but when {@code variantTokens} present,
     * require the quest text/items to mention a disambiguator.
     */
    public static boolean mentionsFocusItem(Hit h, String heldItemId, List<String> variantTokens) {
        if (h == null || heldItemId == null || heldItemId.isBlank() || h.items() == null) {
            return false;
        }
        String want = heldItemId.trim().toLowerCase(Locale.ROOT);
        int brace = want.indexOf('{');
        if (brace > 0) {
            want = want.substring(0, brace);
        }
        boolean idHit = false;
        for (String it : h.items()) {
            if (it == null || it.isBlank()) {
                continue;
            }
            String got = it.trim().toLowerCase(Locale.ROOT);
            int b = got.indexOf('{');
            if (b > 0) {
                got = got.substring(0, b);
            }
            if (got.equals(want)) {
                idHit = true;
                break;
            }
        }
        if (!idHit) {
            return false;
        }
        if (variantTokens == null || variantTokens.isEmpty()) {
            return true;
        }
        return hitMentionsVariant(h, variantTokens);
    }

    static List<String> descriptionLines(String slice) {
        List<String> out = new ArrayList<>();
        String single = depth1Field(slice, "description");
        if (!single.isEmpty()) {
            if (!isNonTextDescLine(single)) {
                out.add(cleanTitle(single));
            }
            return out;
        }
        Matcher start = Pattern.compile("description\\s*:\\s*\\[").matcher(slice);
        if (!start.find()) {
            return out;
        }
        int from = start.end();
        int depth = 1;
        boolean inString = false;
        boolean escape = false;
        StringBuilder cur = null;
        for (int i = from; i < slice.length() && depth > 0; i++) {
            char c = slice.charAt(i);
            if (inString) {
                if (escape) {
                    if (cur != null) {
                        cur.append(c);
                    }
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                    if (cur != null) {
                        String s = cleanTitle(cur.toString());
                        if (!s.isEmpty() && !isNonTextDescLine(s)) {
                            out.add(s);
                        }
                        cur = null;
                    }
                } else if (cur != null) {
                    cur.append(c);
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                cur = new StringBuilder();
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            }
        }
        return out;
    }

    static boolean isNonTextDescLine(String s) {
        if (s == null) {
            return true;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return true;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        return lower.startsWith("{image:")
                || lower.startsWith("{item:")
                || lower.startsWith("{entity:")
                || lower.startsWith("{@");
    }

    static String cleanTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        return FTB_CODES.matcher(title).replaceAll("").trim();
    }

    private static LinkedHashSet<String> itemsInRange(String text, int start, int end) {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        int a = Math.max(0, start);
        int b = Math.min(text.length(), Math.max(a, end));
        // Drop quest-book icons — packs often reuse unrelated item ids as decoration
        // (e.g. 「压力发条扳手」icon: create:wrench while task is precision_mechanism).
        String slice = stripQuestIcons(text.substring(a, b));
        Matcher im = ITEM.matcher(slice);
        while (im.find()) {
            items.add(im.group(1).toLowerCase(Locale.ROOT));
        }
        return items;
    }

    /**
     * Pull Tetra schematic {@code key:} / {@code schematics:[...]} tokens from quest SNBT
     * into the hit items list so soft-prefer can disambiguate bare {@code tetra:scroll_rolled}.
     */
    static void addVariantHintsFromSlice(String slice, List<String> items) {
        if (slice == null || slice.isBlank() || items == null) {
            return;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String it : items) {
            if (it != null && !it.isBlank()) {
                seen.add(it.toLowerCase(Locale.ROOT));
            }
        }
        Matcher km = Pattern.compile("\\bkey\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(slice);
        while (km.find()) {
            String k = km.group(1);
            if (k == null || !ItemVariantKeysText.acceptKey(k)) {
                continue;
            }
            String low = k.trim().toLowerCase(Locale.ROOT);
            if (seen.add(low)) {
                items.add(low);
            }
        }
        Matcher sm = Pattern.compile(
                "\\bschematics\\s*:\\s*\\[([^\\]]*)\\]", Pattern.CASE_INSENSITIVE).matcher(slice);
        while (sm.find()) {
            String body = sm.group(1);
            if (body == null || body.isBlank()) {
                continue;
            }
            Matcher qm = Pattern.compile("\"([^\"]+)\"").matcher(body);
            while (qm.find()) {
                String s = qm.group(1);
                if (s == null || s.isBlank()) {
                    continue;
                }
                String low = s.trim().toLowerCase(Locale.ROOT);
                if (seen.add(low)) {
                    items.add(low);
                }
            }
        }
    }

    /**
     * Remove {@code icon: "id"} / {@code icon: { ... }} so decorative FTB icons are not
     * treated as task/reward item ids for Ask matching.
     */
    static String stripQuestIcons(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        Pattern iconKey = Pattern.compile("\\bicon\\s*:", Pattern.CASE_INSENSITIVE);
        Matcher m = iconKey.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        int last = 0;
        while (m.find()) {
            out.append(text, last, m.start());
            int i = m.end();
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i < text.length() && text.charAt(i) == '"') {
                i++;
                boolean esc = false;
                while (i < text.length()) {
                    char c = text.charAt(i++);
                    if (esc) {
                        esc = false;
                    } else if (c == '\\') {
                        esc = true;
                    } else if (c == '"') {
                        break;
                    }
                }
            } else if (i < text.length() && text.charAt(i) == '{') {
                int depth = 0;
                boolean inStr = false;
                boolean esc = false;
                for (; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (inStr) {
                        if (esc) {
                            esc = false;
                        } else if (c == '\\') {
                            esc = true;
                        } else if (c == '"') {
                            inStr = false;
                        }
                        continue;
                    }
                    if (c == '"') {
                        inStr = true;
                    } else if (c == '{') {
                        depth++;
                    } else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            i++;
                            break;
                        }
                    }
                }
            }
            last = i;
        }
        out.append(text, last, text.length());
        return out.toString();
    }

    private static String empty(String s, String fb) {
        return s == null || s.isBlank() ? fb : s;
    }
}

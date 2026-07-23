package com.skps9.packai.logic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Path index + light pack graph + snippet retrieve (recipes / loot / trades). */
public final class PackIndex {
    private static final Set<String> EXTS = Set.of(".js", ".zs", ".groovy", ".json", ".snbt", ".txt", ".md", ".toml");
    private static final int MAX_GRAPH = 200;
    /** Max facts returned per ask — prefer related nodes over dumping the whole graph. */
    private static final int MAX_RETRIEVE_FACTS = 24;
    /** Skip raw script clips when at least one related fact already covers the ask. */
    private static final int SNIPPET_SKIP_WHEN_FACTS = 1;
    private static final Pattern REMOVE = Pattern.compile(
            "event\\.remove\\(\\s*\\{([^}]*)\\}\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM = Pattern.compile("['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHAPELESS = Pattern.compile(
            "event\\.shapeless\\(\\s*([^,\\n]+)\\s*,\\s*\\[([\\s\\S]*?)\\]\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHAPED = Pattern.compile(
            "event\\.shaped\\(\\s*([^,\\n]+)\\s*,\\s*\\[([\\s\\S]*?)\\]\\s*,\\s*\\{([\\s\\S]*?)\\}\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RECIPE_EDGE = Pattern.compile(
            "^item:([a-z0-9_]+:[a-z0-9_./-]+) -\\[recipe_needs\\]-> item:([a-z0-9_]+:[a-z0-9_./-]+)$",
            Pattern.CASE_INSENSITIVE);
    /** KubeJS / legacy onEvent interaction handlers (right/left click, break, entity, food). */
    private static final Pattern INTERACT_HEADER = Pattern.compile(
            "(?:(BlockEvents)\\.(rightClicked|leftClicked|broken)\\s*\\(\\s*(?:['\"]([a-z0-9_.:/-]+)['\"]\\s*,)?"
                    + "|(ItemEvents)\\.(rightClicked|entityInteracted|foodEaten)\\s*\\(\\s*(?:['\"]([a-z0-9_.:/-]+)['\"]\\s*,)?"
                    + "|onEvent\\(\\s*['\"](block\\.right_click|block\\.left_click|block\\.break"
                    + "|item\\.right_click|item\\.entity_interact|item\\.food_eaten)['\"]\\s*,)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERACT_HELD = Pattern.compile(
            "event\\.(?:item|handItem|mainHandItem)(?:\\.id)?"
                    + "(?:\\s*(?:[=!]=|\\.equals\\()|[\\s\\S]{0,60}?)"
                    + "['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERACT_BLOCK = Pattern.compile(
            "event\\.block(?:\\.id)?"
                    + "(?:\\s*(?:[=!]=|\\.equals\\()|[\\s\\S]{0,60}?)"
                    + "['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERACT_ENTITY = Pattern.compile(
            "event\\.(?:target|entity)(?:\\.type)?"
                    + "(?:\\s*(?:[=!]=|\\.equals\\()|[\\s\\S]{0,40}?)"
                    + "['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERACT_GIVE = Pattern.compile(
            "(?:\\.give|giveInHand|addItem|giveExperienceless|spawnAtLocation|popItem|setItemInHand|set\\()"
                    + "\\s*\\(\\s*(?:Item\\.of\\()?['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERACT_ITEM_OF = Pattern.compile(
            "Item\\.of\\(\\s*['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
            Pattern.CASE_INSENSITIVE);

    private final List<String> paths = new ArrayList<>();
    private final Map<String, List<Integer>> inverted = new HashMap<>();
    private final Map<String, String> textCache = new HashMap<>();
    private final Set<String> removedItems = new HashSet<>();
    private final List<String> graphFacts = new ArrayList<>();
    /** item id → loot/trade relative paths that mention it (built at index time). */
    private final Map<String, List<String>> acquirePathsByItem = new HashMap<>();
    /** translation key → localized text (lang JSON, zh preferred over en). */
    private final Map<String, String> translations = new HashMap<>();
    /** item id → description / score / trigger facts (separate from recipe graph cap). */
    private final Map<String, List<String>> descByItem = new HashMap<>();
    private Path root;

    public void build(Path gameDir, List<String> scanners) {
        paths.clear();
        inverted.clear();
        textCache.clear();
        removedItems.clear();
        graphFacts.clear();
        acquirePathsByItem.clear();
        translations.clear();
        descByItem.clear();
        this.root = gameDir;
        if (gameDir == null || !Files.isDirectory(gameDir)) {
            return;
        }
        List<Path> roots = new ArrayList<>();
        if (scanners.contains("kubejs")) {
            roots.add(gameDir.resolve("kubejs"));
        }
        if (scanners.contains("crafttweaker")) {
            roots.add(gameDir.resolve("scripts"));
        }
        if (scanners.contains("groovyscript")) {
            roots.add(gameDir.resolve("groovy"));
        }
        if (scanners.contains("datapacks")) {
            roots.add(gameDir.resolve("datapacks"));
            roots.add(gameDir.resolve("global_packs"));
            roots.add(gameDir.resolve("openloader/data"));
            roots.add(gameDir.resolve("openloader"));
        }
        if (scanners.contains("ftbquests")) {
            roots.add(gameDir.resolve("config/ftbquests"));
        }
        if (scanners.contains("heracles")) {
            roots.add(gameDir.resolve("config/heracles"));
        }
        roots.add(gameDir.resolve("overrides"));

        Set<String> seenRel = new HashSet<>();
        List<String> langRels = new ArrayList<>();
        List<String> scriptRels = new ArrayList<>();
        for (Path r : roots) {
            if (!Files.isDirectory(r)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(r)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    boolean ok = EXTS.stream().anyMatch(name::endsWith);
                    if (!ok) {
                        return;
                    }
                    try {
                        String rel = gameDir.relativize(p).toString().replace('\\', '/');
                        String pl = rel.toLowerCase(Locale.ROOT);
                        long limit = isLangPath(name, pl) ? 800_000L : 400_000L;
                        if (Files.size(p) > limit) {
                            return;
                        }
                        if (!seenRel.add(rel)) {
                            return;
                        }
                        if (QuestGuide.isRewardTablePath(pl, name)) {
                            return;
                        }
                        addPath(rel);
                        if (isLangPath(name, pl)) {
                            langRels.add(rel);
                        } else if (isAcquirePath(pl)) {
                            indexAcquireFile(rel);
                        } else if (isScriptPath(pl)) {
                            scriptRels.add(rel);
                            indexScriptItems(rel);
                        }
                    } catch (IOException ignored) {
                        // skip
                    }
                });
            } catch (IOException ignored) {
                // skip
            }
        }
        // en first, then zh_* so Chinese wins when both exist
        langRels.sort((a, b) -> Integer.compare(langRank(a), langRank(b)));
        for (String rel : langRels) {
            loadLangFile(rel);
        }
        for (String rel : scriptRels) {
            ingestItemDescriptions(rel);
        }
    }

    private static boolean isLangPath(String fileNameLower, String pathLower) {
        return fileNameLower.endsWith(".json") && pathLower.contains("/lang/");
    }

    private static int langRank(String rel) {
        String pl = rel.toLowerCase(Locale.ROOT);
        if (pl.endsWith("/zh_tw.json") || pl.endsWith("\\zh_tw.json")) {
            return 3;
        }
        if (pl.endsWith("/zh_cn.json")) {
            return 2;
        }
        if (pl.endsWith("/en_us.json")) {
            return 1;
        }
        return 0;
    }

    private void loadLangFile(String rel) {
        String text = readText(rel);
        if (text == null || text.isBlank() || text.charAt(0) != '{') {
            return;
        }
        try {
            JsonElement rootEl = JsonParser.parseString(text);
            if (!rootEl.isJsonObject()) {
                return;
            }
            JsonObject obj = rootEl.getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                if (e.getValue() == null || !e.getValue().isJsonPrimitive()) {
                    continue;
                }
                String key = e.getKey();
                String val = e.getValue().getAsString();
                if (key == null || key.isBlank() || val == null || val.isBlank()) {
                    continue;
                }
                translations.put(key, val);
            }
        } catch (RuntimeException ignored) {
            // malformed lang — skip
        }
    }

    private void ingestItemDescriptions(String rel) {
        String text = readText(rel);
        if (text == null || text.isBlank()) {
            return;
        }
        ItemDescFacts.mergeInto(descByItem, ItemDescFacts.parse(text, translations::get));
    }

    public RetrieveResult retrieve(String question, String heldItemId, List<String> focusMods) {
        return retrieve(question, heldItemId, focusMods, List.of());
    }

    public RetrieveResult retrieve(
            String question,
            String heldItemId,
            List<String> focusMods,
            List<String> extraItemIds
    ) {
        return retrieve(question, heldItemId, focusMods, extraItemIds, List.of());
    }

    public RetrieveResult retrieve(
            String question,
            String heldItemId,
            List<String> focusMods,
            List<String> extraItemIds,
            List<String> extraHintTokens
    ) {
        List<String> tokens = tokenize(question, heldItemId);
        if (extraItemIds != null) {
            for (String id : extraItemIds) {
                for (String t : tokenize(null, id)) {
                    if (!tokens.contains(t)) {
                        tokens.add(t);
                    }
                }
            }
        }
        if (extraHintTokens != null) {
            for (String h : extraHintTokens) {
                if (h == null || h.isBlank()) {
                    continue;
                }
                String t = h.toLowerCase(Locale.ROOT).trim();
                if (t.length() >= 2 && !tokens.contains(t)) {
                    tokens.add(t);
                }
            }
        }
        Set<Integer> cand = new HashSet<>();
        for (String t : tokens) {
            List<Integer> ids = inverted.get(t);
            if (ids != null) {
                cand.addAll(ids);
            }
        }
        for (String mid : focusMods) {
            List<Integer> ids = inverted.get(mid.toLowerCase(Locale.ROOT));
            if (ids != null) {
                cand.addAll(ids);
            }
        }
        if (cand.isEmpty()) {
            for (int i = 0; i < Math.min(paths.size(), 40); i++) {
                cand.add(i);
            }
        }

        record Scored(int score, int idx) {}
        List<Scored> scored = new ArrayList<>();
        for (int idx : cand) {
            String rel = paths.get(idx);
            String pl = rel.toLowerCase(Locale.ROOT);
            int score = 0;
            for (String t : tokens) {
                if (pl.contains(t)) {
                    score += 2;
                }
            }
            for (String mid : focusMods) {
                if (pl.contains(mid.toLowerCase(Locale.ROOT))) {
                    score += 3;
                }
            }
            if (isAcquirePath(pl)) {
                score += 2;
            }
            if (isScriptPath(pl) && score == 0) {
                // inverted hit by item id in file body — path may not contain the token
                score = 1;
            }
            if (score > 0 || tokens.isEmpty()) {
                scored.add(new Scored(score, idx));
            }
        }
        scored.sort((a, b) -> Integer.compare(b.score, a.score));

        List<String> snippets = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        int topScore = 0;
        int read = 0;
        for (Scored s : scored) {
            if (read >= 40) {
                break;
            }
            String rel = paths.get(s.idx);
            if (!pathMatchesFocus(rel, focusMods) && !focusMods.isEmpty()) {
                String pl = rel.toLowerCase(Locale.ROOT);
                if (!(isPackScriptTree(pl) || pl.contains("ftbquests") || pl.contains("/overrides/")
                        || pl.contains("overrides/") || pl.contains("openloader") || isAcquirePath(pl))) {
                    continue;
                }
            }
            String text = readText(rel);
            read++;
            if (text == null) {
                continue;
            }
            ingestGraph(rel, text);
            int score = s.score;
            String lower = text.toLowerCase(Locale.ROOT);
            for (String t : tokens) {
                if (lower.contains(t)) {
                    score += 3;
                }
            }
            if (score <= 0 && !tokens.isEmpty()) {
                continue;
            }
            topScore = Math.max(topScore, score);
            // Defer snippets until we know whether related graph facts already cover the ask.
            if (snippets.size() < 3) {
                String clip = text.length() > 600 ? text.substring(0, 600) : text;
                snippets.add("// file: " + rel + "\n" + clip);
                sources.add(rel);
            }
        }
        Set<String> seeds = seedItemIds(heldItemId, extraItemIds, tokens);
        List<String> related = selectRelatedGraphFacts(seeds, MAX_RETRIEVE_FACTS);
        // Codegraph-style: facts-first. Raw clips are fallback when the subgraph is thin.
        if (related.size() >= SNIPPET_SKIP_WHEN_FACTS) {
            snippets = List.of();
            sources = List.of();
        }
        boolean high = topScore >= 12 && (!snippets.isEmpty() || !related.isEmpty());
        return new RetrieveResult(snippets, sources, topScore, high, Set.copyOf(removedItems), related);
    }

    /**
     * Item ids that seed graph neighborhood (held / hotbar / {@code ns:path} tokens in the question).
     */
    static Set<String> seedItemIds(String heldItemId, List<String> extraItemIds, List<String> tokens) {
        LinkedHashSet<String> seeds = new LinkedHashSet<>();
        addItemSeed(seeds, heldItemId);
        if (extraItemIds != null) {
            for (String id : extraItemIds) {
                addItemSeed(seeds, id);
            }
        }
        if (tokens != null) {
            for (String t : tokens) {
                if (t != null && t.indexOf(':') > 0) {
                    addItemSeed(seeds, t);
                }
            }
        }
        return seeds;
    }

    private static void addItemSeed(Set<String> seeds, String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        String s = id.toLowerCase(Locale.ROOT).trim();
        if (s.indexOf(':') > 0) {
            seeds.add(s);
        }
    }

    /**
     * Keep only facts that mention a seed item id (1-hop neighborhood), capped for LLM context.
     * Description / score / trigger facts from {@link #descByItem} are preferred.
     */
    List<String> selectRelatedGraphFacts(Set<String> seeds, int max) {
        if (seeds == null || seeds.isEmpty() || max <= 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String seed : seeds) {
            List<String> descs = descByItem.get(seed);
            if (descs == null) {
                continue;
            }
            for (String f : descs) {
                if (out.size() >= max) {
                    return List.copyOf(out);
                }
                if (!out.contains(f)) {
                    out.add(f);
                }
            }
        }
        for (String f : graphFacts) {
            if (out.size() >= max) {
                break;
            }
            for (String seed : seeds) {
                if (f.contains(seed) && !out.contains(f)) {
                    out.add(f);
                    break;
                }
            }
        }
        return List.copyOf(out);
    }

    /** Package-visible: description facts for an item (tests / offline). */
    List<String> descFactsFor(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return List.of();
        }
        List<String> list = descByItem.get(itemId.toLowerCase(Locale.ROOT).trim());
        return list == null ? List.of() : List.copyOf(list);
    }

    static boolean isPurposeQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String q = question.toLowerCase(Locale.ROOT);
        return q.contains("用途")
                || q.contains("效果")
                || q.contains("幹嘛")
                || q.contains("干嘛")
                || q.contains("怎麼用")
                || q.contains("怎么用")
                || q.contains("做什麼")
                || q.contains("做什么")
                || q.contains("有什麼用")
                || q.contains("有什么用")
                || q.contains("what does")
                || q.contains("how does")
                || (q.contains("what is") && (q.contains("organ") || q.contains("for")))
                || q.contains("how do i use")
                || q.contains("effect");
    }

    /**
     * Local non-JEI acquire hints for an item (loot / trade / script recipe).
     * Call after {@link #retrieve} so script recipes are also ingested.
     */
    public List<String> acquireFactsFor(String itemId) {
        return acquireFactsFor(itemId, "zh_tw");
    }

    public List<String> acquireFactsFor(String itemId, String replyLang) {
        if (itemId == null || itemId.isBlank()) {
            return List.of();
        }
        String lang = replyLang == null || replyLang.isBlank() ? "zh_tw" : replyLang.trim();
        String id = itemId.toLowerCase(Locale.ROOT).trim();
        List<String> rels = acquirePathsByItem.getOrDefault(id, List.of());
        int n = 0;
        for (String rel : rels) {
            if (n >= 10) {
                break;
            }
            String text = readText(rel);
            if (text != null) {
                ingestGraph(rel, text);
                n++;
            }
        }

        List<String> out = new ArrayList<>();
        List<String> cycles = new ArrayList<>();
        String prefix = "item:" + id;
        Map<String, Set<String>> recipeNeeds = recipeNeedsIndex();
        for (String f : graphFacts) {
            if (out.size() + cycles.size() >= 12) {
                break;
            }
            if (f.startsWith(prefix + " -[fish]-> ")) {
                out.add(ReplyLang.fishing(lang) + f.substring((prefix + " -[fish]-> ").length()));
            } else if (f.startsWith(prefix + " -[loot]-> ")) {
                out.add(ReplyLang.loot(lang) + f.substring((prefix + " -[loot]-> ").length()));
            } else if (f.startsWith(prefix + " -[trade]-> ")) {
                out.add(ReplyLang.trade(lang) + f.substring((prefix + " -[trade]-> ").length()));
            } else if (f.startsWith(prefix + " -[recipe_needs]-> ")) {
                String need = f.substring((prefix + " -[recipe_needs]-> ").length()).replace("item:", "");
                if (isCompactCycle(id, need, recipeNeeds)) {
                    if (cycles.size() < 3) {
                        cycles.add(ReplyLang.compactCycle(lang, Plainify.displayName(need)));
                    }
                } else {
                    out.add(ReplyLang.scriptNeeds(lang, Plainify.displayName(need)));
                }
            } else if (f.startsWith(prefix + " -[removed]-> ")) {
                out.add(ReplyLang.scriptRemoved(lang));
            } else if (f.startsWith(prefix + " -[right_click]-> ")) {
                String rest = f.substring((prefix + " -[right_click]-> ").length());
                String held = afterKey(rest, "held:");
                String target = interactTarget(rest);
                String via = afterKey(rest, "via:");
                if (target != null) {
                    out.add(ReplyLang.interactGet(
                            lang,
                            held == null || "_".equals(held) ? null : Plainify.displayName(held),
                            Plainify.displayName(target),
                            via));
                }
            } else if (f.startsWith(prefix + " -[right_click_use]-> ")) {
                String rest = f.substring((prefix + " -[right_click_use]-> ").length());
                String target = interactTarget(rest);
                String gets = afterKey(rest, "gets:");
                String via = afterKey(rest, "via:");
                if (target != null && gets != null && !"_".equals(target)) {
                    out.add(ReplyLang.interactUse(
                            lang, Plainify.displayName(target), Plainify.displayName(gets), via));
                } else if (gets != null) {
                    out.add(ReplyLang.interactUseSelf(lang, Plainify.displayName(gets), via));
                }
            } else if (f.startsWith(prefix + " -[right_click_as_block]-> ")) {
                String rest = f.substring((prefix + " -[right_click_as_block]-> ").length());
                String held = afterKey(rest, "held:");
                String gets = afterKey(rest, "gets:");
                String via = afterKey(rest, "via:");
                if (gets != null) {
                    out.add(ReplyLang.interactAsTarget(
                            lang,
                            held == null || "_".equals(held) ? null : Plainify.displayName(held),
                            Plainify.displayName(gets),
                            via));
                }
            }
        }
        if (out.isEmpty() && cycles.isEmpty()) {
            return List.of();
        }
        List<String> labeled = new ArrayList<>();
        labeled.add(ReplyLang.localAcquireHeader(lang, Plainify.displayName(id)));
        labeled.addAll(out);
        labeled.addAll(cycles);
        return labeled;
    }

    /**
     * True if A↔B looks like packing/unpacking (ingot↔block), not a real progression craft.
     */
    static boolean isCompactCycle(String a, String b, Map<String, Set<String>> recipeNeeds) {
        if (a == null || b == null || a.equalsIgnoreCase(b)) {
            return false;
        }
        String x = a.toLowerCase(Locale.ROOT);
        String y = b.toLowerCase(Locale.ROOT);
        Set<String> xn = recipeNeeds.getOrDefault(x, Set.of());
        Set<String> yn = recipeNeeds.getOrDefault(y, Set.of());
        if (xn.contains(y) && yn.contains(x)) {
            return true;
        }
        // Crafting unit from its storage block (unpack) is not a real obtain path
        return looksLikeStoragePair(x, y)
                && isUnitForm(itemPath(x))
                && isStorageForm(itemPath(y));
    }

    /** Build out→needs from current {@link #graphFacts}. */
    Map<String, Set<String>> recipeNeedsIndex() {
        Map<String, Set<String>> map = new HashMap<>();
        for (String f : graphFacts) {
            Matcher m = RECIPE_EDGE.matcher(f);
            if (m.matches()) {
                map.computeIfAbsent(m.group(1).toLowerCase(Locale.ROOT), k -> new HashSet<>())
                        .add(m.group(2).toLowerCase(Locale.ROOT));
            }
        }
        return map;
    }

    static boolean looksLikeStoragePair(String a, String b) {
        String pa = itemPath(a);
        String pb = itemPath(b);
        String sa = storageStem(pa);
        String sb = storageStem(pb);
        if (sa.isEmpty() || !sa.equals(sb)) {
            return false;
        }
        boolean aStore = isStorageForm(pa);
        boolean bStore = isStorageForm(pb);
        boolean aUnit = isUnitForm(pa);
        boolean bUnit = isUnitForm(pb);
        return (aStore && bUnit) || (bStore && aUnit);
    }

    static String itemPath(String id) {
        if (id == null) {
            return "";
        }
        int c = id.indexOf(':');
        return c >= 0 ? id.substring(c + 1) : id;
    }

    static String storageStem(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        String[] suffixes = {
                "_storage_block", "_block", "_ingot", "_nugget", "_gem", "_dust",
                "_plate", "_rod", "_gear", "_coin"
        };
        for (String s : suffixes) {
            if (p.endsWith(s)) {
                return p.substring(0, p.length() - s.length());
            }
        }
        return p;
    }

    static boolean isStorageForm(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.endsWith("_block") || p.endsWith("_storage_block");
    }

    static boolean isUnitForm(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (isStorageForm(p)) {
            return false;
        }
        if (p.endsWith("_ingot") || p.endsWith("_nugget") || p.endsWith("_gem")
                || p.endsWith("_dust") || p.endsWith("_plate") || p.endsWith("_rod")
                || p.endsWith("_gear") || p.endsWith("_coin")) {
            return true;
        }
        // bare material id (e.g. minecraft:iron with iron_block counterpart)
        return storageStem(p).equals(p);
    }

    public List<String> paths() {
        return List.copyOf(paths);
    }

    public boolean touchesFocus(List<String> focusMods, String heldItemId) {
        String held = heldItemId == null ? "" : heldItemId.toLowerCase(Locale.ROOT);
        for (String rel : paths) {
            String pl = rel.toLowerCase(Locale.ROOT);
            if (isPackScriptTree(pl) || pl.contains("/scripts/") || pl.contains("/groovy/")
                    || pl.startsWith("scripts/") || pl.startsWith("groovy/")) {
                for (String mid : focusMods) {
                    if (pl.contains(mid.toLowerCase(Locale.ROOT))) {
                        return true;
                    }
                }
                if (!held.isEmpty() && pl.contains(held.replace(':', '_'))) {
                    return true;
                }
            }
            if (pl.startsWith("datapacks/") || pl.startsWith("overrides/") || pl.startsWith("openloader/")
                    || pl.contains("/overrides/") || isAcquirePath(pl)) {
                for (String mid : focusMods) {
                    if (pl.contains("/" + mid.toLowerCase(Locale.ROOT) + "/")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void addPath(String rel) {
        int idx = paths.size();
        paths.add(rel);
        for (String tok : rel.toLowerCase(Locale.ROOT).split("[/._\\-]+")) {
            if (tok.length() > 1) {
                inverted.computeIfAbsent(tok, k -> new ArrayList<>()).add(idx);
            }
        }
    }

    private void indexAcquireFile(String rel) {
        String text = readText(rel);
        if (text == null || text.isBlank()) {
            return;
        }
        int idx = paths.size() - 1;
        Matcher im = ITEM.matcher(text);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        while (im.find() && seen.size() < 80) {
            String id = im.group(1).toLowerCase(Locale.ROOT);
            if (isNoiseItemId(id) || !seen.add(id)) {
                continue;
            }
            acquirePathsByItem.computeIfAbsent(id, k -> new ArrayList<>()).add(rel);
            inverted.computeIfAbsent(id, k -> new ArrayList<>()).add(idx);
            int colon = id.indexOf(':');
            if (colon > 0 && colon < id.length() - 1) {
                String path = id.substring(colon + 1);
                inverted.computeIfAbsent(path, k -> new ArrayList<>()).add(idx);
                String ns = id.substring(0, colon);
                inverted.computeIfAbsent(ns, k -> new ArrayList<>()).add(idx);
            }
        }
    }

    /** Index quoted item ids in scripts so retrieve can find recipe files by held item. */
    private void indexScriptItems(String rel) {
        String text = readText(rel);
        if (text == null || text.isBlank()) {
            return;
        }
        int idx = paths.size() - 1;
        Matcher im = ITEM.matcher(text);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        while (im.find() && seen.size() < 120) {
            String id = im.group(1).toLowerCase(Locale.ROOT);
            if (isNoiseItemId(id) || !seen.add(id)) {
                continue;
            }
            inverted.computeIfAbsent(id, k -> new ArrayList<>()).add(idx);
            int colon = id.indexOf(':');
            if (colon > 0 && colon < id.length() - 1) {
                inverted.computeIfAbsent(id.substring(colon + 1), k -> new ArrayList<>()).add(idx);
                inverted.computeIfAbsent(id.substring(0, colon), k -> new ArrayList<>()).add(idx);
            }
        }
    }

    private String readText(String rel) {
        if (textCache.containsKey(rel)) {
            return textCache.get(rel);
        }
        if (root == null) {
            return null;
        }
        try {
            String text = Files.readString(root.resolve(rel), StandardCharsets.UTF_8);
            textCache.put(rel, text);
            return text;
        } catch (IOException e) {
            return null;
        }
    }

    /** Package-visible for tests. */
    void ingestGraph(String rel, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String pl = rel == null ? "" : rel.toLowerCase(Locale.ROOT);

        Matcher rm = REMOVE.matcher(text);
        while (rm.find()) {
            Matcher im = ITEM.matcher(rm.group(1));
            while (im.find()) {
                String id = im.group(1).toLowerCase(Locale.ROOT);
                removedItems.add(id);
                addFact("item:" + id + " -[removed]-> true");
            }
        }

        Matcher sm = SHAPELESS.matcher(text);
        while (sm.find() && graphFacts.size() < MAX_GRAPH) {
            Matcher outM = ITEM.matcher(sm.group(1));
            String out = outM.find() ? outM.group(1).toLowerCase(Locale.ROOT) : null;
            if (out == null) {
                continue;
            }
            Matcher im = ITEM.matcher(sm.group(2));
            while (im.find()) {
                String need = im.group(1).toLowerCase(Locale.ROOT);
                addFact("item:" + out + " -[recipe_needs]-> item:" + need);
            }
        }

        Matcher sh = SHAPED.matcher(text);
        while (sh.find() && graphFacts.size() < MAX_GRAPH) {
            Matcher outM = ITEM.matcher(sh.group(1));
            String out = outM.find() ? outM.group(1).toLowerCase(Locale.ROOT) : null;
            if (out == null) {
                continue;
            }
            LinkedHashSet<String> needs = new LinkedHashSet<>();
            Matcher im2 = ITEM.matcher(sh.group(2));
            while (im2.find()) {
                needs.add(im2.group(1).toLowerCase(Locale.ROOT));
            }
            Matcher im3 = ITEM.matcher(sh.group(3));
            while (im3.find()) {
                needs.add(im3.group(1).toLowerCase(Locale.ROOT));
            }
            for (String need : needs) {
                addFact("item:" + out + " -[recipe_needs]-> item:" + need);
            }
        }

        if (isFishingPath(pl)) {
            ingestAcquireEdges(rel, text, "fish");
        } else if (isLootPath(pl)) {
            ingestAcquireEdges(rel, text, "loot");
        } else if (isTradePath(pl)) {
            ingestAcquireEdges(rel, text, "trade");
        }
        if (isScriptPath(pl)) {
            ingestRightClickInteractions(text);
            ItemDescFacts.mergeInto(descByItem, ItemDescFacts.parse(text, translations::get));
        }
    }

    /**
     * Parse KubeJS / legacy interaction scripts into graph facts.
     * Covers right/left click, break, entity interact, food eaten, and onEvent('…').
     */
    void ingestRightClickInteractions(String text) {
        for (String fact : parseRightClickFacts(text)) {
            addFact(fact);
        }
    }

    /** Extract interaction facts from script source (no side effects). */
    static List<String> parseRightClickFacts(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        Matcher m = INTERACT_HEADER.matcher(text);
        int guard = 0;
        while (m.find() && guard++ < 60) {
            InteractKind kind = classifyInteract(m);
            if (kind == null) {
                continue;
            }
            String body = extractHandlerBody(text, m.end());
            if (body == null || body.length() < 8) {
                continue;
            }
            String held = firstMatch(INTERACT_HELD, body);
            String block = firstMatchBlock(body);
            String entity = firstMatch(INTERACT_ENTITY, body);
            if (kind.filterId() != null && !isNoiseItemId(kind.filterId())) {
                if (kind.preferBlockFilter() && block == null) {
                    block = kind.filterId();
                } else if (kind.preferEntityFilter() && entity == null) {
                    entity = kind.filterId();
                } else if (kind.preferItemFilter() && held == null) {
                    held = kind.filterId();
                }
            }
            String targetKey;
            String target;
            if (entity != null && (kind.preferEntityFilter() || block == null)) {
                targetKey = "entity";
                target = entity;
            } else if (block != null) {
                targetKey = "block";
                target = block;
            } else {
                targetKey = "block";
                target = null;
            }
            LinkedHashSet<String> results = collectInteractResults(body, held, target);
            if (results.isEmpty()) {
                continue;
            }
                String via = kind.via();
            for (String result : results) {
                if (result.equals(held)) {
                    continue;
                }
                // Breaking a block into itself (e.g. silk spawner) is a valid obtain path.
                if (result.equals(target) && !"break".equals(via)) {
                    continue;
                }
                if (target != null && held != null) {
                    out.add("item:" + result + " -[right_click]-> held:" + held
                            + " + " + targetKey + ":" + target + " + via:" + via);
                    out.add("item:" + held + " -[right_click_use]-> " + targetKey + ":" + target
                            + " + gets:" + result + " + via:" + via);
                    out.add("item:" + target + " -[right_click_as_block]-> held:" + held
                            + " + gets:" + result + " + via:" + via);
                } else if (target != null) {
                    out.add("item:" + result + " -[right_click]-> held:_ + " + targetKey + ":" + target
                            + " + via:" + via);
                    out.add("item:" + target + " -[right_click_as_block]-> held:_ + gets:" + result
                            + " + via:" + via);
                } else if (held != null) {
                    out.add("item:" + result + " -[right_click]-> held:" + held + " + block:_ + via:" + via);
                    out.add("item:" + held + " -[right_click_use]-> block:_ + gets:" + result + " + via:" + via);
                }
            }
        }
        return List.copyOf(out);
    }

    private record InteractKind(
            String via,
            String filterId,
            boolean preferBlockFilter,
            boolean preferItemFilter,
            boolean preferEntityFilter
    ) {}

    private static InteractKind classifyInteract(Matcher m) {
        if (m.group(1) != null) {
            // BlockEvents.*
            String method = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT);
            String via = switch (method) {
                case "leftclicked" -> "left_click";
                case "broken" -> "break";
                default -> "right_click";
            };
            String filter = m.group(3) == null ? null : m.group(3).toLowerCase(Locale.ROOT);
            return new InteractKind(via, filter, true, false, false);
        }
        if (m.group(4) != null) {
            String method = m.group(5) == null ? "" : m.group(5).toLowerCase(Locale.ROOT);
            String via = switch (method) {
                case "entityinteracted" -> "entity";
                case "foodeaten" -> "food";
                default -> "right_click";
            };
            String filter = m.group(6) == null ? null : m.group(6).toLowerCase(Locale.ROOT);
            boolean entity = "entity".equals(via);
            return new InteractKind(via, filter, false, !entity, entity);
        }
        if (m.group(7) != null) {
            String ev = m.group(7).toLowerCase(Locale.ROOT);
            String via = switch (ev) {
                case "block.left_click" -> "left_click";
                case "block.break" -> "break";
                case "item.entity_interact" -> "entity";
                case "item.food_eaten" -> "food";
                default -> "right_click";
            };
            boolean blockish = ev.startsWith("block.");
            boolean entity = ev.contains("entity");
            return new InteractKind(via, null, blockish && !entity, ev.startsWith("item.") && !entity, entity);
        }
        return null;
    }

    private static LinkedHashSet<String> collectInteractResults(String body, String held, String target) {
        LinkedHashSet<String> results = new LinkedHashSet<>();
        Matcher gm = INTERACT_GIVE.matcher(body);
        while (gm.find() && results.size() < 4) {
            String id = gm.group(1).toLowerCase(Locale.ROOT);
            if (!isNoiseItemId(id)) {
                results.add(id);
            }
        }
        if (results.isEmpty()) {
            Matcher im = INTERACT_ITEM_OF.matcher(body);
            while (im.find() && results.size() < 4) {
                String id = im.group(1).toLowerCase(Locale.ROOT);
                if (isNoiseItemId(id) || id.equals(held) || id.equals(target)) {
                    continue;
                }
                results.add(id);
            }
        }
        return results;
    }

    private static String interactTarget(String rest) {
        String t = afterKey(rest, "block:");
        if (t == null || "_".equals(t)) {
            t = afterKey(rest, "entity:");
        }
        return t == null || "_".equals(t) ? null : t;
    }

    static String extractHandlerBody(String text, int from) {
        if (text == null || from < 0 || from >= text.length()) {
            return "";
        }
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        int arrow = text.indexOf("=>", i);
        int brace = text.indexOf('{', i);
        if (arrow >= 0 && (brace < 0 || arrow < brace)) {
            brace = text.indexOf('{', arrow);
        }
        if (brace < 0 || brace - i > 120) {
            int end = Math.min(text.length(), i + 500);
            return text.substring(i, end);
        }
        int depth = 0;
        int limit = Math.min(text.length(), brace + 2500);
        for (int j = brace; j < limit; j++) {
            char c = text.charAt(j);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(brace, j + 1);
                }
            }
        }
        return text.substring(brace, limit);
    }

    private static String firstMatch(Pattern p, String body) {
        Matcher m = p.matcher(body);
        while (m.find()) {
            String id = m.group(1).toLowerCase(Locale.ROOT);
            if (!isNoiseItemId(id)) {
                return id;
            }
        }
        return null;
    }

    /** Block target: skip {@code .set('…')} / setblock side effects and noise ids like air. */
    private static String firstMatchBlock(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        Matcher m = INTERACT_BLOCK.matcher(body);
        while (m.find()) {
            String id = m.group(1).toLowerCase(Locale.ROOT);
            if (isNoiseItemId(id)) {
                continue;
            }
            int start = m.start();
            String before = body.substring(Math.max(0, start - 24), start).toLowerCase(Locale.ROOT);
            if (before.contains(".set(") || before.contains("setblock") || before.contains(".setblock")) {
                continue;
            }
            return id;
        }
        return null;
    }

    private static String afterKey(String rest, String key) {
        if (rest == null || key == null) {
            return null;
        }
        int i = rest.indexOf(key);
        if (i < 0) {
            return null;
        }
        int start = i + key.length();
        int end = start;
        while (end < rest.length()) {
            char c = rest.charAt(end);
            if (c == ' ' || c == '+') {
                break;
            }
            end++;
        }
        String id = rest.substring(start, end).trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            return null;
        }
        if ("_".equals(id)) {
            return "_";
        }
        // via:right_click / left_click / break / entity / food — not item ids
        if ("via:".equals(key)) {
            return id;
        }
        return isNoiseItemId(id) ? null : id;
    }

    private void ingestAcquireEdges(String rel, String text, String kind) {
        String label = humanAcquireLabel(rel);
        String edge = " -[" + kind + "]-> ";
        Matcher im = ITEM.matcher(text);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        while (im.find() && graphFacts.size() < MAX_GRAPH && seen.size() < 40) {
            String id = im.group(1).toLowerCase(Locale.ROOT);
            if (isNoiseItemId(id) || !seen.add(id)) {
                continue;
            }
            addFact("item:" + id + edge + label);
        }
    }

    private void addFact(String fact) {
        if (graphFacts.size() >= MAX_GRAPH || graphFacts.contains(fact)) {
            return;
        }
        graphFacts.add(fact);
    }

    static boolean isNoiseItemId(String id) {
        if (id == null || !id.contains(":")) {
            return true;
        }
        String path = id.substring(id.indexOf(':') + 1);
        return path.isEmpty()
                || path.equals("item")
                || path.equals("block")
                || path.equals("empty")
                || path.equals("air")
                || path.equals("entity")
                || path.equals("tag")
                || path.startsWith("loot_table");
    }

    static boolean isScriptPath(String pathLower) {
        return pathLower.endsWith(".js") || pathLower.endsWith(".zs") || pathLower.endsWith(".groovy");
    }

    static boolean isAcquirePath(String pathLower) {
        return isFishingPath(pathLower) || isLootPath(pathLower) || isTradePath(pathLower);
    }

    /** Fishing loot / gameplay fishing tables (JEI often omits these). */
    static boolean isFishingPath(String pathLower) {
        return pathLower.contains("fishing")
                || pathLower.contains("fisherman")
                || pathLower.contains("/fish/")
                || pathLower.contains("fish_loot");
    }

    static boolean isLootPath(String pathLower) {
        return pathLower.contains("loot_table") || pathLower.contains("loot_tables");
    }

    static boolean isTradePath(String pathLower) {
        return pathLower.contains("villager")
                || pathLower.contains("/trade")
                || pathLower.contains("trades")
                || pathLower.contains("wandering_trader");
    }

    static String humanAcquireLabel(String rel) {
        return ReplyLang.humanAcquireLabel("zh_tw", rel);
    }

    private static boolean pathMatchesFocus(String rel, List<String> focusMods) {
        String pl = rel.toLowerCase(Locale.ROOT);
        if (isPackScriptTree(pl) || pl.contains("/overrides/") || pl.startsWith("overrides/")
                || pl.contains("ftbquests") || pl.contains("readme") || pl.contains("openloader")
                || isAcquirePath(pl)) {
            for (String mid : focusMods) {
                if (pl.contains(mid.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return isPackScriptTree(pl) || isAcquirePath(pl);
        }
        for (String mid : focusMods) {
            if (pl.contains(mid.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return focusMods.isEmpty();
    }

    static boolean isPackScriptTree(String pathLower) {
        return pathLower.startsWith("kubejs/")
                || pathLower.contains("/kubejs/")
                || pathLower.startsWith("scripts/")
                || pathLower.contains("/scripts/")
                || pathLower.startsWith("groovy/")
                || pathLower.contains("/groovy/");
    }

    private static List<String> tokenize(String question, String held) {
        List<String> tokens = new ArrayList<>();
        if (question != null) {
            for (String t : question.toLowerCase(Locale.ROOT).split("[^a-z0-9_:./\\-\\u4e00-\\u9fff]+")) {
                if (t.length() >= 2) {
                    tokens.add(t);
                }
            }
        }
        if (held != null && held.contains(":")) {
            tokens.add(held.toLowerCase(Locale.ROOT));
            tokens.add(held.substring(0, held.indexOf(':')).toLowerCase(Locale.ROOT));
            tokens.add(held.substring(held.indexOf(':') + 1).toLowerCase(Locale.ROOT));
        }
        return tokens;
    }

    public record RetrieveResult(
            List<String> snippets,
            List<String> sources,
            int topScore,
            boolean highConfidence,
            Set<String> removedItems,
            List<String> graphFacts
    ) {}
}

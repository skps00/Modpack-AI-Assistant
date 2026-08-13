package com.skps9.packai.logic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skps9.packai.config.PackAiConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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
    /**
     * Quiet prefix on quest acquire fact titles when FTB {@code can_repeat: true}.
     * Stripped before player-facing labels; used only to rank repeatable above one-shot.
     */
    static final String QUEST_REPEAT_MARK = "\u0001";
    /** Max facts returned per ask — prefer related nodes over dumping the whole graph. */
    private static final int MAX_RETRIEVE_FACTS = 24;
    /** Skip raw clips when enough related facts cover the ask (single weak fact keeps clips). */
    private static final int SNIPPET_SKIP_WHEN_FACTS = 2;
    /** Default nearby clip radius when caller omits config (unit tests). */
    private static final int CLIP_LINES_RADIUS_DEFAULT = 30;
    private static final int CLIP_MAX_CHARS = 1100;
    private static final Pattern REMOVE = Pattern.compile(
            "event\\.remove\\(\\s*\\{([^}]*)\\}\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM = Pattern.compile("['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern TASKS_ARRAY = Pattern.compile("\\btasks\\s*:\\s*\\[", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUESTS_ARRAY = Pattern.compile("\\bquests\\s*:\\s*\\[", Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPE_ITEM = Pattern.compile("\\btype\\s*:\\s*\"?item\"?\\b", Pattern.CASE_INSENSITIVE);
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
                    + "|(PlayerEvents|EntityEvents)\\.(tick)\\s*\\("
                    + "|onEvent\\(\\s*['\"](block\\.right_click|block\\.left_click|block\\.break"
                    + "|item\\.right_click|item\\.entity_interact|item\\.food_eaten|player\\.tick)['\"]\\s*,)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERACT_HELD = Pattern.compile(
            "event\\.(?:item|handItem|mainHandItem)(?:\\.id)?"
                    + "(?:\\s*(?:[=!]=|\\.equals\\()|[\\s\\S]{0,60}?)"
                    + "['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERACT_HELD_TAG = Pattern.compile(
            "(?:event\\.(?:item|handItem|mainHandItem)|item)\\.hasTag\\(\\s*['\"]#?([a-z0-9_.:/-]+)['\"]",
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
    /** Dynamic drop: popItem/give/addItem(getXxx()|randomGet|Item.of(randomGet)) — not a literal id. */
    private static final Pattern INTERACT_DYNAMIC_DROP = Pattern.compile(
            "(?:popItem(?:FromFace)?|\\.give|giveInHand|addItem)\\s*\\(\\s*"
                    + "(?:Item\\.of\\s*\\(\\s*)?(?:get\\w+|randomGet)\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERACT_IF_THUNDER = Pattern.compile(
            "isThundering\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERACT_IF_STAGE = Pattern.compile(
            "stages\\.has\\(\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERACT_IF_DIM = Pattern.compile(
            "(?:\\.dimension(?:Key)?|getDimension(?:Key)?)\\s*"
                    + "(?:[=!]=|\\.equals\\(|\\.toString\\s*\\(\\s*\\)\\s*[=!]=|[\\s\\S]{0,40}?)"
                    + "['\"]([a-z0-9_.:/-]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERACT_IF_NIGHT = Pattern.compile(
            "(?:\\.isNight\\s*\\(|!\\s*[\\w.]*\\.isDay\\s*\\()", Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERACT_IF_NBT = Pattern.compile(
            "(?:persistentData\\.(?:get|contains|put)|getOrCreateTag\\s*\\(|\\.nbt\\.|hasNBT\\s*\\()",
            Pattern.CASE_INSENSITIVE);
    /**
     * KubeJS {@code event.create('id')} / {@code create('ns:id')} (startup registry).
     * Bare ids resolve to {@code kubejs:id}.
     */
    private static final Pattern ITEM_CREATE = Pattern.compile(
            "\\.create\\(\\s*['\"]([a-z0-9_.:/-]+)['\"]\\s*\\)", Pattern.CASE_INSENSITIVE);
    /** Use / finishUsing hooks on a create(…) chain (hold-right-click items). */
    private static final Pattern CREATE_USE_HOOK = Pattern.compile(
            "\\.(finishUsing|useDuration|use)\\s*\\(", Pattern.CASE_INSENSITIVE);
    /** {@code food(...).eaten(...)} on create chain → script_use PURPOSE. */
    private static final Pattern CREATE_FOOD_EATEN = Pattern.compile(
            "\\.eaten\\s*\\(", Pattern.CASE_INSENSITIVE);
    /** Any getXxx / randomXxx helper call in create-use chain (not item-specific). */
    private static final Pattern CREATE_RANDOM_CALL = Pattern.compile(
            "(?:global\\.)?((?:get|random)\\w+)\\s*\\(", Pattern.CASE_INSENSITIVE);
    /** LootJS modifiers → acquire (not PURPOSE). */
    private static final Pattern LOOTJS_MARK = Pattern.compile(
            "LootJS\\.modifiers\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOOTJS_ENTITY_MOD = Pattern.compile(
            "\\.addEntityLootModifier\\s*\\(\\s*['\"]([a-z0-9_.:/-]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOOTJS_TABLE_MOD = Pattern.compile(
            "\\.addLootTableModifier\\s*\\(\\s*['\"]([a-z0-9_.:/-]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOOTJS_ENTRY = Pattern.compile(
            "(?:LootEntry\\.of|\\.addLoot|addLoot)\\s*\\(\\s*['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
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
    /** FTB file {@code default_consume_items}; null = missing / ambiguous. */
    private Boolean fileDefaultConsumeItems;
    /** FTB file-level hide-details default (rare); null = absent. */
    private Boolean fileDefaultHideDetailsUntilStartable;
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
        fileDefaultConsumeItems = null;
        fileDefaultHideDetailsUntilStartable = null;
        RecipeUnlockGates.clearKubeJsGates();
        this.root = gameDir;
        if (gameDir == null || !Files.isDirectory(gameDir)) {
            return;
        }
        loadFileDefaultConsumeItems(gameDir);
        loadFileDefaultHideDetails(gameDir);
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
                        } else if (isAcquirePath(pl) || isGatewayPath(pl)) {
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
        // Build-time: create().finishUsing / .use / food().eaten give facts survive beginAskSession.
        ItemDescFacts.mergeInto(descByItem, parseItemCreateUseFacts(text));
        // #1C: advancement cancel/ritual heuristic → RecipeUnlockGates (not PackIndex dump).
        RecipeUnlockGates.ingestKubeJs(text);
        // #5: LootJS acquire edges — also pin acquirePaths so Ask re-ingests this script.
        for (String fact : parseLootJsFacts(text)) {
            addFact(fact);
            pinLootJsAcquirePath(rel, fact);
        }
        // #5b: Gateways + loot JSON forward index
        for (String fact : LootForwardIndex.parseFacts(rel, text)) {
            addFact(fact);
            pinLootContainsAcquirePath(rel, fact);
        }
    }

    /** Map lootjs fact item id → script rel for acquireFactsFor. */
    private void pinLootJsAcquirePath(String rel, String fact) {
        if (rel == null || fact == null || !fact.contains(" -[loot]-> ")) {
            return;
        }
        int start = fact.startsWith("item:") ? 5 : -1;
        int end = fact.indexOf(" -[loot]-> ");
        if (start < 0 || end <= start) {
            return;
        }
        String id = fact.substring(start, end).toLowerCase(Locale.ROOT).trim();
        if (id.isEmpty() || isNoiseItemId(id)) {
            return;
        }
        List<String> paths = acquirePathsByItem.computeIfAbsent(id, k -> new ArrayList<>());
        if (!paths.contains(rel)) {
            paths.add(rel);
        }
    }

    /** Pin item←loot_table / gateway stack reward edges for acquire retrieve. */
    private void pinLootContainsAcquirePath(String rel, String fact) {
        if (rel == null || fact == null || !fact.startsWith("item:")) {
            return;
        }
        // #5b: loot JSON contains OR gateway stack/stack_list reward
        if (!fact.contains(" -[loot]-> table:") && !fact.contains(" -[loot]-> gateway:")) {
            return;
        }
        int end = fact.indexOf(" -[loot]-> ");
        if (end <= 5) {
            return;
        }
        String id = fact.substring(5, end).toLowerCase(Locale.ROOT).trim();
        if (id.isEmpty() || isNoiseItemId(id)) {
            return;
        }
        List<String> paths = acquirePathsByItem.computeIfAbsent(id, k -> new ArrayList<>());
        if (!paths.contains(rel)) {
            paths.add(rel);
        }
    }

    /**
     * Drop session-accumulated graph edges / removed markers so a prior Ask (or a brief
     * show-hidden=on window) cannot leak spoiler quest titles into later answers.
     * Path indexes stay; call once at the start of each Ask.
     */
    public void beginAskSession() {
        graphFacts.clear();
        removedItems.clear();
    }

    /**
     * Ask-time grounding retrieve — on-demand for the focused item (and its {@code ns:} namespace),
     * not a full pack/script dump into the prompt. PURPOSE asks keep nearby kubejs/script clips when
     * purpose facts are thin; craft/acquire prefers related graph facts. Jar zip facts are filtered
     * separately by held item id in {@link JarLightIndex#factsForAsk}.
     */
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
        Set<String> seeds = seedItemIds(heldItemId, extraItemIds, tokens);
        boolean seedAsk = !seeds.isEmpty();

        Set<Integer> cand = new HashSet<>();
        for (String t : tokens) {
            List<Integer> ids = inverted.get(t);
            if (ids != null) {
                cand.addAll(ids);
            }
        }
        // With a concrete item seed, do not expand by focus mod id alone (would pull every kubejs/* path).
        if (!seedAsk) {
            for (String mid : focusMods) {
                List<Integer> ids = inverted.get(mid.toLowerCase(Locale.ROOT));
                if (ids != null) {
                    cand.addAll(ids);
                }
            }
        }
        if (cand.isEmpty() && !seedAsk) {
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
            for (String seed : seeds) {
                if (pathHintsSeed(pl, seed)) {
                    score += 5;
                }
            }
            if (!seedAsk) {
                for (String mid : focusMods) {
                    if (pl.contains(mid.toLowerCase(Locale.ROOT))) {
                        score += 3;
                    }
                }
            } else {
                // Soft namespace boost for non-script trees only (datapacks/overrides under that mod).
                String heldNs = namespaceOf(heldItemId);
                if (heldNs != null && !isPackScriptTree(pl) && pl.contains(heldNs)) {
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
            if (text == null) {
                continue;
            }
            String plLower = rel.toLowerCase(Locale.ROOT);
            if (!QuestGuide.showHiddenQuestsConfig()
                    && (plLower.contains("ftbquests") || plLower.contains("heracles"))) {
                text = QuestGuide.redactHiddenQuestObjects(text);
            }
            int score = s.score;
            String lower = text.toLowerCase(Locale.ROOT);
            for (String t : tokens) {
                if (lower.contains(t)) {
                    score += 3;
                }
            }
            // Seed ask: only ingest/clip pack scripts that mention a seed item id in the body.
            if (seedAsk && isPackScriptTree(plLower) && !bodyMentionsSeed(lower, seeds)) {
                continue;
            }
            if (score <= 0 && !tokens.isEmpty()) {
                continue;
            }
            read++;
            ingestGraph(rel, text);
            topScore = Math.max(topScore, score);
            // Defer snippets until we know whether related graph facts already cover the ask.
            if (snippets.size() < 3) {
                String clip = clipNearMatch(text, clipNeedles(heldItemId, tokens, extraItemIds),
                        clipRadiusConfig());
                snippets.add("// file: " + rel + "\n" + clip);
                sources.add(rel);
            }
        }
        List<String> related = selectRelatedGraphFacts(seeds, MAX_RETRIEVE_FACTS);
        // Facts-first on craft asks; PURPOSE / thin purpose facts still keep nearby script clips.
        if (shouldSkipSnippets(question, related, seeds)) {
            snippets = List.of();
            sources = List.of();
        }
        boolean high = topScore >= 12 && (!snippets.isEmpty() || !related.isEmpty());
        return new RetrieveResult(snippets, sources, topScore, high, Set.copyOf(removedItems), related);
    }

    /** Config clip radius; default when PackAiConfig not loadable (unit checks). */
    static int clipRadiusConfig() {
        try {
            return PackAiConfig.packIndexClipRadius();
        } catch (Throwable t) {
            return CLIP_LINES_RADIUS_DEFAULT;
        }
    }

    /** True when path looks related to {@code ns:path} (folder / underscored / path part). */
    static boolean pathHintsSeed(String pathLower, String seed) {
        if (pathLower == null || seed == null || seed.isBlank()) {
            return false;
        }
        String s = seed.toLowerCase(Locale.ROOT).trim();
        if (pathLower.contains(s.replace(':', '/'))) {
            return true;
        }
        if (pathLower.contains(s.replace(':', '_'))) {
            return true;
        }
        int c = s.indexOf(':');
        if (c > 0 && c < s.length() - 1) {
            String path = s.substring(c + 1);
            return path.length() >= 3 && pathLower.contains(path);
        }
        return false;
    }

    static String namespaceOf(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        int c = itemId.indexOf(':');
        if (c <= 0) {
            return null;
        }
        return itemId.substring(0, c).toLowerCase(Locale.ROOT);
    }

    static boolean bodyMentionsSeed(String textLower, Set<String> seeds) {
        if (textLower == null || seeds == null || seeds.isEmpty()) {
            return false;
        }
        for (String seed : seeds) {
            if (seed == null || seed.isBlank()) {
                continue;
            }
            String s = seed.toLowerCase(Locale.ROOT).trim();
            if (textLower.contains(s)) {
                return true;
            }
            int c = s.indexOf(':');
            if (c <= 0 || c >= s.length() - 1) {
                continue;
            }
            String path = s.substring(c + 1);
            if (path.length() < 3) {
                continue;
            }
            // KubeJS create('bare') / texture paths — prefer quoted bare id to avoid short false hits.
            if (textLower.contains("'" + path + "'") || textLower.contains("\"" + path + "\"")) {
                return true;
            }
            // Long bare paths (e.g. random_delivery_agreement) may appear unquoted in comments.
            if (path.length() >= 8 && textLower.contains(path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clip around first needle hit (± default radius lines, capped ~{@link #CLIP_MAX_CHARS}).
     * Falls back to file head only when no needle matches.
     */
    static String clipNearMatch(String text, List<String> needles) {
        return clipNearMatch(text, needles, CLIP_LINES_RADIUS_DEFAULT);
    }

    /**
     * Clip around first needle hit (± {@code lineRadius} lines, capped ~{@link #CLIP_MAX_CHARS}).
     * Radius clamped 5–100. Falls back to file head only when no needle matches.
     */
    static String clipNearMatch(String text, List<String> needles, int lineRadius) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int radius = Math.max(5, Math.min(100, lineRadius));
        int hit = findEarliestNeedle(text, needles);
        if (hit < 0) {
            return text.length() > CLIP_MAX_CHARS ? text.substring(0, CLIP_MAX_CHARS) : text;
        }
        int lineStart = 0;
        int before = 0;
        for (int i = hit - 1; i >= 0; i--) {
            if (text.charAt(i) == '\n') {
                before++;
                if (before > radius) {
                    lineStart = i + 1;
                    break;
                }
            }
        }
        int lineEnd = text.length();
        int after = 0;
        for (int i = hit; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                after++;
                if (after > radius) {
                    lineEnd = i;
                    break;
                }
            }
        }
        String clip = text.substring(lineStart, lineEnd);
        if (clip.length() <= CLIP_MAX_CHARS) {
            return clip;
        }
        int local = Math.max(0, hit - lineStart);
        int half = CLIP_MAX_CHARS / 2;
        int from = Math.max(0, local - half);
        int to = Math.min(clip.length(), from + CLIP_MAX_CHARS);
        from = Math.max(0, to - CLIP_MAX_CHARS);
        return clip.substring(from, to);
    }

    private static int findEarliestNeedle(String text, List<String> needles) {
        if (needles == null || needles.isEmpty()) {
            return -1;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int best = -1;
        for (String n : needles) {
            if (n == null || n.length() < 2) {
                continue;
            }
            int i = lower.indexOf(n.toLowerCase(Locale.ROOT));
            if (i >= 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }

    /** Prefer full item ids, bare path, then long hint tokens. */
    static List<String> clipNeedles(String heldItemId, List<String> tokens, List<String> extraItemIds) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (heldItemId != null && !heldItemId.isBlank()) {
            String held = heldItemId.trim().toLowerCase(Locale.ROOT);
            out.add(held);
            int c = held.indexOf(':');
            if (c > 0 && c < held.length() - 1) {
                String path = held.substring(c + 1);
                if (path.length() >= 3) {
                    out.add(path);
                }
            }
        }
        if (extraItemIds != null) {
            for (String id : extraItemIds) {
                if (id == null || id.isBlank() || id.indexOf(':') <= 0) {
                    continue;
                }
                String full = id.trim().toLowerCase(Locale.ROOT);
                out.add(full);
                int c = full.indexOf(':');
                if (c > 0 && c < full.length() - 1) {
                    String path = full.substring(c + 1);
                    if (path.length() >= 3) {
                        out.add(path);
                    }
                }
            }
        }
        if (tokens != null) {
            for (String t : tokens) {
                if (t == null || t.length() < 3) {
                    continue;
                }
                out.add(t.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(out);
    }

    /**
     * Skip raw clips when craft/acquire facts already cover the ask, or when purpose-related
     * facts already cover the seed item (desc / right_click / on:). Keep clips for PURPOSE asks
     * with thin purpose facts so KubeJS drink/use logic still reaches the LLM.
     * Non-PURPOSE: need ≥{@link #SNIPPET_SKIP_WHEN_FACTS} related facts, or a craft-oriented
     * ask plus at least one recipe-shaped fact — never wipe clips on a single weak fact.
     */
    static boolean shouldSkipSnippets(String question, List<String> related, Set<String> seeds) {
        if (related == null || related.isEmpty()) {
            return false;
        }
        // Code/script asks need nearby kubejs clips even when graph already has desc/on: facts.
        if (isCodeOrBehaviorQuestion(question)) {
            return false;
        }
        if (purposeFactsCoverSeeds(related, seeds)) {
            return true;
        }
        if (isPurposeQuestion(question)) {
            return false;
        }
        if (related.size() >= SNIPPET_SKIP_WHEN_FACTS) {
            return true;
        }
        return isCraftOrientedQuestion(question) && hasCraftShapedFact(related);
    }

    /**
     * Attach Ask recipe cards / heavy JEI get-section only for craft/how-to-make or
     * acquire/how-to-get asks — not every item Ask (placement / purpose / idle).
     */
    public static boolean shouldAttachAskRecipeCards(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        return isCraftOrientedQuestion(question)
                || isAcquireOrientedQuestion(question)
                || isPurposeQuestion(question);
    }

    /**
     * True when ask is about pack scripts / source / how something works internally
     * (not a craft or how-to-get ask).
     */
    public static boolean isCodeOrBehaviorQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String q = question.toLowerCase(Locale.ROOT);
        if (q.contains("kubejs")
                || q.contains("源码")
                || q.contains("源碼")
                || q.contains("脚本")
                || q.contains("腳本")
                || q.contains("程式")
                || q.contains("程序")
                || q.contains("script")
                || q.contains("原理")
                || q.contains("behavior")
                || q.contains("行為")
                || q.contains("行为")
                || q.contains("how it works")
                || q.contains("how this works")
                || q.contains("怎么工作")
                || q.contains("怎麼工作")
                || (q.contains("how does") && q.contains("work"))) {
            return true;
        }
        if (q.contains("code")
                && (q.contains("check")
                        || q.contains("read")
                        || q.contains("看")
                        || q.contains("查")
                        || q.contains("讀")
                        || q.contains("读"))) {
            return true;
        }
        return q.contains("代碼") || q.contains("代码");
    }

    /** True when ask looks like how-to-get / obtain / acquire. */
    static boolean isAcquireOrientedQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String q = question.toLowerCase(Locale.ROOT);
        return q.contains("如何取得")
                || q.contains("怎麼取得")
                || q.contains("怎么取得")
                || q.contains("取得方式")
                || q.contains("获得方式")
                || q.contains("獲得方式")
                || q.contains("如何獲得")
                || q.contains("如何获得")
                || q.contains("怎麼獲得")
                || q.contains("怎么获得")
                || q.contains("怎麼來")
                || q.contains("怎么来")
                || q.contains("如何得到")
                || q.contains("怎麼得到")
                || q.contains("怎么得到")
                || q.contains("how to get")
                || q.contains("how do i get")
                || q.contains("where to get")
                || q.contains("where can i get")
                || q.contains("how to obtain")
                || q.contains("obtain");
    }

    /** True when ask looks like craft / how-to-make / recipe (not PURPOSE). */
    static boolean isCraftOrientedQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String q = question.toLowerCase(Locale.ROOT);
        return q.contains("如何做")
                || q.contains("怎麼做")
                || q.contains("怎么做")
                || q.contains("怎麼合成")
                || q.contains("怎么合成")
                || q.contains("如何合成")
                || q.contains("如何製作")
                || q.contains("如何制作")
                || q.contains("配方")
                || q.contains("合成")
                || q.contains("製作")
                || q.contains("制作")
                || q.contains("how to make")
                || q.contains("how to craft")
                || q.contains("how do i craft")
                || q.contains("how do i make")
                || q.contains("craft ")
                || q.contains(" crafting")
                || q.contains("recipe");
    }

    /** Graph edges that look like craft inputs / recipe coverage. */
    static boolean hasCraftShapedFact(List<String> related) {
        if (related == null) {
            return false;
        }
        for (String f : related) {
            if (f != null && f.contains("-[recipe_needs]->")) {
                return true;
            }
        }
        return false;
    }

    /** True when a related fact already describes use/purpose for a seed item. */
    static boolean purposeFactsCoverSeeds(List<String> related, Set<String> seeds) {
        if (related == null || seeds == null || seeds.isEmpty()) {
            return false;
        }
        for (String f : related) {
            if (f == null || f.isBlank()) {
                continue;
            }
            boolean strong = f.contains("-[desc]->")
                    || f.contains("-[right_click")
                    || f.contains("-[on:");
            if (!strong) {
                continue;
            }
            for (String seed : seeds) {
                if (seed != null && !seed.isBlank() && f.contains(seed)) {
                    return true;
                }
            }
        }
        return false;
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

    /** Machine / automate asks — used to prioritize Machine brief in AskEngine. */
    public static boolean isMachineQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String q = question.toLowerCase(Locale.ROOT);
        if (q.contains("automate")
                || q.contains("automation")
                || q.contains("hopper")
                || q.contains("自動化")
                || q.contains("自动化")
                || q.contains("漏斗")
                || q.contains("這機")
                || q.contains("这机")
                || q.contains("機器怎")
                || q.contains("机器怎")) {
            return true;
        }
        if (q.contains("machine") && (q.contains("how") || q.contains("use") || q.contains("auto") || q.contains("work"))) {
            return true;
        }
        return q.contains("怎麼自動") || q.contains("怎么自动") || q.contains("如何自動") || q.contains("如何自动");
    }

    /**
     * Local non-JEI acquire hints for an item (loot / trade / script recipe).
     * Call after {@link #retrieve} so script recipes are also ingested.
     */
    public List<String> acquireFactsFor(String itemId) {
        return acquireFactsFor(itemId, "zh_tw");
    }

    public List<String> acquireFactsFor(String itemId, String replyLang) {
        return acquireFactsFor(itemId, replyLang, List.of());
    }

    /**
     * @param variantTokens schematic / distinctive-name tokens from held NBT. When non-empty,
     *                      only keep quest_submit/obtain edges whose <em>task slice</em> mentions
     *                      a token (strict — no soft fallback to bare-id siblings).
     */
    public List<String> acquireFactsFor(String itemId, String replyLang, List<String> variantTokens) {
        return acquireFactsDetailed(itemId, replyLang, variantTokens).lines();
    }

    /**
     * Ranked acquire lines plus the raw {@code item:… -[fish|loot|trade|removed]-> …} graph edges
     * that actually entered the ~12 ranked list (overflow stays out so AskEngine can graphLines them).
     */
    public record AcquireFacts(List<String> lines, Set<String> rankedSkipEdges) {
        public AcquireFacts {
            lines = lines == null ? List.of() : List.copyOf(lines);
            rankedSkipEdges = rankedSkipEdges == null || rankedSkipEdges.isEmpty()
                    ? Set.of()
                    : Set.copyOf(rankedSkipEdges);
        }

        public static AcquireFacts empty() {
            return new AcquireFacts(List.of(), Set.of());
        }
    }

    public AcquireFacts acquireFactsDetailed(String itemId, String replyLang, List<String> variantTokens) {
        if (itemId == null || itemId.isBlank()) {
            return AcquireFacts.empty();
        }
        String lang = replyLang == null || replyLang.isBlank() ? "zh_tw" : replyLang.trim();
        String id = itemId.toLowerCase(Locale.ROOT).trim();
        List<String> tokens = variantTokens == null || variantTokens.isEmpty()
                ? List.of()
                : List.copyOf(variantTokens);
        boolean strictVariant = !tokens.isEmpty();
        List<String> rels = acquirePathsByItem.getOrDefault(id, List.of());
        int n = 0;
        for (String rel : rels) {
            if (n >= 10) {
                break;
            }
            String text = readTextForGraph(rel);
            if (text != null) {
                ingestGraph(rel, text);
                n++;
            }
        }
        String prefix = "item:" + id;
        String submitPref = prefix + " -[quest_submit]-> ";
        String obtainPref = prefix + " -[quest_obtain]-> ";
        boolean hadIdQuestEdge = false;
        if (strictVariant) {
            // Drop bare-id sibling edges ingested earlier; re-pin with strict task filter.
            for (int i = graphFacts.size() - 1; i >= 0; i--) {
                String f = graphFacts.get(i);
                if (f.startsWith(submitPref) || f.startsWith(obtainPref)) {
                    hadIdQuestEdge = true;
                    graphFacts.remove(i);
                }
            }
        }
        // MAX_GRAPH may already be full from retrieve(); still pin focus item's quest edge.
        ensureFocusQuestAcquireEdges(id, rels, tokens);

        List<RankedAcquire> ranked = new ArrayList<>();
        List<String> cycles = new ArrayList<>();
        Set<String> rankedSkipEdges = new LinkedHashSet<>();
        boolean keptQuestEdge = false;
        Map<String, Set<String>> recipeNeeds = recipeNeedsIndex();
        int seq = 0;
        for (String f : graphFacts) {
            if (ranked.size() + cycles.size() >= 12) {
                break;
            }
            if (f.startsWith(prefix + " -[fish]-> ")) {
                ranked.add(new RankedAcquire(0, seq++,
                        ReplyLang.fishing(lang) + f.substring((prefix + " -[fish]-> ").length())));
                rankedSkipEdges.add(f);
            } else if (f.startsWith(prefix + " -[loot]-> ")) {
                String rest = f.substring((prefix + " -[loot]-> ").length());
                if (rest.startsWith("gateway:")) {
                    String gw = rest.substring("gateway:".length());
                    String pearl = Plainify.pearlEmbedForGateway(gw, graphFacts);
                    if (pearl.isEmpty()) {
                        pearl = Plainify.gatePearlEmbed(gw);
                    }
                    // Pearl (opens challenge) leads — not the reward organ icon.
                    String line = pearl.isEmpty()
                            ? ReplyLang.gatewayRewardObtain(lang, gw)
                            : pearl + " " + ReplyLang.gatewayRewardObtain(lang, gw);
                    ranked.add(new RankedAcquire(1, seq++, line));
                } else if (rest.startsWith("table:")) {
                    String table = rest.substring("table:".length());
                    if (!LootForwardIndex.isTrivialBlockSelfLoot(id, table)) {
                        ranked.add(new RankedAcquire(1, seq++,
                                ReplyLang.lootTableObtain(lang, table)));
                    }
                } else if (rest.startsWith("entity:")) {
                    String ent = rest.substring("entity:".length());
                    ranked.add(new RankedAcquire(1, seq++,
                            ReplyLang.entityLootObtain(lang, ent, Plainify.displayName(ent))));
                } else {
                    ranked.add(new RankedAcquire(1, seq++, ReplyLang.loot(lang) + rest));
                }
                rankedSkipEdges.add(f);
            } else if (f.startsWith(prefix + " -[trade]-> ")) {
                ranked.add(new RankedAcquire(3, seq++,
                        ReplyLang.trade(lang) + f.substring((prefix + " -[trade]-> ").length())));
                rankedSkipEdges.add(f);
            } else if (f.startsWith(submitPref)) {
                String rest = f.substring(submitPref.length());
                boolean repeat = hasQuestRepeatMark(rest);
                ranked.add(new RankedAcquire(repeat ? 5 : 6, seq++,
                        ReplyLang.questSubmit(lang) + stripQuestRepeatMark(rest)));
                keptQuestEdge = true;
            } else if (f.startsWith(obtainPref)) {
                String rest = f.substring(obtainPref.length());
                boolean repeat = hasQuestRepeatMark(rest);
                ranked.add(new RankedAcquire(repeat ? 5 : 6, seq++,
                        ReplyLang.questObtain(lang) + stripQuestRepeatMark(rest)));
                keptQuestEdge = true;
            } else if (f.startsWith(prefix + " -[recipe_needs]-> ")) {
                String need = f.substring((prefix + " -[recipe_needs]-> ").length()).replace("item:", "");
                if (isCompactCycle(id, need, recipeNeeds)) {
                    if (cycles.size() < 3) {
                        cycles.add(ReplyLang.compactCycle(lang, Plainify.displayName(need)));
                    }
                } else {
                    ranked.add(new RankedAcquire(4, seq++,
                            ReplyLang.scriptNeeds(lang, Plainify.displayName(need))));
                }
            } else if (f.startsWith(prefix + " -[removed]-> ")) {
                ranked.add(new RankedAcquire(4, seq++, ReplyLang.scriptRemoved(lang)));
                rankedSkipEdges.add(f);
            } else if (f.startsWith(prefix + " -[right_click]-> ")) {
                String rest = f.substring((prefix + " -[right_click]-> ").length());
                String held = afterKey(rest, "held:");
                String target = interactTarget(rest);
                String via = afterKey(rest, "via:");
                if (target != null) {
                    ranked.add(new RankedAcquire(2, seq++, ReplyLang.interactGet(
                            lang,
                            held == null || "_".equals(held) ? null : Plainify.displayName(held),
                            Plainify.displayName(target),
                            via)));
                }
            } else if (f.startsWith(prefix + " -[right_click_use]-> ")) {
                String rest = f.substring((prefix + " -[right_click_use]-> ").length());
                String target = interactTarget(rest);
                String gets = afterKey(rest, "gets:");
                String via = afterKey(rest, "via:");
                String getsLabel = ReplyLang.getsResultLabel(lang, gets, null);
                if (target != null && gets != null && !"_".equals(target)) {
                    ranked.add(new RankedAcquire(2, seq++, ReplyLang.interactUse(
                            lang, Plainify.displayName(target), getsLabel, via)));
                } else if (gets != null) {
                    ranked.add(new RankedAcquire(2, seq++,
                            ReplyLang.interactUseSelf(lang, getsLabel, via)));
                }
            } else if (f.startsWith(prefix + " -[right_click_as_block]-> ")) {
                String rest = f.substring((prefix + " -[right_click_as_block]-> ").length());
                String held = afterKey(rest, "held:");
                String gets = afterKey(rest, "gets:");
                String via = afterKey(rest, "via:");
                if (gets != null) {
                    ranked.add(new RankedAcquire(2, seq++, ReplyLang.interactAsTarget(
                            lang,
                            held == null || "_".equals(held) ? null : Plainify.displayName(held),
                            ReplyLang.getsResultLabel(lang, gets, null),
                            via)));
                }
            }
        }
        if (strictVariant && !keptQuestEdge
                && (hadIdQuestEdge || hasFocusQuestAcquire(id, rels))) {
            ranked.add(new RankedAcquire(7, seq++, ReplyLang.questVariantUnmatchedCaution(lang)));
        }
        if (ranked.isEmpty() && cycles.isEmpty()) {
            return AcquireFacts.empty();
        }
        ranked.sort(Comparator.comparingInt(RankedAcquire::band).thenComparingInt(RankedAcquire::seq));
        List<String> labeled = new ArrayList<>();
        labeled.add(ReplyLang.localAcquireHeader(lang, Plainify.displayName(id)));
        for (RankedAcquire r : ranked) {
            labeled.add(r.line());
        }
        labeled.addAll(cycles);
        return new AcquireFacts(labeled, rankedSkipEdges);
    }

    /** Ease band for local acquire lines: fish → loot → interact → trade → script → quest(repeat) → quest(once). */
    private record RankedAcquire(int band, int seq, String line) {}

    static boolean hasQuestRepeatMark(String title) {
        return title != null && title.startsWith(QUEST_REPEAT_MARK);
    }

    static String stripQuestRepeatMark(String title) {
        if (hasQuestRepeatMark(title)) {
            return title.substring(QUEST_REPEAT_MARK.length());
        }
        return title == null ? "" : title;
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
        // Bare create('foo') → kubejs:foo (startup registry convention).
        Matcher cm = ITEM_CREATE.matcher(text);
        while (cm.find() && seen.size() < 160) {
            String raw = cm.group(1).toLowerCase(Locale.ROOT);
            if (raw.indexOf('/') >= 0) {
                continue;
            }
            String id = resolveCreateItemId(raw);
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
        } else if (isQuestPath(pl)) {
            ingestQuestAcquireEdges(rel, text);
        }
        if (isScriptPath(pl)) {
            ingestRightClickInteractions(text);
            ItemDescFacts.mergeInto(descByItem, ItemDescFacts.parse(text, translations::get));
            RecipeUnlockGates.ingestKubeJs(text);
            for (String fact : LootForwardIndex.parseFacts(rel, text)) {
                addFact(fact);
            }
        } else if (isLootTablePath(pl) || isGatewayPath(pl)) {
            for (String fact : LootForwardIndex.parseFacts(rel, text)) {
                addFact(fact);
            }
        }
    }

    static boolean isLootTablePath(String pathLower) {
        if (pathLower == null) {
            return false;
        }
        return pathLower.contains("/loot_tables/") || pathLower.contains("/loot_table/");
    }

    static boolean isGatewayPath(String pathLower) {
        return pathLower != null && pathLower.contains("/gateways/");
    }

    /**
     * Parse KubeJS / legacy interaction scripts into graph facts.
     * Covers right/left click, break, entity interact, food eaten, onEvent('…'),
     * and item {@code create(…).finishUsing / .use} chains.
     */
    void ingestRightClickInteractions(String text) {
        for (String fact : parseRightClickFacts(text)) {
            addFact(fact);
        }
        for (String fact : parseItemCreateUseFacts(text)) {
            addFact(fact);
        }
        for (String fact : parseLootJsFacts(text)) {
            addFact(fact);
        }
    }

    /**
     * KubeJS startup {@code event.create('id').…finishUsing / .use} — hold-use items that
     * give loot without {@code ItemEvents.rightClicked}. Bare create ids → {@code kubejs:id}.
     * Uses {@code -[script_use]->} so PURPOSE still keeps nearby clips (not treated as covering
     * {@code right_click}/{@code on:} purpose facts).
     */
    static List<String> parseItemCreateUseFacts(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        Matcher m = ITEM_CREATE.matcher(text);
        int guard = 0;
        while (m.find() && guard++ < 120) {
            String raw = m.group(1).toLowerCase(Locale.ROOT);
            if (raw.indexOf('/') >= 0) {
                continue;
            }
            String itemId = resolveCreateItemId(raw);
            if (isNoiseItemId(itemId)) {
                continue;
            }
            int from = m.end();
            int to = Math.min(text.length(), from + 2500);
            Matcher next = ITEM_CREATE.matcher(text);
            if (next.find(from) && next.start() < to) {
                to = next.start();
            }
            String chain = text.substring(from, to);
            boolean foodEaten = chain.toLowerCase(Locale.ROOT).contains(".food")
                    && CREATE_FOOD_EATEN.matcher(chain).find();
            boolean useHook = CREATE_USE_HOOK.matcher(chain).find();
            if (!useHook && !foodEaten) {
                continue;
            }
            boolean finish = chain.toLowerCase(Locale.ROOT).contains(".finishusing");
            String via;
            if (foodEaten && !finish) {
                via = "food_eaten";
            } else if (finish) {
                via = "finish_using";
            } else {
                via = "use";
            }
            LinkedHashSet<String> results = collectInteractResults(chain, itemId, null);
            Matcher randM = CREATE_RANDOM_CALL.matcher(chain);
            String randomCall = null;
            if (randM.find()) {
                // Keep source spelling (getRandomWare); pattern is case-insensitive.
                randomCall = randM.group(1);
            }
            boolean dynamic = randomCall != null || INTERACT_DYNAMIC_DROP.matcher(chain).find();
            if (results.isEmpty() && !dynamic) {
                if (foodEaten) {
                    // Eaten with effect-only (no give) still PURPOSE.
                    out.add("item:" + itemId + " -[script_use]-> via:food_eaten");
                    continue;
                }
                // Hold-use with no give / random — skip (e.g. ceremonial knife damage-only).
                if (!chain.toLowerCase(Locale.ROOT).contains(".give")
                        && !chain.toLowerCase(Locale.ROOT).contains("additem")
                        && !chain.toLowerCase(Locale.ROOT).contains("giveinhand")) {
                    continue;
                }
                dynamic = true;
            }
            if (results.isEmpty()) {
                StringBuilder fact = new StringBuilder("item:")
                        .append(itemId)
                        .append(" -[script_use]-> via:")
                        .append(via)
                        .append(" + gets:random");
                if (randomCall != null) {
                    fact.append(" + call:").append(randomCall);
                }
                out.add(fact.toString());
            } else {
                for (String result : results) {
                    if (result.equals(itemId)) {
                        continue;
                    }
                    out.add("item:" + itemId + " -[script_use]-> via:" + via + " + gets:" + result);
                }
            }
        }
        return List.copyOf(out);
    }

    /**
     * LootJS {@code LootJS.modifiers} → acquire {@code -[loot]->} facts (not PURPOSE).
     * Literal item ids only; never invents organ/mutation stories from apply callbacks.
     */
    static List<String> parseLootJsFacts(String text) {
        if (text == null || text.isBlank() || !LOOTJS_MARK.matcher(text).find()) {
            return List.of();
        }
        record Hit(int start, String kind, String value) {}
        List<Hit> hits = new ArrayList<>();
        Matcher em = LOOTJS_ENTITY_MOD.matcher(text);
        while (em.find()) {
            hits.add(new Hit(em.start(), "entity", em.group(1).toLowerCase(Locale.ROOT)));
        }
        Matcher tm = LOOTJS_TABLE_MOD.matcher(text);
        while (tm.find()) {
            hits.add(new Hit(tm.start(), "table", tm.group(1).toLowerCase(Locale.ROOT)));
        }
        Matcher lm = LOOTJS_ENTRY.matcher(text);
        while (lm.find()) {
            hits.add(new Hit(lm.start(), "item", lm.group(1).toLowerCase(Locale.ROOT)));
        }
        hits.sort((a, b) -> Integer.compare(a.start(), b.start()));
        List<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        String currentSource = "";
        for (Hit h : hits) {
            if ("entity".equals(h.kind()) || "table".equals(h.kind())) {
                currentSource = h.kind() + ":" + h.value();
                continue;
            }
            if (isNoiseItemId(h.value())) {
                continue;
            }
            StringBuilder fact = new StringBuilder("item:")
                    .append(h.value())
                    .append(" -[loot]-> via:lootjs");
            if (!currentSource.isEmpty()) {
                fact.append(" + ").append(currentSource);
            }
            String line = fact.toString();
            if (seen.add(line)) {
                out.add(line);
            }
            if (out.size() >= 80) {
                break;
            }
        }
        return List.copyOf(out);
    }

    /** Bare {@code create('foo')} → {@code kubejs:foo}; already-namespaced ids unchanged. */
    static String resolveCreateItemId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return "";
        }
        String s = rawId.toLowerCase(Locale.ROOT).trim();
        if (s.indexOf(':') > 0) {
            return s;
        }
        return "kubejs:" + s;
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
            if (held == null) {
                Matcher tagM = INTERACT_HELD_TAG.matcher(body);
                if (tagM.find()) {
                    held = "#" + tagM.group(1).toLowerCase(Locale.ROOT);
                }
            }
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
            boolean dynamic = INTERACT_DYNAMIC_DROP.matcher(body).find();
            if (results.isEmpty() && dynamic && (target != null || kind.filterId() != null)) {
                results.add("random");
            }
            if (results.isEmpty()) {
                // Narrow tick: only keep handlers that actually give/effect something
                continue;
            }
            String via = kind.via();
            String cond = interactConditions(body);
            for (String result : results) {
                if (result.equals(held)) {
                    continue;
                }
                // Breaking a block into itself (e.g. silk spawner) is a valid obtain path.
                if (result.equals(target) && !"break".equals(via)) {
                    continue;
                }
                String suffix = cond.isEmpty() ? "" : " + " + cond;
                if ("random".equals(result)) {
                    String dropTarget = target != null ? target : kind.filterId();
                    if (dropTarget != null) {
                        out.add("item:" + dropTarget + " -[drops]-> random + via:" + via + suffix);
                    }
                    // PURPOSE needs right_click_use / as_block — drops alone is filtered out.
                    String useItem = held != null ? held
                            : (kind.preferItemFilter() ? kind.filterId() : null);
                    if (useItem != null) {
                        if (target != null) {
                            out.add("item:" + useItem + " -[right_click_use]-> " + targetKey + ":"
                                    + target + " + gets:random + via:" + via + suffix);
                        } else {
                            out.add("item:" + useItem + " -[right_click_use]-> block:_ + gets:random + via:"
                                    + via + suffix);
                        }
                    } else if (target != null) {
                        out.add("item:" + target + " -[right_click_as_block]-> held:_ + gets:random + via:"
                                + via + suffix);
                    }
                    continue;
                }
                if (target != null && held != null) {
                    out.add("item:" + result + " -[right_click]-> held:" + held
                            + " + " + targetKey + ":" + target + " + via:" + via + suffix);
                    out.add("item:" + held + " -[right_click_use]-> " + targetKey + ":" + target
                            + " + gets:" + result + " + via:" + via + suffix);
                    out.add("item:" + target + " -[right_click_as_block]-> held:" + held
                            + " + gets:" + result + " + via:" + via + suffix);
                } else if (target != null) {
                    out.add("item:" + result + " -[right_click]-> held:_ + " + targetKey + ":" + target
                            + " + via:" + via + suffix);
                    out.add("item:" + target + " -[right_click_as_block]-> held:_ + gets:" + result
                            + " + via:" + via + suffix);
                } else if (held != null) {
                    out.add("item:" + result + " -[right_click]-> held:" + held + " + block:_ + via:" + via
                            + suffix);
                    out.add("item:" + held + " -[right_click_use]-> block:_ + gets:" + result + " + via:"
                            + via + suffix);
                } else {
                    // e.g. PlayerEvents.tick give with no held/block filter
                    out.add("item:" + result + " -[right_click]-> held:_ + block:_ + via:" + via + suffix);
                }
            }
        }
        return List.copyOf(out);
    }

    private static String interactConditions(String body) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        if (INTERACT_IF_THUNDER.matcher(body).find()) {
            parts.add("if:thunder");
        }
        Matcher sm = INTERACT_IF_STAGE.matcher(body);
        int n = 0;
        while (sm.find() && n++ < 2) {
            parts.add("if:stage:" + sm.group(1).toLowerCase(Locale.ROOT));
        }
        Matcher dm = INTERACT_IF_DIM.matcher(body);
        int d = 0;
        while (dm.find() && d++ < 2) {
            parts.add("if:dim:" + dm.group(1).toLowerCase(Locale.ROOT));
        }
        if (INTERACT_IF_NIGHT.matcher(body).find()) {
            parts.add("if:night");
        }
        if (INTERACT_IF_NBT.matcher(body).find()) {
            parts.add("if:nbt");
        }
        return String.join(" + ", parts);
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
            // PlayerEvents.tick / EntityEvents.tick — narrow: only bodies with give survive later
            return new InteractKind("tick", null, false, false, false);
        }
        if (m.group(9) != null) {
            String ev = m.group(9).toLowerCase(Locale.ROOT);
            String via = switch (ev) {
                case "block.left_click" -> "left_click";
                case "block.break" -> "break";
                case "item.entity_interact" -> "entity";
                case "item.food_eaten" -> "food";
                case "player.tick" -> "tick";
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
        String label = ReplyLang.humanAcquireLabel("zh_tw", rel, kind);
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

    /**
     * FTB item tasks only: emit quest_submit / quest_obtain when consume resolve is definite
     * and the quest is OK to advertise (anti-spoiler). Ambiguous consume → emit nothing.
     * Label = visible quest title (required); no title → no edge.
     */
    private void ingestQuestAcquireEdges(String rel, String text) {
        emitQuestAcquireEdges(rel, text, null, false, List.of());
    }

    /**
     * After retrieve() may have filled {@link #MAX_GRAPH}, still emit submit/obtain for the
     * asked item (bypass cap). Prefer null when resolve is ambiguous — never invent submit.
     *
     * @param variantTokens when non-empty, task slice must mention a token (strict)
     */
    private void ensureFocusQuestAcquireEdges(String itemId, List<String> rels, List<String> variantTokens) {
        if (itemId == null || itemId.isBlank() || rels == null || rels.isEmpty()) {
            return;
        }
        String id = itemId.toLowerCase(Locale.ROOT).trim();
        List<String> tokens = variantTokens == null ? List.of() : variantTokens;
        int n = 0;
        for (String rel : rels) {
            if (n >= 10) {
                break;
            }
            if (rel == null) {
                continue;
            }
            String pl = rel.toLowerCase(Locale.ROOT);
            if (!isQuestPath(pl)) {
                continue;
            }
            String text = readTextForGraph(rel);
            if (text == null || text.isBlank()) {
                continue;
            }
            n++;
            emitQuestAcquireEdges(rel, text, id, true, tokens);
        }
    }

    /** True when focus item has any definite quest_submit/obtain edge ignoring variant filter. */
    private boolean hasFocusQuestAcquire(String itemId, List<String> rels) {
        if (itemId == null || itemId.isBlank() || rels == null || rels.isEmpty()) {
            return false;
        }
        String id = itemId.toLowerCase(Locale.ROOT).trim();
        int n = 0;
        for (String rel : rels) {
            if (n >= 10) {
                break;
            }
            if (rel == null) {
                continue;
            }
            String pl = rel.toLowerCase(Locale.ROOT);
            if (!isQuestPath(pl)) {
                continue;
            }
            String text = readTextForGraph(rel);
            if (text == null || text.isBlank()) {
                continue;
            }
            n++;
            if (emitQuestAcquireEdges(rel, text, id, false, List.of(), true) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Read pack text for graph ingest; when anti-spoiler is on, strip hidden FTB/Heracles
     * quest objects the same way {@link #retrieve} does (acquire path used to ingest raw).
     */
    private String readTextForGraph(String rel) {
        String text = readText(rel);
        if (text == null || text.isBlank()) {
            return text;
        }
        String pl = rel == null ? "" : rel.toLowerCase(Locale.ROOT);
        if (!QuestGuide.showHiddenQuestsConfig()
                && (pl.contains("ftbquests") || pl.contains("heracles"))) {
            return QuestGuide.redactHiddenQuestObjects(text);
        }
        return text;
    }

    /**
     * Walk chapter {@code quests:[]} — skip spoiler/hide-details/deps-gated; require title.
     *
     * @param onlyItemId when non-null, only emit edges for that item
     * @param forced     use {@link #addFactForced} (focus pin past MAX_GRAPH)
     * @param variantTokens when non-empty with {@code onlyItemId}, require task slice mention
     */
    private int emitQuestAcquireEdges(
            String rel, String text, String onlyItemId, boolean forced, List<String> variantTokens
    ) {
        return emitQuestAcquireEdges(rel, text, onlyItemId, forced, variantTokens, false);
    }

    /**
     * @param dryRun count matches only — do not {@link #addFact}/{@link #addFactForced}
     * @return number of edges matched (and added unless dryRun)
     */
    private int emitQuestAcquireEdges(
            String rel,
            String text,
            String onlyItemId,
            boolean forced,
            List<String> variantTokens,
            boolean dryRun
    ) {
        Matcher qm = QUESTS_ARRAY.matcher(text);
        if (!qm.find()) {
            return 0;
        }
        int emitted = 0;
        boolean filterHidden = !QuestGuide.showHiddenQuestsConfig();
        boolean chapterDepsGate = QuestGuide.chapterHidesUntilDepsVisible(text, qm.start());
        Boolean chapterHideDetails = QuestGuide.chapterHideDetailsUntilStartable(text, qm.start());
        Boolean chapterConsume = depth1ExplicitBool(text.substring(0, qm.start()), "consume_items");
        if (chapterConsume == null) {
            // legacy: some packs put consume default only inside quests; keep whole-file depth1
            chapterConsume = depth1ExplicitBool(text, "consume_items");
        }
        List<String> tokens = variantTokens == null ? List.of() : variantTokens;
        boolean strictVariant = onlyItemId != null && !tokens.isEmpty();
        for (int[] qSpan : QuestGuide.topLevelObjects(text, qm.end() - 1)) {
            String questSlice = text.substring(qSpan[0], qSpan[1]);
            if (filterHidden
                    && QuestGuide.shouldSuppressQuestAdvertise(
                            questSlice,
                            chapterHideDetails,
                            chapterDepsGate,
                            fileDefaultHideDetailsUntilStartable)) {
                continue;
            }
            String title = QuestGuide.cleanTitle(QuestGuide.depth1Field(questSlice, "title"));
            if (title.isBlank()) {
                // no player-meaningful title → do not advertise bare 【任務】
                continue;
            }
            Matcher tm = TASKS_ARRAY.matcher(questSlice);
            while (tm.find()) {
                for (int[] tSpan : QuestGuide.topLevelObjects(questSlice, tm.end() - 1)) {
                    String taskSlice = questSlice.substring(tSpan[0], tSpan[1]);
                    if (!TYPE_ITEM.matcher(taskSlice).find()) {
                        continue;
                    }
                    if (onlyItemId != null && !taskMentionsItem(taskSlice, onlyItemId)) {
                        continue;
                    }
                    // Strict Ask acquire: schematic tokens must appear in the task SNBT slice.
                    if (strictVariant && !ItemVariantKeysText.mentionsAny(taskSlice, List.of(), tokens)) {
                        continue;
                    }
                    Boolean taskConsume = depth1ExplicitBool(taskSlice, "consume_items");
                    Boolean resolved = resolveConsume(taskConsume, chapterConsume, fileDefaultConsumeItems);
                    if (resolved == null) {
                        continue;
                    }
                    String kind = resolved ? "quest_submit" : "quest_obtain";
                    String edge = " -[" + kind + "]-> ";
                    boolean canRepeat = QuestGuide.depth1BoolTrue(questSlice, "can_repeat");
                    Matcher im = ITEM.matcher(taskSlice);
                    LinkedHashSet<String> seen = new LinkedHashSet<>();
                    while (im.find() && seen.size() < 40) {
                        String id = im.group(1).toLowerCase(Locale.ROOT);
                        if (isNoiseItemId(id) || !seen.add(id)) {
                            continue;
                        }
                        if (onlyItemId != null && !onlyItemId.equals(id)) {
                            continue;
                        }
                        emitted++;
                        if (dryRun) {
                            continue;
                        }
                        String fact = "item:" + id + edge + (canRepeat ? QUEST_REPEAT_MARK : "") + title;
                        if (forced) {
                            addFactForced(fact);
                        } else {
                            addFact(fact);
                        }
                    }
                }
            }
        }
        return emitted;
    }

    /** True if an item-task slice references {@code itemId} via the ITEM pattern. */
    static boolean taskMentionsItem(String taskSlice, String itemId) {
        if (taskSlice == null || itemId == null || itemId.isBlank()) {
            return false;
        }
        String want = itemId.toLowerCase(Locale.ROOT).trim();
        Matcher im = ITEM.matcher(taskSlice);
        while (im.find()) {
            if (want.equals(im.group(1).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void loadFileDefaultConsumeItems(Path gameDir) {
        fileDefaultConsumeItems = null;
        Path[] candidates = {
                gameDir.resolve("config/ftbquests/quests/data.snbt"),
                gameDir.resolve("config/ftbquests/data.snbt"),
        };
        for (Path p : candidates) {
            if (!Files.isRegularFile(p)) {
                continue;
            }
            try {
                String raw = Files.readString(p);
                Boolean v = parseExplicitBool(raw, "default_consume_items");
                if (v != null) {
                    fileDefaultConsumeItems = v;
                    return;
                }
            } catch (IOException ignored) {
                // try next
            }
        }
    }

    private void loadFileDefaultHideDetails(Path gameDir) {
        fileDefaultHideDetailsUntilStartable = null;
        Path[] candidates = {
                gameDir.resolve("config/ftbquests/quests/data.snbt"),
                gameDir.resolve("config/ftbquests/data.snbt"),
        };
        for (Path p : candidates) {
            if (!Files.isRegularFile(p)) {
                continue;
            }
            try {
                String raw = Files.readString(p);
                Boolean v = parseExplicitBool(raw, "default_hide_quest_details_until_startable");
                if (v == null) {
                    v = parseExplicitBool(raw, "hide_quest_details_until_startable");
                }
                if (v != null) {
                    fileDefaultHideDetailsUntilStartable = v;
                    return;
                }
            } catch (IOException ignored) {
                // try next
            }
        }
    }

    private void addFact(String fact) {
        if (graphFacts.size() >= MAX_GRAPH || graphFacts.contains(fact)) {
            return;
        }
        graphFacts.add(fact);
    }

    /** Bypass {@link #MAX_GRAPH} for focus-item quest edges (still de-dupe). */
    private void addFactForced(String fact) {
        if (fact == null || fact.isBlank() || graphFacts.contains(fact)) {
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
        return isFishingPath(pathLower)
                || isLootPath(pathLower)
                || isTradePath(pathLower)
                || isQuestPath(pathLower);
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

    /** FTB chapter/quest SNBT — not lang, reward tables, or data.snbt. */
    static boolean isQuestPath(String pathLower) {
        if (pathLower == null || !pathLower.contains("ftbquests")) {
            return false;
        }
        String name = pathLower;
        int slash = Math.max(pathLower.lastIndexOf('/'), pathLower.lastIndexOf('\\'));
        if (slash >= 0 && slash < pathLower.length() - 1) {
            name = pathLower.substring(slash + 1);
        }
        if (QuestGuide.isSkippedQuestPath(pathLower, name)) {
            return false;
        }
        if (pathLower.contains("/lang/") || pathLower.contains("\\lang\\")) {
            return false;
        }
        return pathLower.contains("/chapters/")
                || pathLower.contains("\\chapters\\")
                || pathLower.contains("/quests/");
    }

    /**
     * D7 inherit: task explicit → chapter default → file {@code default_consume_items}.
     * Tristate DEFAULT (absent) inherits; all missing → null (do not label submit/obtain).
     */
    static Boolean resolveConsume(Boolean task, Boolean chapter, Boolean fileDefault) {
        if (task != null) {
            return task;
        }
        if (chapter != null) {
            return chapter;
        }
        return fileDefault;
    }

    /** {@code key: true|false} at brace-depth 1; absent / non-bool → null (DEFAULT). */
    static Boolean depth1ExplicitBool(String objectSlice, String key) {
        if (objectSlice == null || key == null || key.isBlank()) {
            return null;
        }
        Pattern p = Pattern.compile(
                "\\b" + Pattern.quote(key) + "\\s*:\\s*(true|false)\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(objectSlice);
        while (m.find()) {
            if (QuestGuide.braceDepthAt(objectSlice, m.start()) == 1) {
                return Boolean.parseBoolean(m.group(1));
            }
        }
        return null;
    }

    /** First {@code key: true|false} anywhere in text (file-level defaults). */
    static Boolean parseExplicitBool(String text, String key) {
        if (text == null || key == null || key.isBlank()) {
            return null;
        }
        Pattern p = Pattern.compile(
                "\\b" + Pattern.quote(key) + "\\s*:\\s*(true|false)\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return Boolean.parseBoolean(m.group(1));
        }
        return null;
    }

    /** Item-type task object slices inside {@code tasks: [ ... ]} arrays. */
    static List<String> itemTaskSlices(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        Matcher am = TASKS_ARRAY.matcher(text);
        while (am.find()) {
            int bracket = am.end() - 1;
            for (int[] span : QuestGuide.topLevelObjects(text, bracket)) {
                String slice = text.substring(span[0], span[1]);
                if (TYPE_ITEM.matcher(slice).find()) {
                    out.add(slice);
                }
            }
        }
        return out;
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

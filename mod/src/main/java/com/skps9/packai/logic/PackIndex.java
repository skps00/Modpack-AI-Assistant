package com.skps9.packai.logic;

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
    private static final int MAX_GRAPH = 120;
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

    private final List<String> paths = new ArrayList<>();
    private final Map<String, List<Integer>> inverted = new HashMap<>();
    private final Map<String, String> textCache = new HashMap<>();
    private final Set<String> removedItems = new HashSet<>();
    private final List<String> graphFacts = new ArrayList<>();
    /** item id → loot/trade relative paths that mention it (built at index time). */
    private final Map<String, List<String>> acquirePathsByItem = new HashMap<>();
    private Path root;

    public void build(Path gameDir, List<String> scanners) {
        paths.clear();
        inverted.clear();
        textCache.clear();
        removedItems.clear();
        graphFacts.clear();
        acquirePathsByItem.clear();
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
                        if (Files.size(p) > 400_000) {
                            return;
                        }
                        String rel = gameDir.relativize(p).toString().replace('\\', '/');
                        if (!seenRel.add(rel)) {
                            return;
                        }
                        String pl = rel.toLowerCase(Locale.ROOT);
                        if (QuestGuide.isRewardTablePath(pl, name)) {
                            return;
                        }
                        addPath(rel);
                        if (isAcquirePath(pl)) {
                            indexAcquireFile(rel);
                        } else if (isScriptPath(pl)) {
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
            if (snippets.size() < 3) {
                String clip = text.length() > 600 ? text.substring(0, 600) : text;
                snippets.add("// file: " + rel + "\n" + clip);
                sources.add(rel);
                topScore = Math.max(topScore, score);
            }
        }
        boolean high = topScore >= 12 && !snippets.isEmpty();
        return new RetrieveResult(snippets, sources, topScore, high, Set.copyOf(removedItems), List.copyOf(graphFacts));
    }

    /**
     * Local non-JEI acquire hints for an item (loot / trade / script recipe).
     * Call after {@link #retrieve} so script recipes are also ingested.
     */
    public List<String> acquireFactsFor(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return List.of();
        }
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
                out.add("釣魚：" + f.substring((prefix + " -[fish]-> ").length()));
            } else if (f.startsWith(prefix + " -[loot]-> ")) {
                out.add("掉落：" + f.substring((prefix + " -[loot]-> ").length()));
            } else if (f.startsWith(prefix + " -[trade]-> ")) {
                out.add("交易：" + f.substring((prefix + " -[trade]-> ").length()));
            } else if (f.startsWith(prefix + " -[recipe_needs]-> ")) {
                String need = f.substring((prefix + " -[recipe_needs]-> ").length()).replace("item:", "");
                if (isCompactCycle(id, need, recipeNeeds)) {
                    if (cycles.size() < 3) {
                        cycles.add("壓縮循環（材料↔磚塊，不是主要取得方式）：與「"
                                + Plainify.displayName(need) + "」互轉");
                    }
                } else {
                    out.add("腳本配方需要：" + Plainify.displayName(need));
                }
            } else if (f.startsWith(prefix + " -[removed]-> ")) {
                out.add("腳本已移除原配方（整合包有改動）");
            }
        }
        if (out.isEmpty() && cycles.isEmpty()) {
            return List.of();
        }
        List<String> labeled = new ArrayList<>();
        labeled.add("【本地獲取】「" + Plainify.displayName(id) + "」");
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
        if (rel == null || rel.isBlank()) {
            return "整合包資料";
        }
        String pl = rel.replace('\\', '/');
        String lower = pl.toLowerCase(Locale.ROOT);
        String name = pl;
        int slash = pl.lastIndexOf('/');
        if (slash >= 0 && slash < pl.length() - 1) {
            name = pl.substring(slash + 1);
        }
        name = name.replaceFirst("\\.[^.]+$", "").replace('_', ' ');
        String kind;
        if (isFishingPath(lower)) {
            kind = "釣魚";
        } else if (isLootPath(lower)) {
            kind = "掉落表";
        } else {
            kind = "交易";
        }
        return kind + "「" + name + "」";
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

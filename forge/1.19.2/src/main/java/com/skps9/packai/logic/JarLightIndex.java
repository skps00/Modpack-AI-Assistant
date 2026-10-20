package com.skps9.packai.logic;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.config.PackAiConfig;

/**
 * Light in-jar datapack index — zip entries only (no decompile).
 * Caches compact recipe/loot facts under {@code config/packai/jar-cache/}.
 * <p>
 * Scan runs on first Ask when {@code scanModJars} is on (not PackIndex warmup). Cache is reused;
 * Ask injects facts only for the focused item id (its {@code ns:path}), not every mod in the pack.
 * <p>
 * Cache layout (schema {@link #CACHE_SCHEMA}):
 * <ul>
 *   <li>{@code manifest.json} — {@code v} + jar file name → fingerprint + shard name</li>
 *   <li>{@code <fp12>.json} — {@code { "schema":2, "items": { "mod:id": ["R|…","U|…"] },
 *       "loot": { "mod:id": ["chests/…"] } }}</li>
 * </ul>
 * Fact codes: {@code R} = crafts as result, {@code U} = used as ingredient, {@code L} = loot
 * (loot lives in {@link #lootRoutes}, not {@link #byItem}).
 */
public final class JarLightIndex {
    public static final JarLightIndex INSTANCE = new JarLightIndex();

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    static final int MAX_RECIPES_PER_JAR = 200;
    static final int MAX_LOOT_PER_JAR = 150;
    static final int MAX_FACTS_PER_ITEM = 8;
    static final int MAX_LOOT_ROUTES_PER_ITEM = 6;
    static final int MAX_INGS = 6;
    static final int MAX_ASK_LINES = 4;
    static final int MAX_ENTRY_BYTES = 256_000;
    static final int CACHE_SCHEMA = 2;

    /** Published immutable snapshot; readers never see a partially filled map. */
    private volatile Map<String, List<String>> byItem = Map.of();
    /** item id → loot table keys (no {@code L|} prefix); capped at {@link #MAX_LOOT_ROUTES_PER_ITEM}. */
    private volatile Map<String, List<String>> lootRoutes = Map.of();
    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private final Object lock = new Object();
    private volatile Path cacheDir;

    private JarLightIndex() {}

    /** Background-safe: no-op when {@code scanModJars} off or mods dir missing. */
    public void ensure(Path gameDir) {
        if (!PackAiConfig.scanModJars()) {
            return;
        }
        if (gameDir == null || !Files.isDirectory(gameDir)) {
            return;
        }
        Path mods = gameDir.resolve("mods");
        if (!Files.isDirectory(mods)) {
            return;
        }
        Path cache = gameDir.resolve("config/packai/jar-cache");
        synchronized (lock) {
            try {
                Files.createDirectories(cache);
            } catch (IOException e) {
                return;
            }
            this.cacheDir = cache;
            final long t0 = System.nanoTime();
            Map<String, List<String>> items = new LinkedHashMap<>();
            Map<String, List<String>> loot = new LinkedHashMap<>();
            boolean cacheLoaded = !loaded.get();
            if (!loaded.get()) {
                loadAllShards(cache, items, loot);
            } else {
                copyMutable(byItem, items);
                copyMutable(lootRoutes, loot);
            }
            int scanned = scanMods(mods, cache, items, loot);
            byItem = freezeStringListMap(items);
            lootRoutes = freezeStringListMap(loot);
            loaded.set(true);
            PackAiMod.LOGGER.info(
                    "Pack AI index scan: cacheLoad={} modJarsScanned={} items={} took={}ms",
                    cacheLoaded, scanned, byItem.size(), (System.nanoTime() - t0) / 1_000_000L);
        }
    }

    /** Clear memory so next {@link #ensure} reloads (e.g. after toggling config). */
    public void reset() {
        synchronized (lock) {
            byItem = Map.of();
            lootRoutes = Map.of();
            loaded.set(false);
            cacheDir = null;
        }
    }

    public boolean isReady() {
        return loaded.get();
    }

    /**
     * Ask-time jar facts for one item id only (namespace implied by {@code ns:path}).
     * Empty if off / unknown / empty — never dumps other mods into the prompt.
     * Delegates with {@code includeRecipeFacts=true} (behaviour unchanged).
     */
    public List<String> factsForAsk(String itemId, String replyLang) {
        return factsForAsk(itemId, replyLang, true);
    }

    /**
     * Like {@link #factsForAsk(String, String)}; when {@code includeRecipeFacts} is false,
     * skip codes whose kind is {@code 'R'} (keep {@code 'U'}). Reads {@link #byItem} only —
     * loot is not in ask facts (use {@link #routeLinesForItem}).
     */
    public List<String> factsForAsk(String itemId, String replyLang, boolean includeRecipeFacts) {
        if (!PackAiConfig.scanModJars() || itemId == null || itemId.isBlank()) {
            return List.of();
        }
        String id = itemId.toLowerCase(Locale.ROOT).trim();
        List<String> raw = byItem.get(id);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        String lang = replyLang == null || replyLang.isBlank() ? "zh_tw" : replyLang.trim();
        List<String> lines = new ArrayList<>();
        lines.add(ReplyLang.jarHeader(lang));
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String code : raw) {
            if (!includeRecipeFacts && code != null && !code.isEmpty() && code.charAt(0) == 'R') {
                continue;
            }
            if (lines.size() - 1 >= MAX_ASK_LINES) {
                break;
            }
            String human = formatFact(code, lang);
            if (human == null || human.isBlank() || !seen.add(human)) {
                continue;
            }
            lines.add(human);
        }
        return lines.size() <= 1 ? List.of() : List.copyOf(lines);
    }

    /**
     * In-memory {@code L|}/{@code R|}/{@code U|} codes for one item.
     * Merges {@link #lootRoutes} then {@link #byItem} — order fixed: L, then R, then U.
     * Empty list if unknown (never null).
     */
    public List<String> routeLinesForItem(String itemId) {
        if (!PackAiConfig.scanModJars()) {
            return List.of();
        }
        if (itemId == null || itemId.isBlank()) {
            return List.of();
        }
        String id = itemId.toLowerCase(Locale.ROOT).trim();
        List<String> out = new ArrayList<>();
        List<String> loot = lootRoutes.get(id);
        if (loot != null) {
            for (String path : loot) {
                if (path != null && !path.isBlank()) {
                    out.add("L|" + path);
                }
            }
        }
        List<String> raw = byItem.get(id);
        if (raw != null) {
            for (String code : raw) {
                if (code == null || code.length() < 3 || code.charAt(1) != '|') {
                    continue;
                }
                if (code.charAt(0) == 'R') {
                    out.add(code);
                }
            }
            for (String code : raw) {
                if (code == null || code.length() < 3 || code.charAt(1) != '|') {
                    continue;
                }
                if (code.charAt(0) == 'U') {
                    out.add(code);
                }
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    // ── package-visible for tests / python mirror ──────────────────────────

    static String fingerprintZip(Path jar) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String name = e.getName() == null ? "" : e.getName();
                md.update(name.getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
                long crc = e.getCrc();
                long size = e.getSize();
                for (int i = 0; i < 8; i++) {
                    md.update((byte) (crc >>> (i * 8)));
                }
                for (int i = 0; i < 8; i++) {
                    md.update((byte) (size >>> (i * 8)));
                }
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    static boolean isRecipeEntry(String pathLower) {
        return pathLower.startsWith("data/")
                && pathLower.contains("/recipes/")
                && pathLower.endsWith(".json")
                && !pathLower.contains("/advancements/");
    }

    static boolean isLootEntry(String pathLower) {
        return pathLower.startsWith("data/")
                && pathLower.contains("/loot_tables/")
                && pathLower.endsWith(".json");
    }

    static String lootKeyFromPath(String pathLower) {
        int i = pathLower.indexOf("/loot_tables/");
        if (i < 0) {
            return pathLower;
        }
        String rest = pathLower.substring(i + "/loot_tables/".length());
        if (rest.endsWith(".json")) {
            rest = rest.substring(0, rest.length() - 5);
        }
        return rest;
    }

    static void parseRecipeJson(String json, Map<String, List<String>> out) {
        if (json == null || json.isBlank()) {
            return;
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (Exception e) {
            return;
        }
        if (!root.isJsonObject()) {
            return;
        }
        JsonObject o = root.getAsJsonObject();
        String type = o.has("type") && o.get("type").isJsonPrimitive()
                ? o.get("type").getAsString()
                : "recipe";
        String typeShort = shortType(type);
        String result = extractItemId(o.get("result"));
        if (result == null) {
            result = extractItemId(o.get("output"));
        }
        if (result == null || PackIndex.isNoiseItemId(result)) {
            return;
        }
        List<String> ings = extractIngredients(o);
        String ingJoined = String.join(",", ings.subList(0, Math.min(ings.size(), MAX_INGS)));
        addFact(out, result, "R|" + typeShort + "|" + ingJoined);
        int u = 0;
        for (String ing : ings) {
            if (u >= MAX_INGS) {
                break;
            }
            if (PackIndex.isNoiseItemId(ing) || ing.equals(result)) {
                continue;
            }
            addFact(out, ing, "U|" + typeShort + "|" + result);
            u++;
        }
    }

    /**
     * Structural loot parse → {@code lootOut} (item id → table keys, no {@code L|} prefix).
     * Only {@code minecraft:item} / bare item entries; never {@code minecraft:loot_table}.
     */
    static void parseLootJson(String pathLower, String json, Map<String, List<String>> lootOut) {
        if (json == null || json.isBlank() || lootOut == null) {
            return;
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (Exception e) {
            return;
        }
        if (!root.isJsonObject()) {
            return;
        }
        String key = lootKeyFromPath(pathLower);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        walkLootNode(root.getAsJsonObject(), key, seen, lootOut);
    }

    static String formatFact(String code, String lang) {
        if (code == null || code.length() < 3 || code.charAt(1) != '|') {
            return null;
        }
        char kind = code.charAt(0);
        String rest = code.substring(2);
        return switch (kind) {
            case 'R' -> {
                int sep = rest.indexOf('|');
                String type = sep < 0 ? "recipe" : rest.substring(0, sep);
                String ings = sep < 0 ? "" : rest.substring(sep + 1);
                yield ReplyLang.jarCraft(lang, type, ings);
            }
            case 'U' -> {
                int sep = rest.indexOf('|');
                String type = sep < 0 ? "recipe" : rest.substring(0, sep);
                String result = sep < 0 ? rest : rest.substring(sep + 1);
                yield ReplyLang.jarUsedIn(lang, type, Plainify.displayName(result));
            }
            case 'L' -> ReplyLang.jarLoot(lang, rest);
            default -> null;
        };
    }

    static void addFact(Map<String, List<String>> out, String itemId, String fact) {
        if (itemId == null || itemId.isBlank() || fact == null || fact.isBlank()) {
            return;
        }
        String id = itemId.toLowerCase(Locale.ROOT).trim();
        List<String> list = out.computeIfAbsent(id, k -> new ArrayList<>());
        if (list.size() >= MAX_FACTS_PER_ITEM || list.contains(fact)) {
            return;
        }
        list.add(fact);
    }

    static void addLootRoute(Map<String, List<String>> out, String itemId, String tablePath) {
        if (itemId == null || itemId.isBlank() || tablePath == null || tablePath.isBlank()) {
            return;
        }
        String id = itemId.toLowerCase(Locale.ROOT).trim();
        List<String> list = out.computeIfAbsent(id, k -> new ArrayList<>());
        if (list.size() >= MAX_LOOT_ROUTES_PER_ITEM || list.contains(tablePath)) {
            return;
        }
        list.add(tablePath);
    }

    /** Per-jar scan: recipe/used facts + loot table paths (separate maps). */
    static final class ScanResult {
        final Map<String, List<String>> items;
        final Map<String, List<String>> loot;

        ScanResult(Map<String, List<String>> items, Map<String, List<String>> loot) {
            this.items = items;
            this.loot = loot;
        }
    }

    // ── private scan / cache ───────────────────────────────────────────────

    private int scanMods(
            Path mods,
            Path cache,
            Map<String, List<String>> items,
            Map<String, List<String>> loot) {
        JsonObject manifest = readManifest(cache);
        JsonObject jars = manifest.has("jars") && manifest.get("jars").isJsonObject()
                ? manifest.getAsJsonObject("jars")
                : new JsonObject();
        boolean dirty = false;
        int scanned = 0;
        List<Path> jarFiles = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(mods, "*.jar")) {
            for (Path jar : ds) {
                jarFiles.add(jar);
            }
        } catch (IOException ignored) {
            return scanned;
        }
        jarFiles.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        for (Path jar : jarFiles) {
            scanned++;
            try {
                if (scanOneJar(jar, cache, jars, items, loot)) {
                    dirty = true;
                }
            } catch (Exception ignored) {
                // soft-skip bad jar
            }
        }
        if (dirty) {
            manifest.addProperty("v", CACHE_SCHEMA);
            manifest.add("jars", jars);
            writeJson(cache.resolve("manifest.json"), manifest);
        }
        return scanned;
    }

    /** @return true if manifest changed */
    private boolean scanOneJar(
            Path jar,
            Path cache,
            JsonObject jars,
            Map<String, List<String>> items,
            Map<String, List<String>> loot) throws Exception {
        String name = jar.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("packai-") || lower.contains("packai")) {
            return false;
        }
        String fp = fingerprintZip(jar);
        if (jars.has(name) && jars.get(name).isJsonObject()) {
            JsonObject prev = jars.getAsJsonObject(name);
            if (fp.equals(prev.has("fp") ? prev.get("fp").getAsString() : "")
                    && prev.has("shard")) {
                Path shardPath = cache.resolve(prev.get("shard").getAsString());
                if (shardSchemaCurrent(shardPath)) {
                    return false;
                }
            }
        }
        ScanResult scanned = scanJarFile(jar);
        String shard = fp.substring(0, Math.min(12, fp.length())) + ".json";
        JsonObject shardObj = new JsonObject();
        shardObj.addProperty("jar", name);
        shardObj.addProperty("fp", fp);
        shardObj.addProperty("schema", CACHE_SCHEMA);
        shardObj.add("items", sortedStringListMap(scanned.items));
        shardObj.add("loot", sortedStringListMap(scanned.loot));
        writeJson(cache.resolve(shard), shardObj);
        mergeFacts(items, scanned.items);
        mergeLoot(loot, scanned.loot);
        JsonObject entry = new JsonObject();
        entry.addProperty("fp", fp);
        entry.addProperty("shard", shard);
        jars.add(name, entry);
        return true;
    }

    static ScanResult scanJarFile(Path jar) throws IOException {
        Map<String, List<String>> items = new LinkedHashMap<>();
        Map<String, List<String>> loot = new LinkedHashMap<>();
        int recipes = 0;
        int loots = 0;
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) {
                    continue;
                }
                String path = e.getName().replace('\\', '/');
                String pl = path.toLowerCase(Locale.ROOT);
                boolean recipe = isRecipeEntry(pl);
                boolean lootEntry = isLootEntry(pl);
                if (!recipe && !lootEntry) {
                    continue;
                }
                if (recipe && recipes >= MAX_RECIPES_PER_JAR) {
                    continue;
                }
                if (lootEntry && loots >= MAX_LOOT_PER_JAR) {
                    continue;
                }
                if (e.getSize() > MAX_ENTRY_BYTES) {
                    continue;
                }
                String text = readZipEntry(zf, e);
                if (text == null || text.isBlank()) {
                    continue;
                }
                if (recipe) {
                    parseRecipeJson(text, items);
                    recipes++;
                } else {
                    parseLootJson(pl, text, loot);
                    loots++;
                }
            }
        }
        return new ScanResult(items, loot);
    }

    private void loadAllShards(
            Path cache, Map<String, List<String>> items, Map<String, List<String>> lootOut) {
        JsonObject manifest = readManifest(cache);
        if (!manifest.has("jars") || !manifest.get("jars").isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : manifest.getAsJsonObject("jars").entrySet()) {
            if (!e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject meta = e.getValue().getAsJsonObject();
            if (!meta.has("shard")) {
                continue;
            }
            Path shard = cache.resolve(meta.get("shard").getAsString());
            if (!Files.isRegularFile(shard)) {
                continue;
            }
            try {
                String text = Files.readString(shard, StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
                if (!shardObjectCurrent(obj)) {
                    continue;
                }
                if (obj.has("items") && obj.get("items").isJsonObject()) {
                    Map<String, List<String>> facts = new LinkedHashMap<>();
                    for (Map.Entry<String, JsonElement> ie : obj.getAsJsonObject("items").entrySet()) {
                        if (!ie.getValue().isJsonArray()) {
                            continue;
                        }
                        for (JsonElement el : ie.getValue().getAsJsonArray()) {
                            if (el.isJsonPrimitive()) {
                                String fact = el.getAsString();
                                if (fact != null && fact.startsWith("L|")) {
                                    continue;
                                }
                                addFact(facts, ie.getKey(), fact);
                            }
                        }
                    }
                    mergeFacts(items, facts);
                }
                if (obj.has("loot") && obj.get("loot").isJsonObject()) {
                    Map<String, List<String>> loot = new LinkedHashMap<>();
                    for (Map.Entry<String, JsonElement> le : obj.getAsJsonObject("loot").entrySet()) {
                        if (!le.getValue().isJsonArray()) {
                            continue;
                        }
                        for (JsonElement el : le.getValue().getAsJsonArray()) {
                            if (el.isJsonPrimitive()) {
                                addLootRoute(loot, le.getKey(), el.getAsString());
                            }
                        }
                    }
                    mergeLoot(lootOut, loot);
                }
            } catch (Exception ignored) {
                // skip corrupt shard
            }
        }
    }

    private void mergeFacts(Map<String, List<String>> destMap, Map<String, List<String>> facts) {
        for (Map.Entry<String, List<String>> e : facts.entrySet()) {
            List<String> dest = destMap.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
            for (String f : e.getValue()) {
                if (dest.size() >= MAX_FACTS_PER_ITEM) {
                    break;
                }
                if (!dest.contains(f)) {
                    dest.add(f);
                }
            }
        }
    }

    private void mergeLoot(Map<String, List<String>> destMap, Map<String, List<String>> loot) {
        for (Map.Entry<String, List<String>> e : loot.entrySet()) {
            List<String> dest = destMap.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
            int dropped = 0;
            for (String path : e.getValue()) {
                if (path == null || path.isBlank()) {
                    continue;
                }
                if (dest.contains(path)) {
                    continue;
                }
                if (dest.size() >= MAX_LOOT_ROUTES_PER_ITEM) {
                    dropped++;
                    continue;
                }
                dest.add(path);
            }
            if (dropped > 0) {
                PackAiMod.LOGGER.info("jar.loot.capped item={} dropped={}", e.getKey(), dropped);
            }
        }
    }

    private static void copyMutable(Map<String, List<String>> src, Map<String, List<String>> dest) {
        if (src == null || src.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<String>> e : src.entrySet()) {
            List<String> vals = e.getValue();
            dest.put(e.getKey(), vals == null ? new ArrayList<>() : new ArrayList<>(vals));
        }
    }

    private static Map<String, List<String>> freezeStringListMap(Map<String, List<String>> src) {
        if (src == null || src.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>(src.size());
        for (Map.Entry<String, List<String>> e : src.entrySet()) {
            List<String> vals = e.getValue();
            out.put(e.getKey(), vals == null || vals.isEmpty() ? List.of() : List.copyOf(vals));
        }
        return Map.copyOf(out);
    }

    private static JsonObject readManifest(Path cache) {
        Path p = cache.resolve("manifest.json");
        if (!Files.isRegularFile(p)) {
            return new JsonObject();
        }
        try {
            JsonElement el = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
            if (!el.isJsonObject()) {
                return new JsonObject();
            }
            JsonObject manifest = el.getAsJsonObject();
            int oldV = 0;
            boolean hasV = manifest.has("v") && manifest.get("v").isJsonPrimitive();
            if (hasV) {
                try {
                    oldV = manifest.get("v").getAsInt();
                } catch (Exception ignored) {
                    oldV = 0;
                    hasV = false;
                }
            }
            if (!hasV || oldV != CACHE_SCHEMA) {
                PackAiMod.LOGGER.info("jar.cache.schema {} -> {}, rescan", oldV, CACHE_SCHEMA);
                return new JsonObject();
            }
            return manifest;
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static boolean shardSchemaCurrent(Path shardPath) {
        if (!Files.isRegularFile(shardPath)) {
            return false;
        }
        try {
            JsonElement el = JsonParser.parseString(Files.readString(shardPath, StandardCharsets.UTF_8));
            return el.isJsonObject() && shardObjectCurrent(el.getAsJsonObject());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean shardObjectCurrent(JsonObject obj) {
        if (obj == null) {
            return false;
        }
        if (!obj.has("schema") || !obj.get("schema").isJsonPrimitive()) {
            return false;
        }
        try {
            if (obj.get("schema").getAsInt() != CACHE_SCHEMA) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return obj.has("loot") && obj.get("loot").isJsonObject();
    }

    private static JsonObject sortedStringListMap(Map<String, List<String>> map) {
        JsonObject obj = new JsonObject();
        if (map == null || map.isEmpty()) {
            return obj;
        }
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            JsonArray arr = new JsonArray();
            List<String> vals = map.get(k);
            if (vals != null) {
                for (String f : vals) {
                    arr.add(f);
                }
            }
            obj.add(k, arr);
        }
        return obj;
    }

    private static void writeJson(Path path, JsonObject obj) {
        try {
            Files.writeString(path, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // soft
        }
    }

    private static String readZipEntry(ZipFile zf, ZipEntry e) {
        try (InputStream in = zf.getInputStream(e);
             InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            int total = 0;
            while ((n = r.read(buf)) >= 0) {
                total += n;
                if (total > MAX_ENTRY_BYTES) {
                    return null;
                }
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } catch (IOException ex) {
            return null;
        }
    }

    private static String shortType(String type) {
        if (type == null || type.isBlank()) {
            return "recipe";
        }
        int colon = type.indexOf(':');
        String s = colon >= 0 ? type.substring(colon + 1) : type;
        if (s.length() > 24) {
            s = s.substring(0, 24);
        }
        return s;
    }

    private static String extractItemId(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (el.isJsonPrimitive()) {
            String s = el.getAsString();
            return s.contains(":") ? s.toLowerCase(Locale.ROOT) : null;
        }
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("item")) {
                return extractItemId(o.get("item"));
            }
            if (o.has("id")) {
                return extractItemId(o.get("id"));
            }
        }
        return null;
    }

    private static List<String> extractIngredients(JsonObject recipe) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        collectItems(recipe.get("ingredient"), out);
        collectItems(recipe.get("ingredients"), out);
        if (recipe.has("key") && recipe.get("key").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : recipe.getAsJsonObject("key").entrySet()) {
                collectItems(e.getValue(), out);
            }
        }
        collectItems(recipe.get("input"), out);
        collectItems(recipe.get("inputs"), out);
        return new ArrayList<>(out);
    }

    private static void collectItems(JsonElement el, LinkedHashSet<String> out) {
        if (el == null || el.isJsonNull() || out.size() >= 40) {
            return;
        }
        if (el.isJsonPrimitive()) {
            String s = el.getAsString();
            if (s.contains(":") && !s.startsWith("#")) {
                out.add(s.toLowerCase(Locale.ROOT));
            }
            return;
        }
        if (el.isJsonArray()) {
            for (JsonElement x : el.getAsJsonArray()) {
                collectItems(x, out);
                if (out.size() >= 40) {
                    return;
                }
            }
            return;
        }
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("item")) {
                collectItems(o.get("item"), out);
            } else if (o.has("id")) {
                collectItems(o.get("id"), out);
            } else if (o.has("tag")) {
                // skip tags — not a concrete id
            } else {
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    if ("count".equals(e.getKey()) || "nbt".equals(e.getKey())
                            || "components".equals(e.getKey())) {
                        continue;
                    }
                    collectItems(e.getValue(), out);
                }
            }
        }
    }

    private static void walkLootNode(
            JsonObject o, String tableKey, LinkedHashSet<String> seen, Map<String, List<String>> lootOut) {
        if (o == null || seen.size() >= 40) {
            return;
        }
        if (o.has("pools")) {
            walkLootElement(o.get("pools"), tableKey, seen, lootOut);
        }
        if (o.has("entries")) {
            walkLootElement(o.get("entries"), tableKey, seen, lootOut);
        }
        if (o.has("children")) {
            walkLootElement(o.get("children"), tableKey, seen, lootOut);
        }
        acceptLootItemEntry(o, tableKey, seen, lootOut);
    }

    private static void walkLootElement(
            JsonElement el, String tableKey, LinkedHashSet<String> seen, Map<String, List<String>> lootOut) {
        if (el == null || el.isJsonNull() || seen.size() >= 40) {
            return;
        }
        if (el.isJsonArray()) {
            for (JsonElement x : el.getAsJsonArray()) {
                walkLootElement(x, tableKey, seen, lootOut);
                if (seen.size() >= 40) {
                    return;
                }
            }
            return;
        }
        if (el.isJsonObject()) {
            walkLootNode(el.getAsJsonObject(), tableKey, seen, lootOut);
        }
    }

    private static void acceptLootItemEntry(
            JsonObject o, String tableKey, LinkedHashSet<String> seen, Map<String, List<String>> lootOut) {
        if (seen.size() >= 40 || !o.has("name")) {
            return;
        }
        String type = null;
        if (o.has("type") && o.get("type").isJsonPrimitive()) {
            type = o.get("type").getAsString();
        }
        if (type != null) {
            String tl = type.toLowerCase(Locale.ROOT);
            if (tl.equals("minecraft:loot_table") || tl.equals("loot_table")) {
                return;
            }
            if (!(tl.equals("minecraft:item") || tl.equals("item"))) {
                return;
            }
        }
        String id = extractItemId(o.get("name"));
        if (id == null || PackIndex.isNoiseItemId(id) || !seen.add(id)) {
            return;
        }
        if (LootForwardIndex.isTrivialBlockSelfLoot(id, tableKey)) {
            return;
        }
        addLootRoute(lootOut, id, tableKey);
    }
}

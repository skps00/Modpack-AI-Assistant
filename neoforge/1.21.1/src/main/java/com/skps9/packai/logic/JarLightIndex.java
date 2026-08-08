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
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skps9.packai.config.PackAiConfig;

/**
 * Light in-jar datapack index — zip entries only (no decompile).
 * Caches compact recipe/loot facts under {@code config/packai/jar-cache/}.
 * <p>
 * Scan runs on first Ask when {@code scanModJars} is on (not PackIndex warmup). Cache is reused;
 * Ask injects facts only for the focused item id (its {@code ns:path}), not every mod in the pack.
 * <p>
 * Cache layout:
 * <ul>
 *   <li>{@code manifest.json} — jar file name → fingerprint + shard name</li>
 *   <li>{@code <fp12>.json} — {@code { "items": { "mod:id": ["R|type|a,b", "L|chests/…", "U|type|result"] } }}</li>
 * </ul>
 * Fact codes: {@code R} = crafts as result, {@code U} = used as ingredient, {@code L} = loot mention.
 */
public final class JarLightIndex {
    public static final JarLightIndex INSTANCE = new JarLightIndex();

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Pattern ITEM_ID = Pattern.compile(
            "\"([a-z0-9_]+:[a-z0-9_./-]+)\"", Pattern.CASE_INSENSITIVE);

    static final int MAX_RECIPES_PER_JAR = 200;
    static final int MAX_LOOT_PER_JAR = 150;
    static final int MAX_FACTS_PER_ITEM = 8;
    static final int MAX_INGS = 6;
    static final int MAX_ASK_LINES = 4;
    static final int MAX_ENTRY_BYTES = 256_000;

    private final ConcurrentHashMap<String, List<String>> byItem = new ConcurrentHashMap<>();
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
            if (!loaded.get()) {
                loadAllShards(cache);
                loaded.set(true);
            }
            scanMods(mods, cache);
        }
    }

    /** Clear memory so next {@link #ensure} reloads (e.g. after toggling config). */
    public void reset() {
        synchronized (lock) {
            byItem.clear();
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
     */
    public List<String> factsForAsk(String itemId, String replyLang) {
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

    static void parseLootJson(String pathLower, String json, Map<String, List<String>> out) {
        if (json == null || json.isBlank()) {
            return;
        }
        String key = lootKeyFromPath(pathLower);
        Matcher m = ITEM_ID.matcher(json);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        while (m.find() && seen.size() < 40) {
            String id = m.group(1).toLowerCase(Locale.ROOT);
            if (PackIndex.isNoiseItemId(id) || !seen.add(id)) {
                continue;
            }
            addFact(out, id, "L|" + key);
        }
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

    // ── private scan / cache ───────────────────────────────────────────────

    private void scanMods(Path mods, Path cache) {
        JsonObject manifest = readManifest(cache);
        JsonObject jars = manifest.has("jars") && manifest.get("jars").isJsonObject()
                ? manifest.getAsJsonObject("jars")
                : new JsonObject();
        boolean dirty = false;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(mods, "*.jar")) {
            for (Path jar : ds) {
                try {
                    if (scanOneJar(jar, cache, jars)) {
                        dirty = true;
                    }
                } catch (Exception ignored) {
                    // soft-skip bad jar
                }
            }
        } catch (IOException ignored) {
            return;
        }
        if (dirty) {
            manifest.addProperty("v", 1);
            manifest.add("jars", jars);
            writeJson(cache.resolve("manifest.json"), manifest);
        }
    }

    /** @return true if manifest changed */
    private boolean scanOneJar(Path jar, Path cache, JsonObject jars) throws Exception {
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
                return false;
            }
        }
        Map<String, List<String>> facts = scanJarFile(jar);
        String shard = fp.substring(0, Math.min(12, fp.length())) + ".json";
        JsonObject shardObj = new JsonObject();
        shardObj.addProperty("jar", name);
        shardObj.addProperty("fp", fp);
        JsonObject items = new JsonObject();
        for (Map.Entry<String, List<String>> e : facts.entrySet()) {
            JsonArray arr = new JsonArray();
            for (String f : e.getValue()) {
                arr.add(f);
            }
            items.add(e.getKey(), arr);
        }
        shardObj.add("items", items);
        writeJson(cache.resolve(shard), shardObj);
        mergeFacts(facts);
        JsonObject entry = new JsonObject();
        entry.addProperty("fp", fp);
        entry.addProperty("shard", shard);
        jars.add(name, entry);
        return true;
    }

    static Map<String, List<String>> scanJarFile(Path jar) throws IOException {
        Map<String, List<String>> out = new LinkedHashMap<>();
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
                boolean loot = isLootEntry(pl);
                if (!recipe && !loot) {
                    continue;
                }
                if (recipe && recipes >= MAX_RECIPES_PER_JAR) {
                    continue;
                }
                if (loot && loots >= MAX_LOOT_PER_JAR) {
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
                    parseRecipeJson(text, out);
                    recipes++;
                } else {
                    parseLootJson(pl, text, out);
                    loots++;
                }
            }
        }
        return out;
    }

    private void loadAllShards(Path cache) {
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
                if (!obj.has("items") || !obj.get("items").isJsonObject()) {
                    continue;
                }
                Map<String, List<String>> facts = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> ie : obj.getAsJsonObject("items").entrySet()) {
                    if (!ie.getValue().isJsonArray()) {
                        continue;
                    }
                    for (JsonElement el : ie.getValue().getAsJsonArray()) {
                        if (el.isJsonPrimitive()) {
                            addFact(facts, ie.getKey(), el.getAsString());
                        }
                    }
                }
                mergeFacts(facts);
            } catch (Exception ignored) {
                // skip corrupt shard
            }
        }
    }

    private void mergeFacts(Map<String, List<String>> facts) {
        for (Map.Entry<String, List<String>> e : facts.entrySet()) {
            List<String> dest = byItem.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
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

    private static JsonObject readManifest(Path cache) {
        Path p = cache.resolve("manifest.json");
        if (!Files.isRegularFile(p)) {
            return new JsonObject();
        }
        try {
            JsonElement el = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
            return el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
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
}

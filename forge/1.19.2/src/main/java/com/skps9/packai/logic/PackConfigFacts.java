package com.skps9.packai.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Runtime reader for advisory numbers: A1 config tree, A2 jar data JSON, A3 offline mech-numbers.
 * Read-only on pack; in-memory mtime cache only. Designated cache root (reads): CACHE_REL.
 */
public final class PackConfigFacts {
    /** Sole allowed on-disk cache / offline artifact directory under the instance. */
    public static final String CACHE_REL = "config/packai";
    public static final String MECH_NUMBERS_REL = CACHE_REL + "/mech-numbers.json";

    static final int MAX_CONFIG_FILES = 1200;
    static final int MAX_A2_JARS = 48;
    static final int MAX_A2_JSON_PER_JAR = 80;
    static final int MAX_A2_FACTS = 4000;
    static final int MAX_LOOKUP = 8;
    static final int MAX_CRAFT_JARS = 120;
    static final int MAX_CRAFT_ROUTES = 3;
    /** Pack-default config note (overridable by live config/). */
    public static final String NOTE_DEFAULTCONFIG =
            "pack 預設（可能被 config/ 覆蓋）";

    private static final Set<String> CONFIG_EXTS = Set.of(
            ".toml", ".json5", ".json", ".yaml", ".yml", ".snbt", ".cfg", ".txt");

    private static final Pattern RE_TOML_TABLE = Pattern.compile("^\\s*\\[([^\\]]+)\\]\\s*(?:#.*)?$");
    private static final Pattern RE_ASSIGN = Pattern.compile(
            "^\\s*(?:\"([^\"]+)\"|'([^']+)'|([A-Za-z_][\\w\\-\\.]*))\\s*[=:]\\s*"
                    + "(?:\"(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\"|"
                    + "'(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)'|"
                    + "(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?))\\s*(?:[;#].*)?$");
    private static final Pattern RE_JSON_KV = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*,?");
    private static final Pattern RE_CFG_KV = Pattern.compile(
            "^\\s*([A-Za-z_][\\w\\-\\.]*)\\s*[:=]\\s*"
                    + "(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*(?:[;#].*)?$");
    private static final Pattern RE_HASH_COMMENT = Pattern.compile("^\\s*#(.*)$");
    private static final Pattern RE_LINE_COMMENT = Pattern.compile("^\\s*//(.*)$");
    private static final Pattern A2_KEY_RE = Pattern.compile(
            "(energy|burn|rate|tick|speed|duration|capacity|coolant|fuel|heat|power|"
                    + "generation|transfer|cooldown|per_tick|pertick|joule|fluidrate|tank|"
                    + "\\brf\\b|\\bfe\\b|\\bmb\\b)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern A2_SKIP_KEY = Pattern.compile(
            "^(count|x|y|z|u|v|r|g|b|a|color|priority|weight|order|index|slot|page|id)$",
            Pattern.CASE_INSENSITIVE);

    private final Path gameDir;
    private final List<NumberFact> a1 = new ArrayList<>();
    private final List<NumberFact> a3 = new ArrayList<>();
    private final Map<String, Long> a1Mtimes = new HashMap<>();
    private final Map<String, List<String>> craftRouteCache = new HashMap<>();
    private List<NumberFact> a2;
    private String a2ModsKey = "";

    public PackConfigFacts(Path gameDir) {
        this.gameDir = gameDir;
    }

    /** Load / refresh A1 + A3. A2 is lazy per lookup namespaces. */
    public void load() {
        a1.clear();
        a3.clear();
        a1Mtimes.clear();
        craftRouteCache.clear();
        a2 = null;
        a2ModsKey = "";
        if (gameDir == null || !Files.isDirectory(gameDir)) {
            return;
        }
        scanConfigTree(gameDir.resolve("config"));
        // Pack default server configs (Forge copies into world); still local instance data.
        scanConfigTree(gameDir.resolve("defaultconfigs"));
        loadMechNumbers(gameDir.resolve(MECH_NUMBERS_REL));
    }

    public Path gameDir() {
        return gameDir;
    }

    public int a1Count() {
        return a1.size();
    }

    public int a3Count() {
        return a3.size();
    }

    /**
     * Ranked mechanics numbers for an item/question. Never invents values.
     * Sources searched are always listed by the caller when empty.
     */
    public List<NumberFact> lookup(String itemId, String question) {
        if (isNonsenseQuery(itemId, question)) {
            return List.of();
        }
        Set<String> terms = termsOf(itemId, question);
        Set<String> mods = modHints(itemId, question);
        Set<String> bannedMods = bannedMods(question);
        ensureA2(mods);
        List<Scored> scored = new ArrayList<>();
        scoreAll(a1, terms, mods, scored);
        if (a2 != null) {
            scoreAll(a2, terms, mods, scored);
        }
        scoreAll(a3, terms, mods, scored);
        scored.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparing(s -> s.fact().source()));
        List<NumberFact> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Scored s : scored) {
            // Need mod hit and/or strong key-term hit — never emit bare mechanics-key noise.
            if (s.score() < 5) {
                continue;
            }
            if (isBanned(s.fact(), bannedMods)) {
                continue;
            }
            String sig = s.fact().tier() + "|" + s.fact().source() + "|" + s.fact().key();
            if (!seen.add(sig)) {
                continue;
            }
            out.add(s.fact());
            if (out.size() >= MAX_LOOKUP) {
                break;
            }
        }
        return out;
    }

    static boolean isNonsenseQuery(String itemId, String question) {
        String id = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT);
        String q = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        if (q.equals("zzz") || q.equals("???") || q.isEmpty()) {
            return true;
        }
        return id.contains("no_such") || id.contains("_zzz") || id.startsWith("packai:no_");
    }

    static Set<String> bannedMods(String question) {
        Set<String> banned = new LinkedHashSet<>();
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (q.contains("唔用 mekanism") || q.contains("不用 mekanism") || q.contains("no mek")) {
            banned.add("mekanism");
        }
        if (q.contains("唔用 nuclearcraft") || q.contains("不用 nuclearcraft") || q.contains("no nuclearcraft")) {
            banned.add("nuclearcraft");
        }
        return banned;
    }

    private static boolean isBanned(NumberFact f, Set<String> bannedMods) {
        if (bannedMods.isEmpty()) {
            return false;
        }
        String blob = (f.mod() + " " + f.file() + " " + f.source()).toLowerCase(Locale.ROOT);
        for (String b : bannedMods) {
            if (blob.contains(b)) {
                return true;
            }
        }
        return false;
    }

    public List<String> searchedSources() {
        List<String> s = new ArrayList<>();
        s.add("A1:config/**");
        s.add("A2:mods/*.jar!data/**/*.json");
        s.add("A3:" + MECH_NUMBERS_REL);
        return s;
    }

    private void scoreAll(List<NumberFact> facts, Set<String> terms, Set<String> mods, List<Scored> out) {
        for (NumberFact f : facts) {
            int sc = score(f, terms, mods);
            if (sc > 0) {
                out.add(new Scored(sc, f));
            }
        }
    }

    private static int score(NumberFact f, Set<String> terms, Set<String> mods) {
        String file = f.file().toLowerCase(Locale.ROOT);
        String key = f.key().toLowerCase(Locale.ROOT);
        String mod = f.mod().toLowerCase(Locale.ROOT);
        String source = f.source().toLowerCase(Locale.ROOT);
        // Noise sources — never score.
        if (file.contains("packai-client.toml") || file.contains("packai-common.toml")
                || file.contains("config/packai/")
                || file.contains("ftbquests/")
                || file.contains("inventoryprofilesnext/")
                || file.contains("integrationhints/")
                || file.contains("/loot_tables/")
                || source.contains("/loot_tables/")) {
            return 0;
        }
        int sc = 0;
        boolean modHit = false;
        for (String m : mods) {
            if (m.isEmpty()) {
                continue;
            }
            String ml = m.toLowerCase(Locale.ROOT);
            if (mod.contains(ml) || file.contains(ml) || source.contains(ml)) {
                sc += 6;
                modHit = true;
            }
        }
        int keyTermHits = 0;
        for (String t : terms) {
            if (t.length() < 3) {
                continue;
            }
            if (key.contains(t)) {
                keyTermHits++;
                sc += t.length() >= 6 ? 4 : 3;
            } else if (file.contains(t) || source.contains(t)) {
                sc += 1;
            }
        }
        if (A2_KEY_RE.matcher(f.key()).find()) {
            sc += modHit ? 2 : 0;
        }
        // Without mod affinity, demand at least one key-term hit.
        if (!modHit && keyTermHits == 0) {
            return 0;
        }
        return sc;
    }

    static Set<String> termsOf(String itemId, String question) {
        Set<String> terms = new LinkedHashSet<>();
        addTokens(terms, itemId);
        addTokens(terms, question);
        // Chinese / English mechanics seeds from the question blob (substring keepers).
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        for (String seed : List.of(
                "fission", "fusion", "reactor", "burn", "fuel", "turbine", "quarry",
                "builder", "laser", "bee", "harvester", "drill", "dynamo", "storage",
                "channel", "capacity", "generation", "powah", "thermal", "mekanism",
                "nuclear", "immersive", "create", "occultism", "colonies", "gtceu",
                "ae2", "refined", "pneumatic", "diesel", "ftbic", "iron", "mining",
                "反應爐", "燃料", "渦輪", "挖礦", "蜜蜂", "發電機", "儲存", "礦脈")) {
            if (q.contains(seed.toLowerCase(Locale.ROOT)) || (itemId != null
                    && itemId.toLowerCase(Locale.ROOT).contains(seed.toLowerCase(Locale.ROOT)))) {
                terms.add(seed.toLowerCase(Locale.ROOT));
            }
        }
        return terms;
    }

    static Set<String> modHints(String itemId, String question) {
        Set<String> mods = new LinkedHashSet<>();
        if (itemId != null && itemId.contains(":")) {
            mods.add(itemId.substring(0, itemId.indexOf(':')).toLowerCase(Locale.ROOT));
        }
        String blob = ((itemId == null ? "" : itemId) + " " + (question == null ? "" : question))
                .toLowerCase(Locale.ROOT);
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("mekanism", "mekanism"),
                Map.entry("biggerreactor", "biggerreactors"),
                Map.entry("nuclearcraft", "nuclearcraft"),
                Map.entry("ftbic", "ftbic"),
                Map.entry("powah", "powah"),
                Map.entry("diesel", "createdieselgenerators"),
                Map.entry("immersive", "immersiveengineering"),
                Map.entry("rftools", "rftoolsbuilder"),
                Map.entry("enchanted miner", "quarryplus"),
                Map.entry("quarryplus", "quarryplus"),
                Map.entry("minecolon", "minecolonies"),
                Map.entry("occultism", "occultism"),
                Map.entry("industrial foregoing", "industrialforegoing"),
                Map.entry("industrialforegoing", "industrialforegoing"),
                Map.entry("mystical", "mysticalagriculture"),
                Map.entry("productivebee", "productivebees"),
                Map.entry("ex nihilo", "exnihilosequentia"),
                Map.entry("create", "create"),
                Map.entry("gtceu", "gtceu"),
                Map.entry("gregtech", "gtceu"),
                Map.entry("thermal", "thermal"),
                Map.entry("ae2", "ae2"),
                Map.entry("applied energistics", "ae2"),
                Map.entry("refined storage", "refinedstorage"),
                Map.entry("refinedstorage", "refinedstorage"),
                Map.entry("pneumatic", "pneumaticcraft"));
        for (Map.Entry<String, String> e : aliases.entrySet()) {
            if (blob.contains(e.getKey())) {
                mods.add(e.getValue());
            }
        }
        return mods;
    }

    private static void addTokens(Set<String> terms, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String s = raw.toLowerCase(Locale.ROOT).replace(':', ' ').replace('/', ' ')
                .replace('-', ' ').replace('_', ' ');
        for (String t : s.split("[^a-z0-9\\u4e00-\\u9fff]+")) {
            if (t.length() >= 3) {
                terms.add(t);
            }
        }
    }

    private void ensureA2(Set<String> mods) {
        String key = String.join(",", mods);
        if (a2 != null && key.equals(a2ModsKey)) {
            return;
        }
        a2ModsKey = key;
        a2 = new ArrayList<>();
        Path modsDir = gameDir == null ? null : gameDir.resolve("mods");
        if (modsDir == null || !Files.isDirectory(modsDir) || mods.isEmpty()) {
            return;
        }
        List<Path> jars;
        try (var stream = Files.list(modsDir)) {
            jars = stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return;
        }
        int jarN = 0;
        for (Path jar : jars) {
            String name = jar.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean hit = false;
            for (String m : mods) {
                if (m.length() >= 3 && name.contains(m.replace("_", ""))) {
                    hit = true;
                    break;
                }
                if (m.length() >= 3 && name.contains(m)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                continue;
            }
            scanJarA2(jar);
            jarN++;
            if (jarN >= MAX_A2_JARS || a2.size() >= MAX_A2_FACTS) {
                break;
            }
        }
    }

    private void scanJarA2(Path jar) {
        String mod = modFromJarName(jar.getFileName().toString());
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            int jsonN = 0;
            while (en.hasMoreElements() && jsonN < MAX_A2_JSON_PER_JAR && a2.size() < MAX_A2_FACTS) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) {
                    continue;
                }
                String n = e.getName().replace('\\', '/');
                if (!n.startsWith("data/") || !n.endsWith(".json")) {
                    continue;
                }
                // Skip pure loot tables with only rolls/count noise unless energy-ish path.
                String low = n.toLowerCase(Locale.ROOT);
                if (low.contains("/loot_tables/") || low.contains("/advancements/")
                        || low.contains("/tags/")) {
                    continue;
                }
                jsonN++;
                try (InputStream in = zf.getInputStream(e);
                     BufferedReader br = new BufferedReader(
                             new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    int lineno = 0;
                    while ((line = br.readLine()) != null) {
                        lineno++;
                        Matcher jm = RE_JSON_KV.matcher(line);
                        while (jm.find()) {
                            String k = jm.group(1);
                            String raw = jm.group(2);
                            if (A2_SKIP_KEY.matcher(k).matches()) {
                                continue;
                            }
                            if (!A2_KEY_RE.matcher(k).find() && !A2_KEY_RE.matcher(n).find()) {
                                continue;
                            }
                            a2.add(new NumberFact(
                                    k, raw, "A2",
                                    jar.getFileName().toString() + "!" + n + ":" + lineno,
                                    n, mod));
                            if (a2.size() >= MAX_A2_FACTS) {
                                return;
                            }
                        }
                        if (sb.length() < 200_000) {
                            sb.append(line).append('\n');
                        }
                    }
                } catch (IOException ignored) {
                    // skip entry
                }
            }
        } catch (IOException ignored) {
            // skip jar
        }
    }

    private void scanConfigTree(Path configRoot) {
        if (!Files.isDirectory(configRoot)) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(configRoot)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                for (String ext : CONFIG_EXTS) {
                    if (name.endsWith(ext)) {
                        files.add(p);
                        break;
                    }
                }
            });
        } catch (IOException ignored) {
            return;
        }
        files.sort(Comparator.comparing(p -> p.toString().replace('\\', '/')));
        int n = 0;
        for (Path p : files) {
            if (n >= MAX_CONFIG_FILES) {
                break;
            }
            // Skip packai internal indexes (huge, not mechanics config).
            String rel = rel(configRoot.getParent() == null ? gameDir : gameDir, p);
            String low = rel.toLowerCase(Locale.ROOT);
            if (low.contains("config/packai/")
                    || low.endsWith("packai-client.toml")
                    || low.endsWith("packai-common.toml")
                    || low.contains("config/jei/")
                    || low.contains("config/ftbquests/")
                    || low.contains("inventoryprofilesnext/")) {
                continue;
            }
            long mtime = 0L;
            try {
                mtime = Files.getLastModifiedTime(p).toMillis();
            } catch (IOException ignored) {
                // keep 0
            }
            a1Mtimes.put(rel, mtime);
            scanConfigFile(p, rel);
            n++;
        }
    }

    private void scanConfigFile(Path abs, String rel) {
        String text;
        try {
            text = Files.readString(abs, StandardCharsets.UTF_8);
        } catch (Exception e) {
            try {
                text = Files.readString(abs);
            } catch (Exception e2) {
                return;
            }
        }
        String ext = extOf(abs.getFileName().toString());
        String mod = modFromConfigRel(rel);
        String section = "";
        List<String> pending = new ArrayList<>();
        boolean inBlock = false;
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineno = i + 1;
            String line = lines[i];
            if (inBlock) {
                if (line.contains("*/")) {
                    inBlock = false;
                    line = line.substring(line.indexOf("*/") + 2);
                } else {
                    continue;
                }
            }
            if (line.contains("/*") && (ext.equals(".json5") || ext.equals(".json")
                    || ext.equals(".cfg") || ext.equals(".txt"))) {
                int a = line.indexOf("/*");
                String before = line.substring(0, a);
                String after = line.substring(a + 2);
                if (after.contains("*/")) {
                    line = before + after.substring(after.indexOf("*/") + 2);
                } else {
                    inBlock = true;
                    line = before;
                }
            }
            Matcher hm = RE_HASH_COMMENT.matcher(line);
            Matcher lm = RE_LINE_COMMENT.matcher(line);
            if (hm.matches() || lm.matches()) {
                String body = (hm.matches() ? hm.group(1) : lm.group(1)).trim();
                pending.add(body);
                continue;
            }
            String[] stripped = stripLineComment(line);
            String code = stripped[0].trim();
            String trail = stripped[1];
            if (code.isEmpty()) {
                continue;
            }
            if (ext.equals(".toml") || ext.equals(".yaml") || ext.equals(".yml")
                    || ((ext.equals(".cfg") || ext.equals(".txt")) && code.startsWith("["))) {
                Matcher tm = RE_TOML_TABLE.matcher(code);
                if (tm.matches()) {
                    section = tm.group(1).trim();
                    pending.clear();
                    continue;
                }
            }
            String key = null;
            String rawNum = null;
            Matcher m = RE_ASSIGN.matcher(code);
            if (m.matches()) {
                key = first(m.group(1), m.group(2), m.group(3));
                rawNum = first(m.group(4), m.group(5), m.group(6));
            } else if (ext.equals(".json") || ext.equals(".json5") || ext.equals(".snbt")) {
                Matcher jm = RE_JSON_KV.matcher(code);
                if (jm.find()) {
                    key = jm.group(1);
                    rawNum = jm.group(2);
                }
            } else {
                Matcher cm = RE_CFG_KV.matcher(code);
                if (cm.matches()) {
                    key = cm.group(1);
                    rawNum = cm.group(2);
                }
            }
            if (key == null || rawNum == null) {
                pending.clear();
                continue;
            }
            String fullKey = section.isEmpty() ? key : section + "." + key;
            String note = rel.startsWith("defaultconfigs/") ? NOTE_DEFAULTCONFIG : "";
            a1.add(new NumberFact(
                    fullKey, rawNum, "A1", rel + ":" + lineno, rel, mod, note));
            pending.clear();
            if (trail != null && !trail.isBlank()) {
                // keep parser simple — trail already ignored for value
            }
        }
    }

    private void loadMechNumbers(Path path) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            JsonArray records = root.has("records") && root.get("records").isJsonArray()
                    ? root.getAsJsonArray("records") : null;
            if (records == null) {
                return;
            }
            for (JsonElement el : records) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                String kind = str(o, "source_kind");
                if (!"A3".equalsIgnoreCase(kind) && !"B".equalsIgnoreCase(kind)) {
                    // A1/A2 in mech-numbers are optional extras; prefer live A1 scan.
                    if (!"A1".equalsIgnoreCase(kind) && !"A2".equalsIgnoreCase(kind)) {
                        continue;
                    }
                }
                String tier = "A3".equalsIgnoreCase(kind) ? "A3"
                        : "B".equalsIgnoreCase(kind) ? "B"
                        : kind.toUpperCase(Locale.ROOT);
                if (!"A3".equals(tier) && !"B".equals(tier)) {
                    continue;
                }
                String file = str(o, "file");
                String key = str(o, "key");
                String valueRaw = o.has("value_raw") ? str(o, "value_raw")
                        : (o.has("value") ? o.get("value").toString() : "");
                String cls = str(o, "class");
                String method = str(o, "method");
                Integer line = o.has("line") && o.get("line").isJsonPrimitive()
                        ? o.get("line").getAsInt() : null;
                String source;
                if (cls != null && !cls.isBlank()) {
                    source = cls + "#" + (method == null || method.isBlank() ? "?" : method);
                } else if (line != null) {
                    source = file + ":" + line;
                } else {
                    source = file;
                }
                String mod = str(o, "mod");
                a3.add(new NumberFact(key, valueRaw, tier, source, file, mod == null ? "" : mod));
            }
        } catch (Exception ignored) {
            // missing / malformed cache = no A3
        }
    }

    private static String[] stripLineComment(String line) {
        Character inS = null;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inS != null) {
                if (ch == inS && (i == 0 || line.charAt(i - 1) != '\\')) {
                    inS = null;
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                inS = ch;
                continue;
            }
            if (ch == '#' || (ch == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/')) {
                return new String[]{line.substring(0, i), line.substring(i).replaceFirst("^[#/]+", "").trim()};
            }
        }
        return new String[]{line, ""};
    }

    private static String first(String... xs) {
        for (String x : xs) {
            if (x != null && !x.isEmpty()) {
                return x;
            }
        }
        return null;
    }

    private static String extOf(String name) {
        int d = name.lastIndexOf('.');
        return d < 0 ? "" : name.substring(d).toLowerCase(Locale.ROOT);
    }

    private static String rel(Path root, Path p) {
        try {
            return root.relativize(p).toString().replace('\\', '/');
        } catch (Exception e) {
            return p.toString().replace('\\', '/');
        }
    }

    private static String modFromConfigRel(String rel) {
        String n = rel.replace('\\', '/');
        String[] parts = n.split("/");
        if (parts.length >= 3 && (parts[0].equals("config") || parts[0].equals("defaultconfigs"))) {
            String mid = parts[1];
            // config/Mekanism/foo.toml → Mekanism; defaultconfigs/refinedstorage-server.toml handled below
            if (!mid.contains(".")) {
                return mid;
            }
        }
        if (parts.length >= 2 && (parts[0].equals("config") || parts[0].equals("defaultconfigs"))) {
            String stem = parts[parts.length - 1];
            int d = stem.lastIndexOf('.');
            stem = d > 0 ? stem.substring(0, d) : stem;
            // refinedstorage-server → refinedstorage
            stem = stem.replaceAll("(?i)-(client|common|server)$", "");
            return stem;
        }
        return "config";
    }

    private static String modFromJarName(String jarName) {
        String base = jarName.replaceAll("(?i)\\.jar$", "");
        base = base.replaceAll("(?i)-(?:forge|fabric|quilt)(?:-|$)", "-");
        base = base.replaceAll("-?\\d+(?:\\.\\d+)+(?:[.-].*)?$", "");
        return base.isEmpty() ? jarName : base;
    }

    private static String str(JsonObject o, String k) {
        if (!o.has(k) || o.get(k).isJsonNull()) {
            return "";
        }
        try {
            return o.get(k).getAsString();
        } catch (Exception e) {
            return o.get(k).toString();
        }
    }

    private record Scored(int score, NumberFact fact) {}

    /**
     * Craft recipe refs from jar datapacks ({@code data/<ns>/recipes/…}).
     * Used by AdvisoryChain as RankedAcquire band=script (PackIndex:1386-1387 / :1490-1491).
     */
    public List<String> findCraftRecipeSources(String itemId) {
        List<String> out = new ArrayList<>();
        if (itemId == null || !itemId.contains(":") || gameDir == null) {
            return out;
        }
        String id = itemId.toLowerCase(Locale.ROOT).trim();
        List<String> cached = craftRouteCache.get(id);
        if (cached != null) {
            return cached;
        }
        String ns = id.substring(0, id.indexOf(':'));
        String path = id.substring(id.indexOf(':') + 1);
        String lastSeg = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        Path modsDir = gameDir.resolve("mods");
        if (!Files.isDirectory(modsDir)) {
            craftRouteCache.put(id, List.of());
            return out;
        }
        List<Path> jars;
        try (var stream = Files.list(modsDir)) {
            jars = stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return out;
        }
        int jarN = 0;
        for (Path jar : jars) {
            if (jarN >= MAX_CRAFT_JARS || out.size() >= MAX_CRAFT_ROUTES) {
                break;
            }
            try (ZipFile zf = new ZipFile(jar.toFile())) {
                List<String> recipePaths = new ArrayList<>();
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (e.isDirectory()) {
                        continue;
                    }
                    String n = e.getName().replace('\\', '/');
                    if (!n.startsWith("data/" + ns + "/") || !n.contains("/recipes/")
                            || !n.endsWith(".json") || n.contains("/advancements/")) {
                        continue;
                    }
                    recipePaths.add(n);
                }
                if (recipePaths.isEmpty()) {
                    continue;
                }
                jarN++;
                // Prefer exact path, then filename match, then result.item scan.
                List<String> ordered = new ArrayList<>();
                String exact = "data/" + ns + "/recipes/" + path + ".json";
                if (recipePaths.contains(exact)) {
                    ordered.add(exact);
                }
                for (String n : recipePaths) {
                    if (n.equals(exact)) {
                        continue;
                    }
                    if (n.endsWith("/" + lastSeg + ".json") || n.endsWith("/" + path + ".json")) {
                        ordered.add(n);
                    }
                }
                if (ordered.isEmpty()) {
                    String soft = lastSeg.contains("_")
                            ? lastSeg.substring(0, lastSeg.indexOf('_')) : lastSeg;
                    for (String n : recipePaths) {
                        if (n.contains(lastSeg) || (soft.length() >= 3 && n.contains(soft))) {
                            ordered.add(n);
                        }
                    }
                }
                if (ordered.isEmpty()) {
                    // last resort: capped result scan (ponytail: O(recipes) ceiling → cache)
                    int cap = Math.min(recipePaths.size(), 64);
                    ordered.addAll(recipePaths.subList(0, cap));
                }
                int checked = 0;
                for (String n : ordered) {
                    if (out.size() >= MAX_CRAFT_ROUTES || checked >= 64) {
                        break;
                    }
                    checked++;
                    if (!recipeOutputsItem(zf, n, id)) {
                        continue;
                    }
                    String src = jar.getFileName().toString() + "!" + n;
                    if (!out.contains(src)) {
                        out.add(src);
                    }
                }
            } catch (IOException ignored) {
                // skip jar
            }
        }
        List<String> frozen = List.copyOf(out);
        craftRouteCache.put(id, frozen);
        return frozen;
    }

    private static boolean recipeOutputsItem(ZipFile zf, String entry, String itemId) {
        try {
            ZipEntry e = zf.getEntry(entry);
            if (e == null) {
                return false;
            }
            String text;
            try (InputStream in = zf.getInputStream(e)) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonElement root = JsonParser.parseString(text);
            for (String res : collectResultItems(root)) {
                if (itemId.equalsIgnoreCase(res)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Walk recipe JSON for result item ids (incl. forge:conditional wrappers). */
    static List<String> collectResultItems(JsonElement el) {
        List<String> out = new ArrayList<>();
        collectResultItems(el, out, 0);
        return out;
    }

    private static void collectResultItems(JsonElement el, List<String> out, int depth) {
        if (el == null || el.isJsonNull() || depth > 8) {
            return;
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String s = el.getAsString();
            if (s.contains(":")) {
                out.add(s);
            }
            return;
        }
        if (el.isJsonArray()) {
            for (JsonElement c : el.getAsJsonArray()) {
                collectResultItems(c, out, depth + 1);
            }
            return;
        }
        if (!el.isJsonObject()) {
            return;
        }
        JsonObject o = el.getAsJsonObject();
        if (o.has("result")) {
            JsonElement r = o.get("result");
            if (r.isJsonPrimitive()) {
                collectResultItems(r, out, depth + 1);
            } else if (r.isJsonObject()) {
                JsonObject ro = r.getAsJsonObject();
                if (ro.has("item")) {
                    out.add(ro.get("item").getAsString());
                } else if (ro.has("id")) {
                    out.add(ro.get("id").getAsString());
                } else {
                    collectResultItems(r, out, depth + 1);
                }
            }
        }
        if (o.has("recipes") && o.get("recipes").isJsonArray()) {
            for (JsonElement wrap : o.getAsJsonArray("recipes")) {
                if (wrap.isJsonObject() && wrap.getAsJsonObject().has("recipe")) {
                    collectResultItems(wrap.getAsJsonObject().get("recipe"), out, depth + 1);
                }
            }
        }
    }

    /**
     * @param tier   A1 / A2 / A3 / B
     * @param source {@code file:line} or {@code class#method}
     * @param note   optional honesty note (e.g. defaultconfigs override warning)
     */
    public record NumberFact(
            String key,
            String valueRaw,
            String tier,
            String source,
            String file,
            String mod,
            String note
    ) {
        public NumberFact(
                String key, String valueRaw, String tier, String source, String file, String mod
        ) {
            this(key, valueRaw, tier, source, file, mod, "");
        }

        public NumberFact {
            key = key == null ? "" : key;
            valueRaw = valueRaw == null ? "" : valueRaw;
            tier = tier == null ? "" : tier;
            source = source == null ? "" : source;
            file = file == null ? "" : file;
            mod = mod == null ? "" : mod;
            note = note == null ? "" : note;
        }
    }
}

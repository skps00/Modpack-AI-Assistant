package com.skps9.packai.logic;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * FTB quest title / description / tasks text → C-tier mechanic hints.
 * Clip only; never dump a whole SNBT into the prompt.
 */
public final class QuestMechanicFacts {
    public static final int MAX_FACTS_PER_ITEM = 5;
    public static final int MAX_CLIP = 300;
    public static final String DISCLAIMER = "任務描述（可能未涵蓋全部機制）";
    static final int DEFAULT_SCAN_MAX_FILES = 200;
    static final long DEFAULT_SCAN_MAX_BYTES = 8_388_608L;
    static final long DEFAULT_SCAN_MAX_MS = 8_000L;
    static final int INDEX_SCHEMA_VERSION = 1;
    static final String INDEX_JSON = "quest-index.json";
    private static final long MAX_SNBT_BYTES = 4_000_000L;

    private static final Pattern ITEM_LIKE = Pattern.compile(
            "#?[a-z0-9_]+:[a-z0-9_./-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY = Pattern.compile(
            "\\b(description|title|tasks)\\s*:", Pattern.CASE_INSENSITIVE);

    @FunctionalInterface
    interface ContentReader {
        byte[] read(Path path) throws Exception;
    }

    private static final AtomicBoolean BUILDING = new AtomicBoolean(false);
    private static final AtomicBoolean PENDING_LOGGED = new AtomicBoolean(false);
    private static final AtomicInteger CONTENT_READS = new AtomicInteger();
    private static volatile ContentReader contentReader = Files::readAllBytes;
    private static volatile boolean READY;
    private static volatile boolean PARTIAL;
    private static volatile Map<String, List<String>> ID_TO_RELS = Map.of();
    private static volatile List<String> INDEXED_RELS = List.of();

    private QuestMechanicFacts() {}

    /** Fixture parse — no disk. {@code relPath} like {@code quests/chapters/demo.snbt}. */
    public static List<String> factsForItem(String snbt, String relPath, String itemId) {
        return factsFrom(snbt, relPath, itemId, MAX_FACTS_PER_ITEM);
    }

    public static List<String> factsForItem(Path gameDir, String itemId) {
        if (itemId == null || itemId.isBlank() || gameDir == null) {
            return List.of();
        }
        if (!READY) {
            logPending();
            return List.of();
        }
        String want = itemId.toLowerCase(Locale.ROOT).trim();
        List<String> rels = ID_TO_RELS.get(want);
        if (rels == null || rels.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        Path questsRoot = gameDir.resolve("config").resolve("ftbquests").resolve("quests");
        int parsed = 0;
        for (String rel : rels) {
            if (out.size() >= MAX_FACTS_PER_ITEM || parsed >= MAX_FACTS_PER_ITEM) {
                break;
            }
            Path p = resolveQuestFile(gameDir, questsRoot, rel);
            String text;
            try {
                if (p == null || !Files.isRegularFile(p) || Files.size(p) > MAX_SNBT_BYTES) {
                    continue;
                }
                text = Files.readString(p, StandardCharsets.UTF_8);
            } catch (Exception e) {
                continue;
            }
            parsed++;
            for (String fact : factsFrom(text, rel, want, MAX_FACTS_PER_ITEM - out.size())) {
                if (seen.add(fact)) {
                    out.add(fact);
                }
                if (out.size() >= MAX_FACTS_PER_ITEM) {
                    break;
                }
            }
        }
        return List.copyOf(out);
    }

    public static boolean isReady() {
        return READY;
    }

    public static void ensureStart(Path gameDir) {
        ensureStart(gameDir, DEFAULT_SCAN_MAX_FILES, DEFAULT_SCAN_MAX_BYTES, DEFAULT_SCAN_MAX_MS);
    }

    public static void ensureStart(Path gameDir, int maxFiles) {
        ensureStart(gameDir, maxFiles, DEFAULT_SCAN_MAX_BYTES, DEFAULT_SCAN_MAX_MS);
    }

    public static void ensureStart(Path gameDir, int maxFiles, long maxBytes, long maxMs) {
        if (gameDir == null) {
            return;
        }
        if (READY) {
            return;
        }
        if (!BUILDING.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                buildIndex(gameDir, maxFiles, maxBytes, maxMs);
            } finally {
                BUILDING.set(false);
            }
        }, "packai-quest-index");
        t.setDaemon(true);
        t.start();
    }

    static void resetIndex() {
        READY = false;
        PARTIAL = false;
        ID_TO_RELS = Map.of();
        INDEXED_RELS = List.of();
        BUILDING.set(false);
        PENDING_LOGGED.set(false);
        contentReader = Files::readAllBytes;
    }

    static void setContentReader(ContentReader reader) {
        contentReader = reader == null ? Files::readAllBytes : reader;
    }

    static int contentReadCount() {
        return CONTENT_READS.get();
    }

    static void resetContentReads() {
        CONTENT_READS.set(0);
    }

    static boolean isPartial() {
        return PARTIAL;
    }

    static List<String> indexedRels() {
        return INDEXED_RELS;
    }

    static void buildIndex(Path gameDir, int maxFiles, long maxBytes, long maxMs) {
        long t0 = System.nanoTime();
        int fileCap = Math.max(0, maxFiles);
        long byteCap = Math.max(0L, maxBytes);
        Path questsRoot = gameDir == null
                ? null
                : gameDir.resolve("config").resolve("ftbquests").resolve("quests");
        Path cacheDir = gameDir == null
                ? null
                : gameDir.resolve("config").resolve("packai").resolve("mechanic-cache");
        Path indexFile = cacheDir == null ? null : cacheDir.resolve(INDEX_JSON);
        Map<String, FileMeta> prev = loadIndexJson(indexFile);
        List<FileMeta> metas = new ArrayList<>();
        int skipped = 0;
        long bytes = 0L;
        boolean partial = false;
        List<Path> files = listQuestFiles(questsRoot);
        for (Path p : files) {
            long elapsed = (System.nanoTime() - t0) / 1_000_000L;
            if (maxMs <= 0L || elapsed >= maxMs) {
                partial = true;
                break;
            }
            if (metas.size() >= fileCap) {
                partial = true;
                break;
            }
            long sz = size(p);
            if (byteCap > 0L && bytes + sz > byteCap) {
                partial = true;
                break;
            }
            if (sz > MAX_SNBT_BYTES) {
                continue;
            }
            String rel = relFromQuests(questsRoot, p);
            if (rel.isEmpty() || rel.contains("reward_tables")) {
                continue;
            }
            long mt = mtime(p);
            FileMeta cached = prev.get(rel);
            FileMeta meta;
            if (cached != null && cached.size == sz && cached.mtime == mt) {
                meta = cached;
                skipped++;
            } else {
                try {
                    byte[] raw = readContent(p);
                    String src = new String(raw, StandardCharsets.UTF_8);
                    meta = new FileMeta();
                    meta.rel = rel;
                    meta.size = sz;
                    meta.mtime = mt;
                    meta.ids = extractItemIds(src);
                } catch (Exception e) {
                    continue;
                }
            }
            bytes += sz;
            metas.add(meta);
        }
        Map<String, List<String>> idMap = new LinkedHashMap<>();
        List<String> rels = new ArrayList<>();
        for (FileMeta meta : metas) {
            rels.add(meta.rel);
            for (String id : meta.ids) {
                idMap.computeIfAbsent(id, k -> new ArrayList<>()).add(meta.rel);
            }
        }
        Map<String, List<String>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : idMap.entrySet()) {
            frozen.put(e.getKey(), List.copyOf(e.getValue()));
        }
        ID_TO_RELS = java.util.Collections.unmodifiableMap(frozen);
        INDEXED_RELS = List.copyOf(rels);
        PARTIAL = partial;
        writeIndexJson(indexFile, metas);
        READY = true;
        PENDING_LOGGED.set(false);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        if (partial) {
            logInfo("Pack AI quest index partial files=" + metas.size() + " ms=" + ms);
        }
        logInfo("Pack AI quest index ready files=" + metas.size()
                + " skipped=" + skipped + " ms=" + ms);
    }

    static List<Path> listQuestFiles(Path questsRoot) {
        List<Path> files = new ArrayList<>();
        if (questsRoot == null || !Files.isDirectory(questsRoot)) {
            return files;
        }
        try (Stream<Path> walk = Files.walk(questsRoot)) {
            walk.filter(QuestMechanicFacts::isQuestSnbt).forEach(files::add);
        } catch (Exception ignored) {
            return files;
        }
        files.sort(Comparator.comparing(p -> p.toString().replace('\\', '/')));
        return files;
    }

    private static boolean isQuestSnbt(Path p) {
        if (p == null || !Files.isRegularFile(p)) {
            return false;
        }
        String name = p.getFileName().toString();
        if (!name.endsWith(".snbt")) {
            return false;
        }
        String n = p.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return !n.contains("/reward_tables/");
    }

    private static String relFromQuests(Path questsRoot, Path file) {
        if (questsRoot == null || file == null) {
            return "";
        }
        try {
            return "quests/" + questsRoot.relativize(file).toString().replace('\\', '/');
        } catch (Exception e) {
            return "quests/" + file.getFileName();
        }
    }

    private static Path resolveQuestFile(Path gameDir, Path questsRoot, String rel) {
        if (rel == null || rel.isBlank()) {
            return null;
        }
        String r = rel.replace('\\', '/');
        if (r.startsWith("ftbquests/")) {
            r = r.substring("ftbquests/".length());
        }
        if (r.startsWith("quests/")) {
            return questsRoot.resolve(r.substring("quests/".length()));
        }
        Path direct = gameDir.resolve(r);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        return questsRoot.resolve(r);
    }

    static List<String> extractItemIds(String src) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (src == null || src.isEmpty()) {
            return List.of();
        }
        Matcher m = ITEM_LIKE.matcher(src);
        while (m.find()) {
            String raw = m.group().toLowerCase(Locale.ROOT);
            if (raw.contains(":")) {
                ids.add(raw);
            }
        }
        return List.copyOf(ids);
    }

    private static byte[] readContent(Path p) throws Exception {
        CONTENT_READS.incrementAndGet();
        return contentReader.read(p);
    }

    private static Map<String, FileMeta> loadIndexJson(Path file) {
        Map<String, FileMeta> out = new LinkedHashMap<>();
        if (file == null || !Files.isRegularFile(file)) {
            return out;
        }
        try {
            JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (!o.has("schemaVersion") || o.get("schemaVersion").getAsInt() != INDEX_SCHEMA_VERSION) {
                return out;
            }
            JsonArray arr = o.getAsJsonArray("files");
            if (arr == null) {
                return out;
            }
            for (JsonElement el : arr) {
                FileMeta meta = FileMeta.fromJson(el.getAsJsonObject());
                if (meta.rel != null && !meta.rel.isBlank()) {
                    out.put(meta.rel, meta);
                }
            }
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
        return out;
    }

    private static void writeIndexJson(Path file, List<FileMeta> metas) {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            JsonObject o = new JsonObject();
            o.addProperty("schemaVersion", INDEX_SCHEMA_VERSION);
            JsonArray arr = new JsonArray();
            for (FileMeta meta : metas) {
                arr.add(meta.toJson());
            }
            o.add("files", arr);
            Files.writeString(file, o.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // soft-fail
        }
    }

    private static void logPending() {
        if (!PENDING_LOGGED.compareAndSet(false, true)) {
            return;
        }
        logInfo("Pack AI quest index pending");
    }

    private static void logInfo(String msg) {
        try {
            Class<?> c = Class.forName("com.skps9.packai.PackAiMod");
            Object logger = c.getField("LOGGER").get(null);
            logger.getClass().getMethod("info", String.class).invoke(logger, msg);
        } catch (Throwable ignored) {
            // tests / early
        }
    }

    private static long mtime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static long size(Path p) {
        try {
            return Files.size(p);
        } catch (Exception e) {
            return 0L;
        }
    }

    static final class FileMeta {
        String rel = "";
        long size;
        long mtime;
        List<String> ids = new ArrayList<>();

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("rel", rel);
            o.addProperty("size", size);
            o.addProperty("mtime", mtime);
            JsonArray arr = new JsonArray();
            for (String id : ids) {
                arr.add(id);
            }
            o.add("ids", arr);
            return o;
        }

        static FileMeta fromJson(JsonObject o) {
            FileMeta m = new FileMeta();
            m.rel = o.has("rel") && o.get("rel").isJsonPrimitive() ? o.get("rel").getAsString() : "";
            m.size = o.has("size") ? o.get("size").getAsLong() : 0L;
            m.mtime = o.has("mtime") ? o.get("mtime").getAsLong() : 0L;
            m.ids = new ArrayList<>();
            if (o.has("ids") && o.get("ids").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("ids")) {
                    m.ids.add(e.getAsString());
                }
            }
            return m;
        }
    }

    static List<String> factsFrom(String snbt, String relPath, String itemId, int cap) {
        if (snbt == null || itemId == null || itemId.isBlank() || cap <= 0) {
            return List.of();
        }
        String want = itemId.toLowerCase(Locale.ROOT).trim();
        List<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        Matcher m = KEY.matcher(snbt);
        while (m.find() && out.size() < cap) {
            int colon = m.end();
            int valueStart = skipWs(snbt, colon);
            if (valueStart >= snbt.length()) {
                continue;
            }
            String block = readValue(snbt, valueStart);
            if (block.isEmpty() || !mentions(block, want)) {
                continue;
            }
            String clip = KubeJsMechanicScan.clip(block.replace('\r', ' ').replace('\n', ' '), MAX_CLIP);
            String rel = relPath == null ? "quests/unknown.snbt" : relPath.replace('\\', '/');
            if (rel.startsWith("ftbquests/")) {
                rel = rel.substring("ftbquests/".length());
            }
            String fact = "item:" + want + " -[quest_text]-> " + DISCLAIMER + ": " + clip
                    + " (source:ftbquests/" + rel + " tier:C)";
            if (seen.add(fact)) {
                out.add(fact);
            }
        }
        return List.copyOf(out);
    }

    static boolean mentions(String text, String itemId) {
        if (text == null || itemId == null || itemId.isBlank()) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        String needle = itemId.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from <= t.length() - needle.length()) {
            int i = t.indexOf(needle, from);
            if (i < 0) {
                return false;
            }
            boolean left = i == 0 || !isIdChar(t.charAt(i - 1));
            int end = i + needle.length();
            boolean right = end >= t.length() || !isIdChar(t.charAt(end));
            if (left && right) {
                return true;
            }
            from = i + 1;
        }
        return false;
    }

    private static boolean isIdChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '/' || c == '.' || c == ':';
    }

    private static String readValue(String s, int from) {
        char c = s.charAt(from);
        if (c == '"' || c == '\'') {
            int end = KubeJsMechanicScan.skipQuoted(s, from, c);
            return unquote(s.substring(from, Math.min(s.length(), end)));
        }
        if (c == '[') {
            int end = matching(s, from, '[', ']');
            return collectStrings(s.substring(from, Math.min(s.length(), end + 1)));
        }
        if (c == '{') {
            int end = matching(s, from, '{', '}');
            return collectStrings(s.substring(from, Math.min(s.length(), end + 1)));
        }
        int i = from;
        while (i < s.length() && s.charAt(i) != '\n' && s.charAt(i) != ',') {
            i++;
        }
        return s.substring(from, i).trim();
    }

    private static String collectStrings(String block) {
        StringBuilder sb = new StringBuilder();
        Matcher ids = ITEM_LIKE.matcher(block);
        // Keep human text + ids. Pull quoted strings first.
        int i = 0;
        int n = block.length();
        while (i < n) {
            char c = block.charAt(i);
            if (c == '"' || c == '\'') {
                int end = KubeJsMechanicScan.skipQuoted(block, i, c);
                String q = unquote(block.substring(i, Math.min(n, end)));
                if (!q.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(q);
                }
                i = end;
                continue;
            }
            i++;
        }
        if (sb.length() == 0) {
            while (ids.find()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(ids.group());
            }
        }
        return sb.toString().trim();
    }

    private static String unquote(String raw) {
        String s = raw.trim();
        if (s.length() >= 2) {
            char a = s.charAt(0);
            char b = s.charAt(s.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
                s = s.substring(1, s.length() - 1);
            }
        }
        return s.replace("\\\"", "\"").replace("\\n", " ").trim();
    }

    private static int matching(String s, int open, char openCh, char closeCh) {
        int depth = 0;
        int i = open;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                i = KubeJsMechanicScan.skipQuoted(s, i, c);
                continue;
            }
            if (c == openCh) {
                depth++;
            } else if (c == closeCh) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return n - 1;
    }

    private static int skipWs(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }
}

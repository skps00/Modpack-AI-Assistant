package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Disk format + fingerprint for guidebook index ({@code config/packai/guidebook-index/}).
 * Pure IO / string logic — no ResourceManager / Patchouli (unit-testable).
 * formatVersion 2 = Phase B category / links / titleTokens.
 * formatVersion 3 = also index data-folder patchouli_books from mods jars (Ars etc.).
 */
public final class GuidebookIndexCache {
    public static final int FORMAT_VERSION = 3;
    public static final int MAX_ENTRIES = 20_000;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public record Meta(String mc, String loader, String lang, String modFp) {
        public Meta {
            mc = nz(mc);
            loader = nz(loader);
            lang = nz(lang);
            modFp = nz(modFp);
        }
    }

    public record Document(Meta meta, List<GuidebookEntry> entries) {
        public Document {
            meta = meta == null ? new Meta("", "", "", "") : meta;
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    private GuidebookIndexCache() {}

    public static String fingerprintMods(List<String> modIdAtVersion) {
        return ItemIndexCache.fingerprintMods(modIdAtVersion);
    }

    public static String cacheKey(Meta meta) {
        if (meta == null) {
            return "v" + FORMAT_VERSION + "_unknown";
        }
        String fp = meta.modFp();
        String shortFp = fp.length() <= 12 ? fp : fp.substring(0, 12);
        return "v" + FORMAT_VERSION + "_"
                + sanitize(meta.mc()) + "_"
                + sanitize(meta.loader()) + "_"
                + sanitize(meta.lang()) + "_"
                + sanitize(shortFp);
    }

    public static Path cacheDir(Path gameDir) {
        return gameDir.resolve("config/packai/guidebook-index");
    }

    public static Path cacheFile(Path gameDir, Meta meta) {
        return cacheDir(gameDir).resolve(cacheKey(meta) + ".json");
    }

    public static boolean metaMatches(Meta disk, Meta want) {
        if (disk == null || want == null) {
            return false;
        }
        return Objects.equals(disk.mc(), want.mc())
                && Objects.equals(disk.loader(), want.loader())
                && Objects.equals(disk.lang(), want.lang())
                && Objects.equals(disk.modFp(), want.modFp());
    }

    public static boolean shouldRebuild(Meta disk, Meta want) {
        return !metaMatches(disk, want);
    }

    public static int langRank(String folderLang, String preferredLang) {
        String folder = folderLang == null ? "" : folderLang.trim().toLowerCase(Locale.ROOT);
        String pref = preferredLang == null ? "" : preferredLang.trim().toLowerCase(Locale.ROOT);
        if (folder.isEmpty()) {
            return 2;
        }
        if (!pref.isEmpty() && folder.equals(pref)) {
            return 0;
        }
        if ("en_us".equals(folder)) {
            return 1;
        }
        return -1;
    }

    public static Map<String, List<String>> buildItemMap(List<GuidebookEntry> entries) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (entries == null) {
            return map;
        }
        for (GuidebookEntry e : entries) {
            if (e == null) {
                continue;
            }
            String key = e.stableKey();
            if (key.isBlank()) {
                continue;
            }
            for (String item : e.linkedItems()) {
                if (item == null || item.isBlank()) {
                    continue;
                }
                String id = item.trim().toLowerCase(Locale.ROOT);
                List<String> list = map.computeIfAbsent(id, k -> new ArrayList<>());
                if (!list.contains(key)) {
                    list.add(key);
                }
            }
        }
        return map;
    }

    /** titleToken → entry stableKeys. */
    public static Map<String, List<String>> buildTitleTokenMap(List<GuidebookEntry> entries) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (entries == null) {
            return map;
        }
        for (GuidebookEntry e : entries) {
            if (e == null) {
                continue;
            }
            String key = e.stableKey();
            if (key.isBlank()) {
                continue;
            }
            for (String tok : e.titleTokens()) {
                if (tok == null || tok.isBlank()) {
                    continue;
                }
                String t = tok.toLowerCase(Locale.ROOT);
                List<String> list = map.computeIfAbsent(t, k -> new ArrayList<>());
                if (!list.contains(key)) {
                    list.add(key);
                }
            }
        }
        return map;
    }

    /** categoryId → entry stableKeys (same category string as stored). */
    public static Map<String, List<String>> buildCategoryMap(List<GuidebookEntry> entries) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (entries == null) {
            return map;
        }
        for (GuidebookEntry e : entries) {
            if (e == null || e.categoryId() == null || e.categoryId().isBlank()) {
                continue;
            }
            String cat = e.categoryId().trim().toLowerCase(Locale.ROOT);
            String key = e.stableKey();
            if (key.isBlank()) {
                continue;
            }
            List<String> list = map.computeIfAbsent(cat, k -> new ArrayList<>());
            if (!list.contains(key)) {
                list.add(key);
            }
        }
        return map;
    }

    /** Invert linksOut → linksIn on each entry. Unknown targets ignored. */
    public static List<GuidebookEntry> enrichLinksIn(List<GuidebookEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        Map<String, LinkedHashSet<String>> inbound = new LinkedHashMap<>();
        LinkedHashSet<String> known = new LinkedHashSet<>();
        for (GuidebookEntry e : entries) {
            if (e != null && !e.stableKey().isBlank()) {
                known.add(e.stableKey());
            }
        }
        for (GuidebookEntry e : entries) {
            if (e == null) {
                continue;
            }
            String from = e.stableKey();
            for (String to : e.linksOut()) {
                if (to == null || to.isBlank() || !known.contains(to)) {
                    continue;
                }
                inbound.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(from);
            }
        }
        List<GuidebookEntry> out = new ArrayList<>(entries.size());
        for (GuidebookEntry e : entries) {
            if (e == null) {
                continue;
            }
            LinkedHashSet<String> in = inbound.get(e.stableKey());
            out.add(e.withLinksIn(in == null ? List.of() : List.copyOf(in)));
        }
        return out;
    }

    public static String toJson(Document doc) {
        JsonObject root = new JsonObject();
        root.addProperty("v", FORMAT_VERSION);
        Meta m = doc.meta();
        root.addProperty("mc", m.mc());
        root.addProperty("loader", m.loader());
        root.addProperty("lang", m.lang());
        root.addProperty("modFp", m.modFp());
        root.addProperty("entryCount", doc.entries().size());
        JsonArray arr = new JsonArray();
        int n = 0;
        for (GuidebookEntry e : doc.entries()) {
            if (n >= MAX_ENTRIES) {
                break;
            }
            if (e == null) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("bookNs", e.bookNs());
            o.addProperty("bookId", e.bookId());
            o.addProperty("entryId", e.entryId());
            o.addProperty("lang", e.lang());
            o.addProperty("title", e.title());
            o.addProperty("textClip", e.textClip());
            o.addProperty("sourcePath", e.sourcePath());
            o.addProperty("categoryId", e.categoryId());
            o.add("linkedItems", stringArray(e.linkedItems()));
            o.add("linksOut", stringArray(e.linksOut()));
            o.add("linksIn", stringArray(e.linksIn()));
            o.add("titleTokens", stringArray(e.titleTokens()));
            arr.add(o);
            n++;
        }
        root.add("entries", arr);
        return GSON.toJson(root);
    }

    public static Document parseJson(String json) {
        if (json == null || json.isBlank()) {
            return new Document(new Meta("", "", "", ""), List.of());
        }
        JsonElement el;
        try {
            el = JsonParser.parseString(json);
        } catch (Exception e) {
            return new Document(new Meta("", "", "", ""), List.of());
        }
        if (!el.isJsonObject()) {
            return new Document(new Meta("", "", "", ""), List.of());
        }
        JsonObject root = el.getAsJsonObject();
        int v = root.has("v") && root.get("v").isJsonPrimitive() ? root.get("v").getAsInt() : 0;
        if (v != FORMAT_VERSION) {
            return new Document(new Meta("", "", "", ""), List.of());
        }
        Meta meta = new Meta(str(root, "mc"), str(root, "loader"), str(root, "lang"), str(root, "modFp"));
        List<GuidebookEntry> entries = new ArrayList<>();
        if (root.has("entries") && root.get("entries").isJsonArray()) {
            for (JsonElement row : root.getAsJsonArray("entries")) {
                if (!row.isJsonObject()) {
                    continue;
                }
                JsonObject o = row.getAsJsonObject();
                GuidebookEntry e = new GuidebookEntry(
                        str(o, "bookNs"),
                        str(o, "bookId"),
                        str(o, "entryId"),
                        str(o, "lang"),
                        str(o, "title"),
                        str(o, "textClip"),
                        readStringList(o, "linkedItems"),
                        str(o, "sourcePath"),
                        str(o, "categoryId"),
                        readStringList(o, "linksOut"),
                        readStringList(o, "linksIn"),
                        readStringList(o, "titleTokens"));
                if (e.bookId().isEmpty() && e.entryId().isEmpty()) {
                    continue;
                }
                entries.add(e);
                if (entries.size() >= MAX_ENTRIES) {
                    break;
                }
            }
        }
        return new Document(meta, enrichLinksIn(entries));
    }

    public static Document load(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return new Document(new Meta("", "", "", ""), List.of());
        }
        try {
            return parseJson(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new Document(new Meta("", "", "", ""), List.of());
        }
    }

    public static boolean save(Path file, Document doc) {
        if (file == null || doc == null) {
            return false;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, toJson(doc), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray a = new JsonArray();
        if (values == null) {
            return a;
        }
        for (String id : values) {
            if (id != null && !id.isBlank()) {
                a.add(id);
            }
        }
        return a;
    }

    private static List<String> readStringList(JsonObject o, String key) {
        List<String> linked = new ArrayList<>();
        if (o == null || !o.has(key) || !o.get(key).isJsonArray()) {
            return linked;
        }
        for (JsonElement t : o.getAsJsonArray(key)) {
            if (t.isJsonPrimitive()) {
                String s = t.getAsString();
                if (s != null && !s.isBlank()) {
                    linked.add(s.trim());
                }
            }
        }
        return linked;
    }

    private static String str(JsonObject o, String key) {
        if (o == null || key == null || !o.has(key) || !o.get(key).isJsonPrimitive()) {
            return "";
        }
        try {
            return o.get(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String sanitize(String s) {
        if (s == null || s.isEmpty()) {
            return "x";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '.'
                    || c == '-') {
                b.append(c);
            } else {
                b.append('_');
            }
        }
        return b.length() == 0 ? "x" : b.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}

package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Disk format + fingerprint for Ask item search index ({@code config/packai/item-index/}).
 * Pure IO / string logic — no registry / JEI (unit-testable).
 */
public final class ItemIndexCache {
    public static final int FORMAT_VERSION = 1;
    /** Hard cap so huge JEI lists cannot OOM the client. */
    public static final int MAX_ENTRIES = 80_000;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public record Meta(String mc, String loader, String lang, String modFp, boolean jei) {
        public Meta {
            mc = nz(mc);
            loader = nz(loader);
            lang = nz(lang);
            modFp = nz(modFp);
        }

        /** Backward-compat helper — jei unknown treated as false. */
        public Meta(String mc, String loader, String lang, String modFp) {
            this(mc, loader, lang, modFp, false);
        }
    }

    /** One searchable row (NBT optional SNBT body with braces). */
    public record Entry(String id, String label, String nbt, List<String> schem, String dedupe) {
        public Entry {
            id = nz(id);
            label = label == null ? "" : label;
            nbt = nbt == null ? "" : nbt;
            schem = schem == null ? List.of() : List.copyOf(schem);
            dedupe = dedupe == null ? "" : dedupe;
        }
    }

    public record Document(Meta meta, List<Entry> entries) {
        public Document {
            meta = meta == null ? new Meta("", "", "", "") : meta;
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    private ItemIndexCache() {}

    /** SHA-1 hex of sorted {@code modId@version} lines (empty → empty fp). */
    public static String fingerprintMods(List<String> modIdAtVersion) {
        if (modIdAtVersion == null || modIdAtVersion.isEmpty()) {
            return "";
        }
        List<String> sorted = new ArrayList<>();
        for (String line : modIdAtVersion) {
            if (line != null && !line.isBlank()) {
                sorted.add(line.trim().toLowerCase(Locale.ROOT));
            }
        }
        sorted.sort(String::compareTo);
        return sha1Hex(String.join("\n", sorted));
    }

    /** Safe cache file stem: {@code v1_<mc>_<loader>_<lang>_<fp12>}. */
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
        return gameDir.resolve("config/packai/item-index");
    }

    public static Path cacheFile(Path gameDir, Meta meta) {
        return cacheDir(gameDir).resolve(cacheKey(meta) + ".json");
    }

    /**
     * Match cache identity: mc + loader + lang + modFp.
     * {@code jei} is advisory (upgrade when false→true) and ignored here.
     */
    public static boolean metaMatches(Meta disk, Meta want) {
        if (disk == null || want == null) {
            return false;
        }
        return Objects.equals(disk.mc(), want.mc())
                && Objects.equals(disk.loader(), want.loader())
                && Objects.equals(disk.lang(), want.lang())
                && Objects.equals(disk.modFp(), want.modFp());
    }

    /** True when identity differs; JEI-only upgrade handled by caller. */
    public static boolean shouldRebuild(Meta disk, Meta want) {
        return !metaMatches(disk, want);
    }

    /** Disk was built without JEI but JEI is now available → rebuild once. */
    public static boolean shouldUpgradeForJei(Meta disk, boolean jeiNow) {
        return disk != null && !disk.jei() && jeiNow;
    }

    public static String toJson(Document doc) {
        JsonObject root = new JsonObject();
        root.addProperty("v", FORMAT_VERSION);
        Meta m = doc.meta();
        root.addProperty("mc", m.mc());
        root.addProperty("loader", m.loader());
        root.addProperty("lang", m.lang());
        root.addProperty("modFp", m.modFp());
        root.addProperty("jei", m.jei());
        JsonArray arr = new JsonArray();
        int n = 0;
        for (Entry e : doc.entries()) {
            if (n >= MAX_ENTRIES) {
                break;
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", e.id());
            o.addProperty("label", e.label());
            if (e.nbt() != null && !e.nbt().isEmpty()) {
                o.addProperty("nbt", e.nbt());
            }
            if (e.schem() != null && !e.schem().isEmpty()) {
                JsonArray s = new JsonArray();
                for (String t : e.schem()) {
                    if (t != null && !t.isBlank()) {
                        s.add(t);
                    }
                }
                if (!s.isEmpty()) {
                    o.add("schem", s);
                }
            }
            if (e.dedupe() != null && !e.dedupe().isEmpty()) {
                o.addProperty("dedupe", e.dedupe());
            }
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
        Meta meta = new Meta(
                str(root, "mc"),
                str(root, "loader"),
                str(root, "lang"),
                str(root, "modFp"),
                root.has("jei") && root.get("jei").isJsonPrimitive() && root.get("jei").getAsBoolean());
        List<Entry> entries = new ArrayList<>();
        if (root.has("entries") && root.get("entries").isJsonArray()) {
            for (JsonElement row : root.getAsJsonArray("entries")) {
                if (!row.isJsonObject()) {
                    continue;
                }
                JsonObject o = row.getAsJsonObject();
                String id = str(o, "id");
                if (id.isEmpty()) {
                    continue;
                }
                List<String> schem = new ArrayList<>();
                if (o.has("schem") && o.get("schem").isJsonArray()) {
                    for (JsonElement t : o.getAsJsonArray("schem")) {
                        if (t.isJsonPrimitive()) {
                            String s = t.getAsString();
                            if (s != null && !s.isBlank()) {
                                schem.add(s);
                            }
                        }
                    }
                }
                entries.add(new Entry(id, str(o, "label"), str(o, "nbt"), schem, str(o, "dedupe")));
                if (entries.size() >= MAX_ENTRIES) {
                    break;
                }
            }
        }
        return new Document(meta, entries);
    }

    public static Document load(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return new Document(new Meta("", "", "", ""), List.of());
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return parseJson(json);
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

    private static String sha1Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}

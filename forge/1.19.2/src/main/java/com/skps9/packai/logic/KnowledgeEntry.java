package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * One knowledge-base item entry. Manual JsonObject walk — unknown fields ignored.
 */
public final class KnowledgeEntry {
    public String item = "";
    public final Map<String, String> display = new LinkedHashMap<>();
    public String mod = "";
    public String mc = "";
    public String loader = "";
    public final List<String> modVersions = new ArrayList<>();
    public final List<Fact> obtain = new ArrayList<>();
    public final List<Fact> use = new ArrayList<>();
    public final List<Fact> worn = new ArrayList<>();
    public String notes = "";
    public final List<String> contributors = new ArrayList<>();
    public String updated = "";

    public static final class Fact {
        public String kind = "";
        public String type = "";
        public String mob = "";
        public String container = "";
        public String trigger = "";
        public String effect = "";
        public String slot = "";
        public final List<String> effects = new ArrayList<>();
        public String chance = "";
        public final List<String> requires = new ArrayList<>();
        public String source = "";
        public String tier = "";
        public boolean conflict;
    }

    private KnowledgeEntry() {}

    /** Parse one entry, {@code {"entries":[…]}} , or a top-level array. Never throws. */
    public static List<KnowledgeEntry> parseAll(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root == null || root.isJsonNull()) {
                return List.of();
            }
            List<KnowledgeEntry> out = new ArrayList<>();
            if (root.isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray()) {
                    KnowledgeEntry e = parseOne(el);
                    if (e != null) {
                        out.add(e);
                    }
                }
                return List.copyOf(out);
            }
            if (!root.isJsonObject()) {
                return List.of();
            }
            JsonObject obj = root.getAsJsonObject();
            if (obj.has("entries") && obj.get("entries").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("entries")) {
                    KnowledgeEntry e = parseOne(el);
                    if (e != null) {
                        out.add(e);
                    }
                }
                return List.copyOf(out);
            }
            KnowledgeEntry one = parseOne(obj);
            return one == null ? List.of() : List.of(one);
        } catch (Throwable t) {
            return List.of();
        }
    }

    static KnowledgeEntry parseOne(JsonElement el) {
        if (el == null || !el.isJsonObject()) {
            return null;
        }
        try {
            return parseOne(el.getAsJsonObject());
        } catch (Throwable t) {
            return null;
        }
    }

    static KnowledgeEntry parseOne(JsonObject o) {
        if (o == null) {
            return null;
        }
        KnowledgeEntry e = new KnowledgeEntry();
        e.item = str(o, "item").trim().toLowerCase(Locale.ROOT);
        if (e.item.isEmpty()) {
            return null;
        }
        e.mod = str(o, "mod");
        e.notes = str(o, "notes");
        e.updated = str(o, "updated");
        fillMap(o, "display", e.display);
        fillStrList(o, "contributors", e.contributors);
        if (o.has("applies_to") && o.get("applies_to").isJsonObject()) {
            JsonObject a = o.getAsJsonObject("applies_to");
            e.mc = str(a, "mc");
            e.loader = str(a, "loader");
            fillStrList(a, "mod_versions", e.modVersions);
        }
        fillFacts(o, "obtain", "obtain", e.obtain);
        fillFacts(o, "use", "use", e.use);
        fillFacts(o, "worn", "worn", e.worn);
        return e;
    }

    private static void fillFacts(JsonObject o, String key, String kind, List<Fact> out) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            return;
        }
        for (JsonElement el : o.getAsJsonArray(key)) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            Fact f = fact(el.getAsJsonObject(), kind);
            if (f != null) {
                out.add(f);
            }
        }
    }

    private static Fact fact(JsonObject o, String kind) {
        Fact f = new Fact();
        f.kind = kind;
        f.type = str(o, "type");
        f.mob = str(o, "mob");
        f.container = str(o, "container");
        f.trigger = str(o, "trigger");
        f.effect = str(o, "effect");
        f.slot = str(o, "slot");
        f.chance = anyStr(o, "chance");
        f.source = str(o, "source");
        f.tier = str(o, "tier").toUpperCase(Locale.ROOT);
        f.conflict = bool(o, "conflict");
        fillStrList(o, "effects", f.effects);
        fillStrList(o, "requires", f.requires);
        return f;
    }

    static String str(JsonObject o, String k) {
        if (o == null || k == null || !o.has(k) || o.get(k).isJsonNull()) {
            return "";
        }
        JsonElement el = o.get(k);
        if (el.isJsonPrimitive()) {
            return el.getAsString();
        }
        return "";
    }

    static String anyStr(JsonObject o, String k) {
        if (o == null || k == null || !o.has(k) || o.get(k).isJsonNull()) {
            return "";
        }
        JsonElement el = o.get(k);
        if (el.isJsonPrimitive()) {
            return el.getAsString();
        }
        return "";
    }

    static boolean bool(JsonObject o, String k) {
        if (o == null || k == null || !o.has(k) || !o.get(k).isJsonPrimitive()) {
            return false;
        }
        try {
            return o.get(k).getAsBoolean();
        } catch (Throwable t) {
            return false;
        }
    }

    static void fillMap(JsonObject o, String k, Map<String, String> out) {
        if (!o.has(k) || !o.get(k).isJsonObject()) {
            return;
        }
        JsonObject m = o.getAsJsonObject(k);
        for (Map.Entry<String, JsonElement> e : m.entrySet()) {
            if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                out.put(e.getKey(), e.getValue().getAsString());
            }
        }
    }

    static void fillStrList(JsonObject o, String k, List<String> out) {
        if (!o.has(k) || !o.get(k).isJsonArray()) {
            return;
        }
        JsonArray a = o.getAsJsonArray(k);
        for (JsonElement el : a) {
            if (el != null && el.isJsonPrimitive()) {
                String s = el.getAsString();
                if (s != null && !s.isBlank()) {
                    out.add(s);
                }
            }
        }
    }
}

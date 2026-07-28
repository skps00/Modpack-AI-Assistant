package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Patchouli entry JSON helpers — no Patchouli classes.
 * Match focus item via icon / extra_recipe_mappings / page item fields; extract text pages.
 */
public final class PatchouliEntryScan {
    public static final int DEFAULT_MAX_ENTRIES = 2;
    public static final int DEFAULT_MAX_CHARS = 3000;

    private PatchouliEntryScan() {}

    /** Bare registry id without NBT / damage suffix. Tags keep leading {@code #}. */
    public static String normalizeItemKey(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        int brace = s.indexOf('{');
        if (brace >= 0) {
            s = s.substring(0, brace);
        }
        if (!s.startsWith("#")) {
            int hash = s.indexOf('#');
            if (hash >= 0) {
                s = s.substring(0, hash);
            }
        }
        return s.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean idMentions(String raw, String itemId) {
        String want = normalizeItemKey(itemId);
        String got = normalizeItemKey(raw);
        if (want.isEmpty() || got.isEmpty()) {
            return false;
        }
        return got.equals(want);
    }

    public static boolean referencesItem(JsonObject entry, String itemId) {
        if (entry == null || itemId == null || itemId.isBlank()) {
            return false;
        }
        if (idMentions(stringOrEmpty(entry, "icon"), itemId)) {
            return true;
        }
        if (entry.has("extra_recipe_mappings") && entry.get("extra_recipe_mappings").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : entry.getAsJsonObject("extra_recipe_mappings").entrySet()) {
                if (idMentions(e.getKey(), itemId)) {
                    return true;
                }
            }
        }
        if (!entry.has("pages") || !entry.get("pages").isJsonArray()) {
            return false;
        }
        for (JsonElement pe : entry.getAsJsonArray("pages")) {
            if (!pe.isJsonObject()) {
                continue;
            }
            JsonObject page = pe.getAsJsonObject();
            if (idMentions(stringOrEmpty(page, "item"), itemId)) {
                return true;
            }
            if (page.has("items") && page.get("items").isJsonArray()) {
                for (JsonElement ie : page.getAsJsonArray("items")) {
                    if (ie.isJsonPrimitive() && idMentions(ie.getAsString(), itemId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Plain text from text-like pages; Patchouli macros stripped. */
    public static String extractPlainText(JsonObject entry) {
        if (entry == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        String name = stringOrEmpty(entry, "name");
        if (!name.isBlank()) {
            parts.add(name.trim());
        }
        if (entry.has("pages") && entry.get("pages").isJsonArray()) {
            for (JsonElement pe : entry.getAsJsonArray("pages")) {
                if (!pe.isJsonObject()) {
                    continue;
                }
                JsonObject page = pe.getAsJsonObject();
                if (!isTextPage(page)) {
                    continue;
                }
                String text = stringOrEmpty(page, "text");
                if (!text.isBlank()) {
                    parts.add(stripMacros(text));
                }
            }
        }
        return String.join("\n", parts).trim();
    }

    public static boolean isTextPage(JsonObject page) {
        String type = stringOrEmpty(page, "type").toLowerCase(Locale.ROOT);
        if (type.isEmpty()) {
            return page.has("text");
        }
        return type.equals("text")
                || type.equals("patchouli:text")
                || type.endsWith(":text");
    }

    public static String stripMacros(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.replace("$(br)", "\n").replace("$()", "");
        s = s.replaceAll("\\$\\([^)]*\\)", "");
        return s.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    }

    /**
     * Cap to top {@code maxEntries} non-blank bodies, total {@code maxChars}.
     * Returns bare guide body (no {@code [GUIDE]} header).
     */
    public static String joinCapped(List<String> entryBodies, int maxEntries, int maxChars) {
        if (entryBodies == null || entryBodies.isEmpty() || maxEntries <= 0 || maxChars <= 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int n = 0;
        for (String body : entryBodies) {
            if (body == null || body.isBlank()) {
                continue;
            }
            String chunk = body.trim();
            if (out.length() > 0) {
                if (out.length() + 2 >= maxChars) {
                    break;
                }
                out.append("\n\n");
            }
            int room = maxChars - out.length();
            if (chunk.length() > room) {
                out.append(chunk, 0, Math.max(0, room));
                break;
            }
            out.append(chunk);
            n++;
            if (n >= maxEntries) {
                break;
            }
        }
        return out.toString().trim();
    }

    public static JsonObject parseObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonElement el = JsonParser.parseString(json);
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    /** Score for ranking: icon match > extra_recipe_mappings > page item. */
    public static int matchScore(JsonObject entry, String itemId) {
        if (entry == null) {
            return 0;
        }
        if (idMentions(stringOrEmpty(entry, "icon"), itemId)) {
            return 3;
        }
        if (entry.has("extra_recipe_mappings") && entry.get("extra_recipe_mappings").isJsonObject()) {
            for (String key : entry.getAsJsonObject("extra_recipe_mappings").keySet()) {
                if (idMentions(key, itemId)) {
                    return 2;
                }
            }
        }
        if (referencesItem(entry, itemId)) {
            return 1;
        }
        return 0;
    }

    private static String stringOrEmpty(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        JsonElement el = obj.get(key);
        if (el.isJsonPrimitive()) {
            return el.getAsString();
        }
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            return arr.isEmpty() ? "" : arr.get(0).getAsString();
        }
        return "";
    }
}

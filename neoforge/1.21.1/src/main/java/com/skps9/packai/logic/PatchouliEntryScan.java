package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Patchouli entry JSON helpers — no Patchouli classes.
 * Match focus item via icon / extra_recipe_mappings / page item fields /
 * crafting-page recipe outputs; extract text pages.
 * Structured {@link GuidebookEntry} for disk index (WP1+).
 */
public final class PatchouliEntryScan {
    public static final int DEFAULT_MAX_ENTRIES = 2;
    public static final int DEFAULT_MAX_CHARS = 3000;
    /** Per-entry text clip at index build (Ask still applies total ≤3000). */
    public static final int MAX_TEXT_CLIP = 2000;
    /** Cap linked item ids stored per entry. */
    public static final int MAX_LINKED_ITEMS = 32;
    /** Cap outbound entry links stored per entry. */
    public static final int MAX_LINKS_OUT = 16;
    private static final Pattern LINK_MACRO = Pattern.compile("\\$\\(l:([^)]+)\\)", Pattern.CASE_INSENSITIVE);

    /** Parsed resource path segments for one entry JSON. */
    public record PathInfo(String bookNs, String bookId, String lang, String entryId) {
        public PathInfo {
            bookNs = bookNs == null ? "" : bookNs.trim().toLowerCase(Locale.ROOT);
            bookId = bookId == null ? "" : bookId.trim();
            lang = lang == null ? "" : lang.trim().toLowerCase(Locale.ROOT);
            entryId = entryId == null ? "" : entryId.trim().replace('\\', '/');
        }
    }

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

    /** Text pages + spotlight pages that carry a {@code text} field (WP4). */
    public static boolean isTextLikePage(JsonObject page) {
        if (isTextPage(page)) {
            return true;
        }
        return GuidebookPins.isSpotlightPage(page) && page.has("text");
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

    /**
     * Parse {@code assets|data/<ns>/patchouli_books/<book>/<lang>/entries/<id>.json}
     * or RL-style {@code patchouli_books/<book>/<lang>/entries/<id>.json} (+ optional ns).
     */
    public static PathInfo parseEntryPath(String sourcePath) {
        return parseEntryPath("", sourcePath);
    }

    public static PathInfo parseEntryPath(String resourceNamespace, String sourcePath) {
        String nsHint = resourceNamespace == null ? "" : resourceNamespace.trim().toLowerCase(Locale.ROOT);
        if (sourcePath == null || sourcePath.isBlank()) {
            return new PathInfo(nsHint, "", "", "");
        }
        String p = sourcePath.replace('\\', '/');
        // Drop leading pack root noise
        int assets = indexOfSegment(p, "assets/");
        int data = indexOfSegment(p, "data/");
        int cut = -1;
        if (assets >= 0 && (data < 0 || assets <= data)) {
            cut = assets + "assets/".length();
        } else if (data >= 0) {
            cut = data + "data/".length();
        }
        String bookNs = nsHint;
        if (cut >= 0) {
            int slash = p.indexOf('/', cut);
            if (slash > cut) {
                bookNs = p.substring(cut, slash).toLowerCase(Locale.ROOT);
                p = p.substring(slash + 1);
            }
        }
        int books = p.indexOf("patchouli_books/");
        if (books < 0) {
            return new PathInfo(bookNs, "", "", "");
        }
        p = p.substring(books + "patchouli_books/".length());
        String[] parts = p.split("/");
        if (parts.length < 4) {
            return new PathInfo(bookNs, parts.length > 0 ? parts[0] : "", "", "");
        }
        String bookId = parts[0];
        String lang = parts[1];
        // expect .../entries/<stem...>.json
        int entriesIdx = -1;
        for (int i = 0; i < parts.length; i++) {
            if ("entries".equals(parts[i])) {
                entriesIdx = i;
                break;
            }
        }
        if (entriesIdx < 0 || entriesIdx + 1 >= parts.length) {
            return new PathInfo(bookNs, bookId, lang, "");
        }
        StringBuilder stem = new StringBuilder();
        for (int i = entriesIdx + 1; i < parts.length; i++) {
            String seg = parts[i];
            if (i == parts.length - 1 && seg.endsWith(".json")) {
                seg = seg.substring(0, seg.length() - 5);
            }
            if (seg.isEmpty()) {
                continue;
            }
            if (stem.length() > 0) {
                stem.append('/');
            }
            stem.append(seg);
        }
        return new PathInfo(bookNs, bookId, lang, stem.toString());
    }

    /** Crafting-like Patchouli page ({@code type} …crafting, or recipe/recipe2 without type). */
    public static boolean isCraftingPage(JsonObject page) {
        if (page == null) {
            return false;
        }
        String type = stringOrEmpty(page, "type").toLowerCase(Locale.ROOT);
        if (type.isEmpty()) {
            return page.has("recipe") || page.has("recipe2");
        }
        return type.equals("crafting")
                || type.equals("patchouli:crafting")
                || type.endsWith(":crafting");
    }

    /** {@code recipe} / {@code recipe2} ids on crafting pages (not item ids). */
    public static List<String> collectCraftingRecipeIds(JsonObject entry) {
        if (entry == null || !entry.has("pages") || !entry.get("pages").isJsonArray()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (JsonElement pe : entry.getAsJsonArray("pages")) {
            if (!pe.isJsonObject()) {
                continue;
            }
            JsonObject page = pe.getAsJsonObject();
            if (!isCraftingPage(page)) {
                continue;
            }
            addRecipeId(out, stringOrEmpty(page, "recipe"));
            addRecipeId(out, stringOrEmpty(page, "recipe2"));
        }
        return List.copyOf(out);
    }

    /**
     * Icon + extra_recipe_mappings keys + page item(s) + resolved crafting recipe
     * outputs; normalized, deduped, capped.
     */
    public static List<String> collectLinkedItems(JsonObject entry) {
        return collectLinkedItems(entry, Map.of());
    }

    public static List<String> collectLinkedItems(JsonObject entry, Map<String, String> recipeOutputs) {
        if (entry == null) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        addLinked(out, stringOrEmpty(entry, "icon"));
        if (entry.has("extra_recipe_mappings") && entry.get("extra_recipe_mappings").isJsonObject()) {
            for (String key : entry.getAsJsonObject("extra_recipe_mappings").keySet()) {
                addLinked(out, key);
            }
        }
        if (entry.has("pages") && entry.get("pages").isJsonArray()) {
            for (JsonElement pe : entry.getAsJsonArray("pages")) {
                if (!pe.isJsonObject()) {
                    continue;
                }
                JsonObject page = pe.getAsJsonObject();
                addLinked(out, stringOrEmpty(page, "item"));
                if (page.has("items") && page.get("items").isJsonArray()) {
                    for (JsonElement ie : page.getAsJsonArray("items")) {
                        if (ie.isJsonPrimitive()) {
                            addLinked(out, ie.getAsString());
                        }
                    }
                }
            }
        }
        if (recipeOutputs != null && !recipeOutputs.isEmpty()) {
            for (String rid : collectCraftingRecipeIds(entry)) {
                String item = recipeOutputs.get(rid);
                if (item == null) {
                    item = recipeOutputs.get(rid.toLowerCase(Locale.ROOT));
                }
                addLinked(out, item);
            }
        }
        return clipLinked(out);
    }

    /** Append resolved recipe outputs onto an existing linked-item list (capped). */
    public static List<String> mergeRecipeResults(
            List<String> linked, List<String> recipeIds, Map<String, String> outputs) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (linked != null) {
            for (String id : linked) {
                addLinked(out, id);
            }
        }
        if (recipeIds != null && outputs != null) {
            for (String rid : recipeIds) {
                if (rid == null) {
                    continue;
                }
                String item = outputs.get(rid);
                if (item == null) {
                    item = outputs.get(rid.toLowerCase(Locale.ROOT));
                }
                addLinked(out, item);
            }
        }
        return clipLinked(out);
    }

    /** Text-like pages only (no title); macros stripped; hard-capped. */
    public static String extractTextClip(JsonObject entry, int maxChars) {
        if (entry == null || maxChars <= 0) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (entry.has("pages") && entry.get("pages").isJsonArray()) {
            for (JsonElement pe : entry.getAsJsonArray("pages")) {
                if (!pe.isJsonObject()) {
                    continue;
                }
                JsonObject page = pe.getAsJsonObject();
                if (!isTextLikePage(page)) {
                    continue;
                }
                String text = stringOrEmpty(page, "text");
                if (!text.isBlank()) {
                    parts.add(stripMacros(text));
                }
            }
        }
        String joined = String.join("\n", parts).trim();
        if (joined.length() <= maxChars) {
            return joined;
        }
        return joined.substring(0, maxChars);
    }

    public static String extractTextClip(JsonObject entry) {
        return extractTextClip(entry, MAX_TEXT_CLIP);
    }

    /**
     * Build structured entry from JSON + resource path.
     * Pure-text entries (no linked items) still parse — index stores them; Ask pins only on item hit.
     */
    public static GuidebookEntry toEntry(JsonObject entry, String sourcePath) {
        return toEntry(entry, "", sourcePath);
    }

    public static GuidebookEntry toEntry(JsonObject entry, String resourceNamespace, String sourcePath) {
        PathInfo path = parseEntryPath(resourceNamespace, sourcePath);
        String title = entry == null ? "" : stringOrEmpty(entry, "name").trim();
        String clip = extractTextClip(entry, MAX_TEXT_CLIP);
        List<String> linked = collectLinkedItems(entry);
        String category = entry == null ? "" : stringOrEmpty(entry, "category").trim();
        List<String> linksOut = collectLinksOut(entry, path.bookNs(), path.bookId());
        List<String> titleToks = tokenizeTitle(title, path.entryId());
        String sp = sourcePath == null ? "" : sourcePath.replace('\\', '/');
        return new GuidebookEntry(
                path.bookNs(),
                path.bookId(),
                path.entryId(),
                path.lang(),
                title,
                clip,
                linked,
                sp,
                category,
                linksOut,
                List.of(),
                titleToks);
    }

    /**
     * Stable outbound links only when parseable — no invent.
     * Targets stored as {@code bookNs/bookId/entryId} (same-book default).
     */
    public static List<String> collectLinksOut(JsonObject entry, String bookNs, String bookId) {
        if (entry == null) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String ns = bookNs == null ? "" : bookNs.trim().toLowerCase(Locale.ROOT);
        String book = bookId == null ? "" : bookId.trim();
        if (entry.has("pages") && entry.get("pages").isJsonArray()) {
            for (JsonElement pe : entry.getAsJsonArray("pages")) {
                if (!pe.isJsonObject()) {
                    continue;
                }
                JsonObject page = pe.getAsJsonObject();
                addLinkTarget(out, stringOrEmpty(page, "entry"), ns, book);
                addLinkTarget(out, stringOrEmpty(page, "link"), ns, book);
                // Raw text macros before strip
                String rawText = stringOrEmpty(page, "text");
                if (!rawText.isBlank()) {
                    Matcher m = LINK_MACRO.matcher(rawText);
                    while (m.find()) {
                        addLinkTarget(out, m.group(1), ns, book);
                    }
                }
            }
        }
        if (out.size() <= MAX_LINKS_OUT) {
            return List.copyOf(out);
        }
        List<String> clipped = new ArrayList<>(MAX_LINKS_OUT);
        int n = 0;
        for (String id : out) {
            clipped.add(id);
            if (++n >= MAX_LINKS_OUT) {
                break;
            }
        }
        return clipped;
    }

    /** Lowercase alphanumeric tokens from title + entryId stem (min length 2). */
    public static List<String> tokenizeTitle(String title, String entryId) {
        LinkedHashSet<String> tok = new LinkedHashSet<>();
        addTitleTokens(tok, title);
        if (entryId != null && !entryId.isBlank()) {
            String stem = entryId;
            int slash = stem.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < stem.length()) {
                stem = stem.substring(slash + 1);
            }
            addTitleTokens(tok, stem.replace('_', ' ').replace('-', ' '));
        }
        return List.copyOf(tok);
    }

    private static void addTitleTokens(Set<String> out, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String norm = raw.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ");
        for (String t : norm.split("\\s+")) {
            if (t.length() >= 2) {
                out.add(t);
            }
        }
    }

    private static void addLinkTarget(Set<String> out, String raw, String bookNs, String bookId) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String s = raw.trim().replace('\\', '/');
        // Drop anchor fragments
        int hash = s.indexOf('#');
        if (hash >= 0) {
            s = s.substring(0, hash);
        }
        if (s.isEmpty() || s.startsWith("http")) {
            return;
        }
        // Already stable key bookNs/bookId/entry
        String[] parts = s.split("/");
        if (parts.length >= 3 && parts[0].contains(":") == false
                && !parts[0].isEmpty() && s.indexOf(':') < 0) {
            // could be bookNs/bookId/entry… if first looks like ns — only if 3+ segments and bookNs matches first
            if (parts[0].equalsIgnoreCase(bookNs) && parts[1].equals(bookId)) {
                out.add(s.toLowerCase(Locale.ROOT));
                return;
            }
        }
        // Patchouli $(l:path/to/entry) relative to same book
        if (s.indexOf(':') < 0) {
            if (bookNs.isEmpty() || bookId.isEmpty()) {
                return;
            }
            out.add((bookNs + "/" + bookId + "/" + s).toLowerCase(Locale.ROOT));
            return;
        }
        // ns:path form → treat path as entry under same bookId when ns == bookNs
        int colon = s.indexOf(':');
        String linkNs = s.substring(0, colon).toLowerCase(Locale.ROOT);
        String rest = s.substring(colon + 1);
        if (rest.isBlank()) {
            return;
        }
        if (!bookNs.isEmpty() && !linkNs.equals(bookNs)) {
            // cross-mod entry id without book folder — skip (don't invent bookId)
            return;
        }
        if (bookId.isEmpty()) {
            return;
        }
        out.add((linkNs + "/" + bookId + "/" + rest).toLowerCase(Locale.ROOT));
    }

    private static void addLinked(Set<String> out, String raw) {
        String id = normalizeItemKey(raw);
        if (!id.isEmpty() && !id.startsWith("#")) {
            out.add(id);
        }
    }

    private static void addRecipeId(Set<String> out, String raw) {
        if (raw == null) {
            return;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || !s.contains(":") || s.startsWith("#")) {
            return;
        }
        out.add(s);
    }

    private static List<String> clipLinked(Set<String> out) {
        if (out.size() <= MAX_LINKED_ITEMS) {
            return List.copyOf(out);
        }
        List<String> clipped = new ArrayList<>(MAX_LINKED_ITEMS);
        int n = 0;
        for (String id : out) {
            clipped.add(id);
            if (++n >= MAX_LINKED_ITEMS) {
                break;
            }
        }
        return clipped;
    }

    private static int indexOfSegment(String path, String segment) {
        if (path.startsWith(segment)) {
            return 0;
        }
        int i = path.indexOf('/' + segment);
        return i >= 0 ? i + 1 : -1;
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

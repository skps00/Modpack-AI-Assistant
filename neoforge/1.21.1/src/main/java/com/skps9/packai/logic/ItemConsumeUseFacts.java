package com.skps9.packai.logic;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * PURPOSE facts from datapack {@code minecraft:consume_item} advancements (zip JSON only —
 * no jar decompile). Covers Goety research scrolls etc. that unlock on right-click consume
 * but only show flavor in the item tooltip.
 */
public final class ItemConsumeUseFacts {
    public static final String HEADER = "[CONSUME_USE]";
    static final int MAX_PER_ITEM = 3;
    static final int MAX_ENTRY_BYTES = 256_000;
    static final int MAX_ADV_SCAN = 8_000;

    private static final Object LOCK = new Object();
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);
    private static volatile Path loadedModsDir;
    /** item id (lower) → hits */
    private static volatile Map<String, List<Hit>> byItem = Map.of();

    /**
     * @param advId advancement resource id or path stem
     * @param titleRaw display title (literal or translate key)
     * @param descRaw display description (literal or translate key)
     * @param titleIsKey whether titleRaw is an I18n key
     * @param descIsKey whether descRaw is an I18n key
     */
    public record Hit(
            String advId,
            String titleRaw,
            String descRaw,
            boolean titleIsKey,
            boolean descIsKey
    ) {
        public Hit {
            advId = nz(advId);
            titleRaw = nz(titleRaw);
            descRaw = nz(descRaw);
        }
    }

    private ItemConsumeUseFacts() {}

    /** Live Ask: jar index + I18n resolve. Soft-fails to empty. */
    public static List<String> purposeLinesFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        String id;
        try {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            id = key == null ? "" : key.toString();
        } catch (Throwable t) {
            return List.of();
        }
        if (id.isBlank()) {
            return List.of();
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                ensure(mc.gameDirectory.toPath());
            }
        } catch (Throwable ignored) {
            // headless
        }
        return purposeLinesForItem(id);
    }

    /** Resolve cached hits for an item id (after {@link #ensure} / {@link #indexFromJson}). */
    public static List<String> purposeLinesForItem(String itemId) {
        String want = normalizeId(itemId);
        if (want.isEmpty()) {
            return List.of();
        }
        List<Hit> hits = byItem.get(want);
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(hits.size());
        for (Hit h : hits) {
            String line = formatHit(h);
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    /** Scan {@code gameDir/mods/*.jar} data advancements. Idempotent per mods path. */
    public static void ensure(Path gameDir) {
        if (gameDir == null) {
            return;
        }
        Path mods = gameDir.resolve("mods");
        if (!Files.isDirectory(mods)) {
            return;
        }
        synchronized (LOCK) {
            if (LOADED.get() && mods.equals(loadedModsDir) && !byItem.isEmpty()) {
                return;
            }
            Map<String, List<Hit>> built = scanMods(mods);
            byItem = built;
            loadedModsDir = mods;
            LOADED.set(true);
        }
    }

    /** Test / offline: replace index with hits from one advancement JSON. */
    public static void indexFromJson(String advId, String json) {
        JsonObject root = parseObject(json);
        if (root == null) {
            return;
        }
        Map<String, List<Hit>> map = new LinkedHashMap<>();
        indexAdvancement(advId, root, map);
        synchronized (LOCK) {
            byItem = freeze(map);
            LOADED.set(true);
        }
    }

    /** Clear memory so next {@link #ensure} rescans. */
    public static void reset() {
        synchronized (LOCK) {
            byItem = Map.of();
            loadedModsDir = null;
            LOADED.set(false);
        }
    }

    /**
     * Pure parse: if advancement uses {@code minecraft:consume_item} for {@code wantItemId},
     * return a Hit; else empty. When {@code wantItemId} blank, return hit for each matched item
     * (caller indexes).
     */
    public static List<Hit> parseConsumeItemHits(String advId, JsonObject root) {
        if (root == null || !root.has("criteria") || !root.get("criteria").isJsonObject()) {
            return List.of();
        }
        LinkedHashSet<String> items = new LinkedHashSet<>();
        JsonObject criteria = root.getAsJsonObject("criteria");
        for (Map.Entry<String, JsonElement> e : criteria.entrySet()) {
            if (e.getValue() == null || !e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject crit = e.getValue().getAsJsonObject();
            String trigger = stringOrEmpty(crit, "trigger").toLowerCase(Locale.ROOT);
            if (!"minecraft:consume_item".equals(trigger)) {
                continue;
            }
            JsonObject conditions = crit.has("conditions") && crit.get("conditions").isJsonObject()
                    ? crit.getAsJsonObject("conditions")
                    : null;
            items.addAll(itemsFromConsumeConditions(conditions));
        }
        if (items.isEmpty()) {
            return List.of();
        }
        DisplayText display = readDisplay(root);
        if (display.title().isBlank() && display.desc().isBlank()) {
            // No display → cannot honestly describe unlock; skip (miss > invent).
            return List.of();
        }
        Hit template = new Hit(
                nz(advId),
                display.title(),
                display.desc(),
                display.titleIsKey(),
                display.descIsKey());
        // One hit object reused per item at index time; callers store per-item.
        List<Hit> out = new ArrayList<>(1);
        out.add(template);
        // Stash matched items on advId suffix for indexAdvancement — return template once;
        // indexAdvancement reads items separately.
        return out;
    }

    /** Items listed under consume_item conditions (items[] / item string). */
    public static List<String> itemsFromConsumeConditions(JsonObject conditions) {
        if (conditions == null) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        JsonElement itemEl = conditions.get("item");
        if (itemEl == null) {
            return List.of();
        }
        if (itemEl.isJsonPrimitive()) {
            String id = normalizeId(itemEl.getAsString());
            if (!id.isEmpty()) {
                out.add(id);
            }
            return List.copyOf(out);
        }
        if (!itemEl.isJsonObject()) {
            return List.of();
        }
        JsonObject itemObj = itemEl.getAsJsonObject();
        if (itemObj.has("items")) {
            JsonElement items = itemObj.get("items");
            if (items.isJsonArray()) {
                for (JsonElement ie : items.getAsJsonArray()) {
                    if (ie != null && ie.isJsonPrimitive()) {
                        String id = normalizeId(ie.getAsString());
                        if (!id.isEmpty()) {
                            out.add(id);
                        }
                    }
                }
            } else if (items.isJsonPrimitive()) {
                String id = normalizeId(items.getAsString());
                if (!id.isEmpty()) {
                    out.add(id);
                }
            }
        }
        if (itemObj.has("item") && itemObj.get("item").isJsonPrimitive()) {
            String id = normalizeId(itemObj.get("item").getAsString());
            if (!id.isEmpty()) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    public static String formatHit(Hit hit) {
        if (hit == null) {
            return "";
        }
        String title = resolveText(hit.titleRaw(), hit.titleIsKey());
        String desc = resolveText(hit.descRaw(), hit.descIsKey());
        StringBuilder sb = new StringBuilder(HEADER);
        sb.append(" Right-click / consume");
        if (!title.isBlank()) {
            sb.append(": ").append(title);
        }
        if (!desc.isBlank()) {
            sb.append(title.isBlank() ? ": " : " — ").append(desc);
        }
        if (title.isBlank() && desc.isBlank()) {
            // I18n miss / headless: still pin honest consume use without inventing lore.
            sb.append(": unlocks knowledge / research (datapack consume_item)");
        }
        return sb.toString();
    }

    public static boolean isPurposeGraphFact(String gf) {
        return gf != null && gf.contains("-[consume_item]->");
    }

    /** Graph edge for AskEngine PURPOSE filter (optional). */
    public static String toGraphFact(String itemId, Hit hit) {
        String id = normalizeId(itemId);
        if (id.isEmpty() || hit == null) {
            return "";
        }
        String title = resolveText(hit.titleRaw(), hit.titleIsKey());
        String desc = resolveText(hit.descRaw(), hit.descIsKey());
        String label = !desc.isBlank() ? desc : title;
        if (label.isBlank()) {
            return "";
        }
        return "item:" + id + " -[consume_item]-> " + label;
    }

    static void indexAdvancement(String advId, JsonObject root, Map<String, List<Hit>> map) {
        if (root == null || map == null) {
            return;
        }
        if (!root.has("criteria") || !root.get("criteria").isJsonObject()) {
            return;
        }
        LinkedHashSet<String> items = new LinkedHashSet<>();
        JsonObject criteria = root.getAsJsonObject("criteria");
        for (Map.Entry<String, JsonElement> e : criteria.entrySet()) {
            if (e.getValue() == null || !e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject crit = e.getValue().getAsJsonObject();
            String trigger = stringOrEmpty(crit, "trigger").toLowerCase(Locale.ROOT);
            if (!"minecraft:consume_item".equals(trigger)) {
                continue;
            }
            JsonObject conditions = crit.has("conditions") && crit.get("conditions").isJsonObject()
                    ? crit.getAsJsonObject("conditions")
                    : null;
            items.addAll(itemsFromConsumeConditions(conditions));
        }
        if (items.isEmpty()) {
            return;
        }
        DisplayText display = readDisplay(root);
        if (display.title().isBlank() && display.desc().isBlank()) {
            return;
        }
        Hit hit = new Hit(
                nz(advId),
                display.title(),
                display.desc(),
                display.titleIsKey(),
                display.descIsKey());
        for (String item : items) {
            List<Hit> list = map.computeIfAbsent(item, k -> new ArrayList<>(2));
            if (list.size() >= MAX_PER_ITEM) {
                continue;
            }
            boolean dup = false;
            for (Hit existing : list) {
                if (existing.advId().equalsIgnoreCase(hit.advId())) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                list.add(hit);
            }
        }
    }

    private static Map<String, List<Hit>> scanMods(Path mods) {
        Map<String, List<Hit>> map = new LinkedHashMap<>();
        int scanned = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(mods, "*.jar")) {
            for (Path jar : ds) {
                if (scanned >= MAX_ADV_SCAN) {
                    break;
                }
                scanned += scanJar(jar, map);
            }
        } catch (Exception ignored) {
            return freeze(map);
        }
        return freeze(map);
    }

    private static int scanJar(Path jar, Map<String, List<Hit>> map) {
        int n = 0;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e == null || e.isDirectory()) {
                    continue;
                }
                String path = e.getName().replace('\\', '/');
                String lower = path.toLowerCase(Locale.ROOT);
                if (!isAdvancementEntry(lower)) {
                    continue;
                }
                if (e.getSize() > MAX_ENTRY_BYTES) {
                    continue;
                }
                String json;
                try (InputStream in = zip.getInputStream(e)) {
                    json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                String advId = advIdFromPath(lower);
                indexAdvancement(advId, parseObject(json), map);
                n++;
                if (n >= MAX_ADV_SCAN) {
                    break;
                }
            }
        } catch (Exception ignored) {
            // soft-fail per jar
        }
        return n;
    }

    public static boolean isAdvancementEntry(String pathLower) {
        return pathLower.startsWith("data/")
                && pathLower.contains("/advancements/")
                && pathLower.endsWith(".json");
    }

    public static String advIdFromPath(String pathLower) {
        // data/<ns>/advancements/<path>.json → ns:path
        if (!pathLower.startsWith("data/")) {
            return pathLower;
        }
        String rest = pathLower.substring("data/".length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return rest;
        }
        String ns = rest.substring(0, slash);
        String after = rest.substring(slash + 1);
        String marker = "advancements/";
        int i = after.indexOf(marker);
        if (i < 0) {
            return ns + ":" + after;
        }
        String path = after.substring(i + marker.length());
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
        }
        return ns + ":" + path;
    }

    private record DisplayText(String title, String desc, boolean titleIsKey, boolean descIsKey) {}

    private static DisplayText readDisplay(JsonObject root) {
        if (!root.has("display") || !root.get("display").isJsonObject()) {
            return new DisplayText("", "", false, false);
        }
        JsonObject display = root.getAsJsonObject("display");
        Comp title = readComponent(display.get("title"));
        Comp desc = readComponent(display.get("description"));
        return new DisplayText(title.text(), desc.text(), title.isKey(), desc.isKey());
    }

    private record Comp(String text, boolean isKey) {}

    private static Comp readComponent(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return new Comp("", false);
        }
        if (el.isJsonPrimitive()) {
            return new Comp(el.getAsString().trim(), false);
        }
        if (!el.isJsonObject()) {
            return new Comp("", false);
        }
        JsonObject obj = el.getAsJsonObject();
        String translate = stringOrEmpty(obj, "translate");
        if (!translate.isBlank()) {
            return new Comp(translate.trim(), true);
        }
        String text = stringOrEmpty(obj, "text");
        return new Comp(text.trim(), false);
    }

    private static String resolveText(String raw, boolean isKey) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim();
        if (!isKey) {
            return clip(s, 160);
        }
        try {
            String resolved = I18n.get(s).trim();
            // Unresolved keys usually echo the key (a.b.c); never dump those into PURPOSE.
            if (!resolved.isEmpty() && !resolved.equals(s)) {
                return clip(resolved, 160);
            }
        } catch (Throwable ignored) {
            // headless / missing
        }
        return "";
    }

    private static String clip(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private static Map<String, List<Hit>> freeze(Map<String, List<Hit>> map) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, List<Hit>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, List<Hit>> e : map.entrySet()) {
            frozen.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    static JsonObject parseObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonElement el = JsonParser.parseString(json);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    static String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        int brace = s.indexOf('{');
        if (brace >= 0) {
            s = s.substring(0, brace);
        }
        return s.trim();
    }

    private static String stringOrEmpty(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        JsonElement el = obj.get(key);
        if (el.isJsonPrimitive()) {
            return el.getAsString();
        }
        return "";
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}

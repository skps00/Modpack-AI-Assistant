package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;

/**
 * Tetra material JSON under data/tetra/materials → registry item id for an installed material key.
 * Only {@code material.items[0]} (no tag, no invented dirt) for the forward map. Unique modules
 * with no items fall back to schematic {@code outcomes[].material.items[0]} keyed by
 * {@code moduleVariant} / {@code moduleKey} (materials win via putIfAbsent). Cache per gameDir.
 * Reverse map: item id → {@link Use} lines for Ask {@code [TETRA_USE]} (material / socket /
 * module / modifier). Slots only when schematic JSON lists them — never invent tools.
 */
public final class TetraMaterialItems {
    public static final String USE_HEADER = "[TETRA_USE]";
    private static final Object LOCK = new Object();
    private static final AtomicBoolean LOADED = new AtomicBoolean();
    private static final int MAX_FILES = 4000;
    private static final int MAX_JSON_BYTES = 128_000;
    /** ponytail: cap prompt. Upgrade: per-category pages. */
    static final int MAX_USES = 12;
    static final int MAX_SLOTS = 8;

    private static Path loadedDir;
    private static Map<String, String> byKey = Map.of();
    private static Map<String, List<Use>> byItem = Map.of();

    /**
     * One datapack use of an item. {@code slots}/{@code module} empty when JSON omits them.
     *
     * @param kind     material | socket | module | modifier
     * @param key      material / variant key
     * @param category material category (metal, socket, …)
     * @param slots    comma-joined schematic {@code slots[]} (capped)
     * @param module   {@code moduleKey} or improvement id
     */
    public record Use(String kind, String key, String category, String slots, String module) {
        public Use {
            kind = nz(kind);
            key = nz(key);
            category = nz(category);
            slots = nz(slots);
            module = nz(module);
        }

        String line() {
            StringBuilder sb = new StringBuilder(kind);
            if (!key.isBlank()) {
                sb.append(" key=").append(key);
            }
            if (!category.isBlank()) {
                sb.append(" category=").append(category);
            }
            if (!slots.isBlank()) {
                sb.append(" slots=").append(slots);
            }
            if (!module.isBlank()) {
                if ("modifier".equals(kind)) {
                    sb.append(" improvement=").append(module);
                } else {
                    sb.append(" module=").append(module);
                }
            }
            return sb.toString();
        }
    }

    private TetraMaterialItems() {}

    /**
     * Registry id for a Tetra material / variant string ({@code sword_socket/thunder_gem1_socket}
     * or {@code thunder_gem1_socket}), or empty.
     */
    public static String itemIdFor(String materialId, Path gameDir) {
        if (materialId == null || materialId.isBlank()) {
            return "";
        }
        ensure(gameDir);
        String raw = materialId.trim();
        String hit = byKey.get(raw.toLowerCase(Locale.ROOT));
        if (hit != null && !hit.isBlank()) {
            return hit;
        }
        String last = lastSegment(raw);
        if (!last.isBlank() && !last.equalsIgnoreCase(raw)) {
            hit = byKey.get(last.toLowerCase(Locale.ROOT));
            if (hit != null && !hit.isBlank()) {
                return hit;
            }
        }
        return "";
    }

    /** Ask PURPOSE {@code [TETRA_USE]} for this stack's registry id, or empty. */
    public static String purposeLines(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String id = "";
        try {
            ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
            id = key == null ? "" : key.toString();
        } catch (Throwable ignored) {
            return "";
        }
        Path dir = null;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                dir = mc.gameDirectory.toPath();
            }
        } catch (Throwable ignored) {
            // headless
        }
        return purposeLines(id, dir);
    }

    /** Headless: format reverse hits for {@code itemId} after {@link #ensure} or test index. */
    public static String purposeLines(String itemId, Path gameDir) {
        if (itemId == null || itemId.isBlank()) {
            return "";
        }
        if (gameDir != null) {
            ensure(gameDir);
        }
        return formatUses(byItem.get(itemId.trim().toLowerCase(Locale.ROOT)));
    }

    static String formatUses(List<Use> uses) {
        if (uses == null || uses.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(USE_HEADER);
        int n = 0;
        for (Use u : uses) {
            if (u == null) {
                continue;
            }
            String line = u.line();
            if (line.isBlank()) {
                continue;
            }
            if (n >= MAX_USES) {
                break;
            }
            sb.append('\n').append(line);
            n++;
        }
        return n == 0 ? "" : sb.toString();
    }

    /** Parse {@code key} from a Tetra material JSON body. */
    public static String jsonKey(String json) {
        JsonObject root = parseObject(json);
        if (root == null) {
            return "";
        }
        return str(root, "key");
    }

    /**
     * First {@code material.items[]} entry as {@code ns:path}. Ignores {@code tag} and count suffix.
     */
    public static String firstItemId(String json) {
        return firstItemIdFrom(parseObject(json));
    }

    static String firstItemIdFrom(JsonObject holder) {
        List<String> ids = itemIdsFrom(holder);
        return ids.isEmpty() ? "" : ids.get(0);
    }

    static List<String> itemIdsFrom(JsonObject holder) {
        if (holder == null || !holder.has("material") || !holder.get("material").isJsonObject()) {
            return List.of();
        }
        JsonObject material = holder.getAsJsonObject("material");
        JsonElement items = material.get("items");
        if (!(items instanceof JsonArray arr) || arr.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement e : arr) {
            if (e == null || !e.isJsonPrimitive()) {
                continue;
            }
            String id = e.getAsString();
            if (looksLikeItemId(id)) {
                String t = id.trim();
                if (!out.contains(t)) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    static boolean looksLikeItemId(String raw) {
        if (raw == null) {
            return false;
        }
        String s = raw.trim();
        if (s.isEmpty() || s.length() > 128 || s.indexOf(' ') >= 0) {
            return false;
        }
        int c = s.indexOf(':');
        if (c <= 0 || c >= s.length() - 1) {
            return false;
        }
        if (s.indexOf(':') != s.lastIndexOf(':')) {
            return false;
        }
        return s.indexOf('/') != 0;
    }

    static String lastSegment(String materialId) {
        if (materialId == null || materialId.isBlank()) {
            return "";
        }
        String s = materialId.trim();
        int slash = s.lastIndexOf('/');
        return slash >= 0 ? s.substring(slash + 1) : s;
    }

    public static void ensure(Path gameDir) {
        if (gameDir == null) {
            return;
        }
        synchronized (LOCK) {
            if (LOADED.get() && gameDir.equals(loadedDir) && !byKey.isEmpty()) {
                return;
            }
            Map<String, String> map = new LinkedHashMap<>();
            Map<String, List<Use>> reverse = new LinkedHashMap<>();
            scanTree(gameDir.resolve("kubejs").resolve("data"), map, reverse);
            scanTree(gameDir.resolve("datapacks"), map, reverse);
            scanResourceManager(map, reverse);
            byKey = Map.copyOf(map);
            byItem = freezeReverse(reverse);
            loadedDir = gameDir;
            LOADED.set(true);
        }
    }

    static void scanTree(Path start, Map<String, String> dest) {
        scanTree(start, dest, null);
    }

    static void scanTree(Path start, Map<String, String> dest, Map<String, List<Use>> reverse) {
        scanTree(start, dest, reverse, "/tetra/materials/", true);
        scanTree(start, dest, reverse, "/tetra/schematics/", false);
    }

    static void scanTree(
            Path start,
            Map<String, String> dest,
            Map<String, List<Use>> reverse,
            String infix,
            boolean materials) {
        if (start == null || !Files.isDirectory(start) || dest == null || infix == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(start, 14)) {
            int n = 0;
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                if (n >= MAX_FILES) {
                    break;
                }
                String path = p.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (!path.contains(infix) || !path.endsWith(".json")) {
                    continue;
                }
                n++;
                try {
                    byte[] bytes = Files.readAllBytes(p);
                    if (bytes.length == 0 || bytes.length > MAX_JSON_BYTES) {
                        continue;
                    }
                    String json = new String(bytes, StandardCharsets.UTF_8);
                    if (materials) {
                        indexJson(json, dest, reverse);
                    } else {
                        indexSchematicJson(json, dest, reverse);
                    }
                } catch (Throwable ignored) {
                    // soft per file
                }
            }
        } catch (Throwable ignored) {
            // soft
        }
    }

    static void indexJson(String json, Map<String, String> dest) {
        indexJson(json, dest, null);
    }

    static void indexJson(String json, Map<String, String> dest, Map<String, List<Use>> reverse) {
        JsonObject root = parseObject(json);
        List<String> items = itemIdsFrom(root);
        if (items.isEmpty()) {
            return;
        }
        String item0 = items.get(0);
        String key = jsonKey(json);
        putIfAbsent(dest, key, item0);
        putIfAbsent(dest, lastSegment(key), item0);
        if (reverse == null) {
            return;
        }
        String category = str(root, "category");
        String kind = "socket".equalsIgnoreCase(category) ? "socket" : "material";
        Use use = new Use(kind, key, category, "", "");
        for (String item : items) {
            addUse(reverse, item, use);
        }
    }

    /**
     * Schematic {@code outcomes[]} with {@code material.items[0]} — unique modules (no material
     * JSON items). Ignores {@code requiredTools}. {@code putIfAbsent} so materials win.
     */
    static void indexSchematicJson(String json, Map<String, String> dest) {
        indexSchematicJson(json, dest, null);
    }

    static void indexSchematicJson(String json, Map<String, String> dest, Map<String, List<Use>> reverse) {
        JsonObject root = parseObject(json);
        if (root == null || dest == null || !root.has("outcomes") || !root.get("outcomes").isJsonArray()) {
            return;
        }
        String slots = joinSlots(root);
        for (JsonElement el : root.getAsJsonArray("outcomes")) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject outcome = el.getAsJsonObject();
            List<String> items = itemIdsFrom(outcome);
            String item0 = items.isEmpty() ? "" : items.get(0);
            String variant = str(outcome, "moduleVariant");
            String moduleKey = str(outcome, "moduleKey");
            if (!item0.isBlank()) {
                putIfAbsent(dest, variant, item0);
                putIfAbsent(dest, moduleKey, item0);
                putIfAbsent(dest, lastSegment(moduleKey), item0);
            }
            if (reverse == null || items.isEmpty()) {
                continue;
            }
            String improvement = firstImprovementKey(outcome);
            String kind;
            String module;
            if (!improvement.isBlank()) {
                kind = "modifier";
                module = improvement;
            } else if (!moduleKey.isBlank()) {
                kind = "module";
                module = moduleKey;
            } else {
                kind = "schematic";
                module = "";
            }
            Use use = new Use(kind, variant, "", slots, module);
            for (String item : items) {
                addUse(reverse, item, use);
            }
        }
    }

    static void addUse(Map<String, List<Use>> reverse, String itemId, Use use) {
        if (reverse == null || itemId == null || itemId.isBlank() || use == null) {
            return;
        }
        String id = itemId.trim().toLowerCase(Locale.ROOT);
        List<Use> list = reverse.computeIfAbsent(id, k -> new ArrayList<>());
        String line = use.line();
        for (Use existing : list) {
            if (existing.line().equals(line)) {
                return;
            }
        }
        if (list.size() >= MAX_USES) {
            return;
        }
        list.add(use);
    }

    static String joinSlots(JsonObject root) {
        if (root == null || !root.has("slots") || !root.get("slots").isJsonArray()) {
            return "";
        }
        List<String> slots = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("slots")) {
            if (e == null || !e.isJsonPrimitive()) {
                continue;
            }
            String s = e.getAsString();
            if (s != null && !s.isBlank() && s.length() < 64) {
                slots.add(s.trim());
            }
        }
        if (slots.isEmpty()) {
            return "";
        }
        if (slots.size() <= MAX_SLOTS) {
            return String.join(",", slots);
        }
        // Overflow is its own comma token so +N cannot be read as a slot id.
        return String.join(",", slots.subList(0, MAX_SLOTS)) + ",+" + (slots.size() - MAX_SLOTS);
    }

    static String firstImprovementKey(JsonObject outcome) {
        if (outcome == null || !outcome.has("improvements") || !outcome.get("improvements").isJsonObject()) {
            return "";
        }
        JsonObject im = outcome.getAsJsonObject("improvements");
        for (String k : im.keySet()) {
            if (k != null && !k.isBlank()) {
                return k.trim();
            }
        }
        return "";
    }

    private static void putIfAbsent(Map<String, String> dest, String key, String item) {
        if (dest == null || key == null || key.isBlank() || item == null || item.isBlank()) {
            return;
        }
        dest.putIfAbsent(key.trim().toLowerCase(Locale.ROOT), item.trim());
    }

    private static Map<String, List<Use>> freezeReverse(Map<String, List<Use>> reverse) {
        Map<String, List<Use>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Use>> e : reverse.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            out.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return Map.copyOf(out);
    }

    private static void scanResourceManager(Map<String, String> dest, Map<String, List<Use>> reverse) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return;
            }
            ResourceManager rm = mc.getResourceManager();
            if (rm == null) {
                return;
            }
            indexResourceFolder(rm, "tetra/materials", dest, reverse, true);
            indexResourceFolder(rm, "tetra/schematics", dest, reverse, false);
        } catch (Throwable ignored) {
            // headless / no client
        }
    }

    private static void indexResourceFolder(
            ResourceManager rm,
            String folder,
            Map<String, String> dest,
            Map<String, List<Use>> reverse,
            boolean materials) {
        if (rm == null || folder == null || dest == null) {
            return;
        }
        Map<ResourceLocation, Resource> found =
                rm.listResources(folder, loc -> loc.getPath().endsWith(".json"));
        int n = 0;
        for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
            if (n >= MAX_FILES) {
                break;
            }
            n++;
            Resource res = e.getValue();
            if (res == null) {
                continue;
            }
            try (var in = res.open()) {
                byte[] bytes = in.readAllBytes();
                if (bytes.length == 0 || bytes.length > MAX_JSON_BYTES) {
                    continue;
                }
                String json = new String(bytes, StandardCharsets.UTF_8);
                if (materials) {
                    indexJson(json, dest, reverse);
                } else {
                    indexSchematicJson(json, dest, reverse);
                }
            } catch (Throwable ignored) {
                // soft
            }
        }
    }

    private static JsonObject parseObject(String json) {
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

    private static String str(JsonObject o, String key) {
        if (o == null || key == null || !o.has(key) || !o.get(key).isJsonPrimitive()) {
            return "";
        }
        try {
            String s = o.get(key).getAsString();
            return s == null ? "" : s.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}

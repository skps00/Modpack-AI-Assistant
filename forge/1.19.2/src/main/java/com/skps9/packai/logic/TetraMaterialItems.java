package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Tetra material JSON under data/tetra/materials → registry item id for an installed material key.
 * Only {@code material.items[0]} (no tag, no invented dirt). Unique modules with no items fall
 * back to schematic {@code outcomes[].material.items[0]} keyed by {@code moduleVariant} /
 * {@code moduleKey} (materials win via putIfAbsent). Cache per gameDir.
 */
public final class TetraMaterialItems {
    private static final Object LOCK = new Object();
    private static final AtomicBoolean LOADED = new AtomicBoolean();
    private static final int MAX_FILES = 4000;
    private static final int MAX_JSON_BYTES = 128_000;

    private static Path loadedDir;
    private static Map<String, String> byKey = Map.of();

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
        if (holder == null || !holder.has("material") || !holder.get("material").isJsonObject()) {
            return "";
        }
        JsonObject material = holder.getAsJsonObject("material");
        JsonElement items = material.get("items");
        if (!(items instanceof JsonArray arr) || arr.isEmpty()) {
            return "";
        }
        for (JsonElement e : arr) {
            if (e == null || !e.isJsonPrimitive()) {
                continue;
            }
            String id = e.getAsString();
            if (looksLikeItemId(id)) {
                return id.trim();
            }
        }
        return "";
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
            scanTree(gameDir.resolve("kubejs").resolve("data"), map);
            scanTree(gameDir.resolve("datapacks"), map);
            scanResourceManager(map);
            byKey = Map.copyOf(map);
            loadedDir = gameDir;
            LOADED.set(true);
        }
    }

    static void scanTree(Path start, Map<String, String> dest) {
        scanTree(start, dest, "/tetra/materials/", true);
        scanTree(start, dest, "/tetra/schematics/", false);
    }

    static void scanTree(Path start, Map<String, String> dest, String infix, boolean materials) {
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
                        indexJson(json, dest);
                    } else {
                        indexSchematicJson(json, dest);
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
        String item = firstItemId(json);
        if (item.isBlank()) {
            return;
        }
        String key = jsonKey(json);
        putIfAbsent(dest, key, item);
        putIfAbsent(dest, lastSegment(key), item);
    }

    /**
     * Schematic {@code outcomes[]} with {@code material.items[0]} — unique modules (no material
     * JSON items). Ignores {@code requiredTools}. {@code putIfAbsent} so materials win.
     */
    static void indexSchematicJson(String json, Map<String, String> dest) {
        JsonObject root = parseObject(json);
        if (root == null || dest == null || !root.has("outcomes") || !root.get("outcomes").isJsonArray()) {
            return;
        }
        for (JsonElement el : root.getAsJsonArray("outcomes")) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject outcome = el.getAsJsonObject();
            String item = firstItemIdFrom(outcome);
            if (item.isBlank()) {
                continue;
            }
            String variant = str(outcome, "moduleVariant");
            String moduleKey = str(outcome, "moduleKey");
            putIfAbsent(dest, variant, item);
            putIfAbsent(dest, moduleKey, item);
            putIfAbsent(dest, lastSegment(moduleKey), item);
        }
    }

    private static void putIfAbsent(Map<String, String> dest, String key, String item) {
        if (dest == null || key == null || key.isBlank() || item == null || item.isBlank()) {
            return;
        }
        dest.putIfAbsent(key.trim().toLowerCase(Locale.ROOT), item.trim());
    }

    private static void scanResourceManager(Map<String, String> dest) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return;
            }
            ResourceManager rm = mc.getResourceManager();
            if (rm == null) {
                return;
            }
            indexResourceFolder(rm, "tetra/materials", dest, true);
            indexResourceFolder(rm, "tetra/schematics", dest, false);
        } catch (Throwable ignored) {
            // headless / no client
        }
    }

    private static void indexResourceFolder(
            ResourceManager rm, String folder, Map<String, String> dest, boolean materials) {
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
                    indexJson(json, dest);
                } else {
                    indexSchematicJson(json, dest);
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
}

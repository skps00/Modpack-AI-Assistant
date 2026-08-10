package com.skps9.packai.logic;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;

/**
 * Resolve Tetra schematic datapack JSON for scroll variant keys → PURPOSE facts.
 * <p>
 * Load order (ponytail): gameDir/kubejs/data schematics tree first, then datapacks/, then
 * live {@link ResourceManager}. After PURPOSE build, expand material folder refs
 * ({@code tetra:battery/}) via kubejs/datapacks {@code materials/<category>/*.json}
 * into pick-one {@code install_items} line.
 */
public final class TetraSchematicLookup {
    private TetraSchematicLookup() {}

    /** PURPOSE {@code [SCROLL_UNLOCK]} / {@code [SCROLL_MATERIALS]}; empty if no schematic keys. */
    public static String purposeLines(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        List<String> keys = ItemVariantKeys.schematics(stack);
        if (keys.isEmpty()) {
            return "";
        }
        List<String> prefer = new ArrayList<>();
        for (String k : keys) {
            if (k != null && !k.isBlank() && looksSchematicKey(k)) {
                prefer.add(k.trim());
            }
        }
        if (prefer.isEmpty()) {
            prefer.addAll(keys);
        }
        LinkedHashSet<String> blocks = new LinkedHashSet<>();
        int keyCap = Math.min(prefer.size(), 3);
        for (int i = 0; i < keyCap; i++) {
            String block = loadForKey(prefer.get(i));
            if (block != null && !block.isBlank()) {
                blocks.add(block);
            }
        }
        if (blocks.isEmpty()) {
            return "";
        }
        return String.join("\n", blocks);
    }

    static boolean looksSchematicKey(String raw) {
        String k = raw.toLowerCase(Locale.ROOT);
        return k.contains("/") || k.contains(":") || k.contains("hone") || k.contains("warforge")
                || k.contains("bottle") || k.contains("mirror") || k.contains("gild");
    }

    static String loadForKey(String rawKey) {
        List<String> ids = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        // NFWC: kubejs datapack first (confirmed ~2700 tetra data files).
        tryLoadFromDiskTree(rawKey, ids, bodies, "kubejs", "data");
        if (bodies.isEmpty()) {
            tryLoadFromDiskTree(rawKey, ids, bodies, "datapacks");
        }
        if (bodies.isEmpty()) {
            tryLoadFromResourceManager(rawKey, ids, bodies);
        }
        // Scroll keys like tetra:terra may only unlock other schematics via locked requirement
        // (no schematics/terra.json) — reverse-scan those outcomes for materials/module.
        if (bodies.isEmpty()) {
            tryLoadLockedByKey(rawKey, ids, bodies, "kubejs", "data");
        }
        if (bodies.isEmpty()) {
            tryLoadLockedByKey(rawKey, ids, bodies, "datapacks");
        }
        String purpose = TetraSchematicText.purposeFromLoaded(rawKey, ids, bodies);
        return expandMaterialFolders(purpose);
    }

    /** After PURPOSE: expand {@code ns:category/} → {@code install_items:} from materials JSON. */
    static String expandMaterialFolders(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return purpose == null ? "" : purpose;
        }
        List<String> refs = TetraSchematicText.materialFolderRefs(purpose);
        if (refs.isEmpty()) {
            return purpose;
        }
        LinkedHashSet<String> items = new LinkedHashSet<>();
        for (String ref : refs) {
            String cat = TetraSchematicText.materialFolderCategory(ref);
            if (cat.isBlank()) {
                continue;
            }
            if (!loadMaterialFolderItems(cat, items, "kubejs", "data")) {
                loadMaterialFolderItems(cat, items, "datapacks");
            }
        }
        String line = TetraSchematicText.formatInstallItemsLine(items, refs);
        return TetraSchematicText.withInstallItemsLine(purpose, line);
    }

    /**
     * Scan gameDir roots for {@code materials/<category>/*.json}; return true if any item found.
     */
    private static boolean loadMaterialFolderItems(String category, LinkedHashSet<String> dest, String... roots) {
        Path gameDir = gameDir();
        if (gameDir == null || category == null || category.isBlank() || roots == null || roots.length == 0) {
            return false;
        }
        Path start = gameDir;
        for (String r : roots) {
            start = start.resolve(r);
        }
        if (!Files.isDirectory(start)) {
            return false;
        }
        String needle = "/materials/" + category.toLowerCase(Locale.ROOT).replace('\\', '/') + "/";
        int before = dest.size();
        List<Path> matched = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(start, 14)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                        return s.contains(needle) && s.endsWith(".json");
                    })
                    .forEach(matched::add);
        } catch (Throwable ignored) {
            return dest.size() > before;
        }
        matched.sort((a, b) -> {
            String fa = a.getFileName() != null ? a.getFileName().toString() : "";
            String fb = b.getFileName() != null ? b.getFileName().toString() : "";
            return fa.compareToIgnoreCase(fb);
        });
        int fileCount = 0;
        for (Path p : matched) {
            if (fileCount >= 64) {
                break;
            }
            fileCount++;
            try {
                byte[] bytes = Files.readAllBytes(p);
                if (bytes.length == 0 || bytes.length > 128_000) {
                    continue;
                }
                String json = new String(bytes, StandardCharsets.UTF_8);
                List<String> parsed = TetraSchematicText.itemsFromMaterialJson(json);
                if (!parsed.isEmpty()) {
                    TetraSchematicText.mergeInstallItems(parsed, dest);
                }
            } catch (Throwable ignored) {
                // soft
            }
        }
        return dest.size() > before;
    }

    /** Walk gameDir + roots for matching schematics JSON files. */
    private static void tryLoadFromDiskTree(
            String rawKey, List<String> ids, List<String> bodies, String... roots
    ) {
        Path gameDir = gameDir();
        if (gameDir == null || roots == null || roots.length == 0) {
            return;
        }
        Path start = gameDir;
        for (String r : roots) {
            start = start.resolve(r);
        }
        if (!Files.isDirectory(start)) {
            return;
        }
        // Fast path: **/schematics/<key>.json and **/schematics/**/<key>.json via walk of schematics dirs only.
        String kp = TetraSchematicText.keyPath(rawKey).toLowerCase(Locale.ROOT);
        if (kp.isBlank()) {
            return;
        }
        try (Stream<Path> walk = Files.walk(start, 14)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        if (bodies.size() >= TetraSchematicText.MAX_RESOURCES) {
                            return false;
                        }
                        String s = p.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                        return s.contains("/schematics/") && s.endsWith(".json");
                    })
                    .forEach(p -> {
                        if (bodies.size() >= TetraSchematicText.MAX_RESOURCES) {
                            return;
                        }
                        String norm = p.toString().replace('\\', '/');
                        int schem = norm.toLowerCase(Locale.ROOT).indexOf("/schematics/");
                        if (schem < 0) {
                            return;
                        }
                        String before = norm.substring(0, schem);
                        int dataIdx = before.toLowerCase(Locale.ROOT).lastIndexOf("/data/");
                        String ns;
                        if (dataIdx >= 0) {
                            ns = before.substring(dataIdx + "/data/".length());
                        } else {
                            int slash = before.lastIndexOf('/');
                            ns = slash >= 0 ? before.substring(slash + 1) : before;
                        }
                        String path = "schematics/" + norm.substring(schem + "/schematics/".length());
                        if (path.endsWith(".json")) {
                            path = path.substring(0, path.length() - 5);
                        }
                        if (!TetraSchematicText.resourceMatchesKey(path, rawKey)) {
                            return;
                        }
                        try {
                            byte[] bytes = Files.readAllBytes(p);
                            if (bytes.length == 0 || bytes.length > 256_000) {
                                return;
                            }
                            String json = new String(bytes, StandardCharsets.UTF_8);
                            if (json.isBlank()) {
                                return;
                            }
                            ids.add(ns + ":" + path);
                            bodies.add(json);
                        } catch (Throwable ignored) {
                            // soft
                        }
                    });
        } catch (Throwable ignored) {
            // soft
        }
    }

    /**
     * When no schematic file named for the scroll key, find schematics locked behind that key
     * (e.g. {@code tetra:terra} → {@code schematics/shield/plate/cthulhu.json}).
     */
    private static void tryLoadLockedByKey(
            String rawKey, List<String> ids, List<String> bodies, String... roots
    ) {
        Path gameDir = gameDir();
        if (gameDir == null || rawKey == null || rawKey.isBlank() || roots == null || roots.length == 0) {
            return;
        }
        Path start = gameDir;
        for (String r : roots) {
            start = start.resolve(r);
        }
        if (!Files.isDirectory(start)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(start, 14)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        if (bodies.size() >= TetraSchematicText.MAX_RESOURCES) {
                            return false;
                        }
                        String s = p.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                        return s.contains("/schematics/") && s.endsWith(".json");
                    })
                    .forEach(p -> {
                        if (bodies.size() >= TetraSchematicText.MAX_RESOURCES) {
                            return;
                        }
                        try {
                            byte[] bytes = Files.readAllBytes(p);
                            if (bytes.length == 0 || bytes.length > 256_000) {
                                return;
                            }
                            String json = new String(bytes, StandardCharsets.UTF_8);
                            if (json.isBlank()) {
                                return;
                            }
                            String locked = TetraSchematicText.lockedKeyFromSchematicJson(json);
                            if (!TetraSchematicText.lockedKeyMatches(locked, rawKey)) {
                                return;
                            }
                            String norm = p.toString().replace('\\', '/');
                            int schem = norm.toLowerCase(Locale.ROOT).indexOf("/schematics/");
                            if (schem < 0) {
                                return;
                            }
                            String before = norm.substring(0, schem);
                            int dataIdx = before.toLowerCase(Locale.ROOT).lastIndexOf("/data/");
                            String ns;
                            if (dataIdx >= 0) {
                                ns = before.substring(dataIdx + "/data/".length());
                            } else {
                                int slash = before.lastIndexOf('/');
                                ns = slash >= 0 ? before.substring(slash + 1) : before;
                            }
                            String path = "schematics/" + norm.substring(schem + "/schematics/".length());
                            if (path.endsWith(".json")) {
                                path = path.substring(0, path.length() - 5);
                            }
                            ids.add(ns + ":" + path);
                            bodies.add(json);
                        } catch (Throwable ignored) {
                            // soft
                        }
                    });
        } catch (Throwable ignored) {
            // soft
        }
    }

    private static Path gameDir() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameDirectory == null) {
                return null;
            }
            return mc.gameDirectory.toPath();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void tryLoadFromResourceManager(String rawKey, List<String> ids, List<String> bodies) {
        Minecraft mc;
        try {
            mc = Minecraft.getInstance();
        } catch (Throwable t) {
            return;
        }
        if (mc == null) {
            return;
        }
        ResourceManager rm = null;
        try {
            if (mc.getSingleplayerServer() != null) {
                rm = mc.getSingleplayerServer().getResourceManager();
            }
        } catch (Throwable ignored) {
            // fall through
        }
        if (rm == null) {
            try {
                rm = mc.getResourceManager();
            } catch (Throwable ignored) {
                return;
            }
        }
        if (rm == null) {
            return;
        }
        Map<ResourceLocation, Resource> found;
        try {
            found = rm.listResources(
                    "schematics",
                    loc -> {
                        String p = loc.getPath();
                        return p != null && p.endsWith(".json");
                    });
        } catch (Throwable t) {
            return;
        }
        if (found == null || found.isEmpty()) {
            return;
        }
        for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
            if (bodies.size() >= TetraSchematicText.MAX_RESOURCES) {
                break;
            }
            ResourceLocation loc = e.getKey();
            if (loc == null) {
                continue;
            }
            String path = loc.getPath();
            if (!TetraSchematicText.resourceMatchesKey(path, rawKey)) {
                continue;
            }
            try (var in = e.getValue().open();
                    var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[4096];
                int n;
                int total = 0;
                while ((n = reader.read(buf)) >= 0) {
                    sb.append(buf, 0, n);
                    total += n;
                    if (total > 256_000) {
                        break;
                    }
                }
                String json = sb.toString();
                if (json.isBlank()) {
                    continue;
                }
                ids.add(loc.getNamespace() + ":" + path);
                bodies.add(json);
            } catch (Throwable ignored) {
                // soft-fail one resource
            }
        }
    }
}

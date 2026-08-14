package com.skps9.packai.logic;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Datapack / kubejs / mods-jar recipe JSON → result item id.
 * For guidebook index on a worker thread (no RecipeManager).
 */
public final class RecipeJsonOutputs {
    private RecipeJsonOutputs() {}

    /**
     * {@code data/ns/recipes/path.json} or {@code data/ns/recipe/path.json}
     * (1.21) → {@code ns:path}. Empty when not a recipe file.
     */
    public static String recipeIdFromDataPath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        int data = p.startsWith("data/") ? 0 : p.indexOf("/data/");
        if (data < 0) {
            return "";
        }
        int start = data == 0 ? "data/".length() : data + "/data/".length();
        if (start >= p.length()) {
            return "";
        }
        String rest = p.substring(start);
        int slash = rest.indexOf('/');
        if (slash <= 0) {
            return "";
        }
        String ns = rest.substring(0, slash);
        String after = rest.substring(slash + 1);
        if (after.contains("/advancements/")) {
            return "";
        }
        String folder;
        if (after.startsWith("recipes/")) {
            folder = "recipes/";
        } else if (after.startsWith("recipe/")) {
            folder = "recipe/";
        } else {
            return "";
        }
        String stem = after.substring(folder.length());
        if (!stem.endsWith(".json") || stem.length() <= 5) {
            return "";
        }
        stem = stem.substring(0, stem.length() - 5);
        if (stem.isEmpty() || ns.isEmpty()) {
            return "";
        }
        return ns + ":" + stem;
    }

    /** {@code result} / {@code output} item id, or empty. */
    public static String resultItemFromJson(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (Exception e) {
            return "";
        }
        if (!root.isJsonObject()) {
            return "";
        }
        JsonObject o = root.getAsJsonObject();
        String result = extractItemId(o.get("result"));
        if (result == null) {
            result = extractItemId(o.get("output"));
        }
        return result == null ? "" : result;
    }

    /** Same shape as {@link JarLightIndex} recipe result/output. */
    private static String extractItemId(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (el.isJsonPrimitive()) {
            String s = el.getAsString();
            return s.contains(":") ? s.toLowerCase(Locale.ROOT) : null;
        }
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("item")) {
                return extractItemId(o.get("item"));
            }
            if (o.has("id")) {
                return extractItemId(o.get("id"));
            }
        }
        return null;
    }

    /**
     * Resolve wanted recipe ids to result item ids from {@code mods/*.jar},
     * then {@code kubejs/} and {@code datapacks/} (pack overrides win).
     */
    public static Map<String, String> resolve(Path gameDir, Set<String> wanted) {
        if (gameDir == null || wanted == null || wanted.isEmpty()) {
            return Map.of();
        }
        Set<String> need = new HashSet<>();
        for (String w : wanted) {
            if (w == null || w.isBlank() || !w.contains(":")) {
                continue;
            }
            need.add(w.trim().toLowerCase(Locale.ROOT));
        }
        if (need.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        Path mods = gameDir.resolve("mods");
        if (Files.isDirectory(mods)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(mods, "*.jar")) {
                for (Path jar : ds) {
                    if (out.size() >= need.size()) {
                        break;
                    }
                    scanJar(jar, need, out);
                }
            } catch (Exception ignored) {
                // soft-skip
            }
        }
        scanDirTree(gameDir.resolve("kubejs"), need, out);
        scanDirTree(gameDir.resolve("datapacks"), need, out);
        return out;
    }

    private static void scanJar(Path jar, Set<String> need, Map<String, String> out) {
        if (jar == null || !Files.isRegularFile(jar)) {
            return;
        }
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                if (out.size() >= need.size()) {
                    return;
                }
                ZipEntry entry = en.nextElement();
                if (entry == null || entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                String id = recipeIdFromDataPath(name);
                if (id.isEmpty() || !need.contains(id) || out.containsKey(id)) {
                    continue;
                }
                try (var reader = new InputStreamReader(zf.getInputStream(entry), StandardCharsets.UTF_8)) {
                    StringBuilder sb = new StringBuilder();
                    char[] buf = new char[4096];
                    int n;
                    while ((n = reader.read(buf)) >= 0) {
                        sb.append(buf, 0, n);
                    }
                    String item = resultItemFromJson(sb.toString());
                    if (!item.isEmpty()) {
                        out.put(id, item);
                    }
                } catch (Exception ignored) {
                    // skip bad entry
                }
            }
        } catch (Exception ignored) {
            // soft-skip bad jar
        }
    }

    private static void scanDirTree(Path root, Set<String> need, Map<String, String> out) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root, 16)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                        return s.endsWith(".json")
                                && (s.contains("/recipes/") || s.contains("/recipe/"))
                                && !s.contains("/advancements/");
                    })
                    .forEach(p -> {
                        String id = recipeIdFromDataPath(p.toString());
                        if (id.isEmpty() || !need.contains(id)) {
                            return;
                        }
                        try {
                            byte[] bytes = Files.readAllBytes(p);
                            if (bytes.length == 0 || bytes.length > 256_000) {
                                return;
                            }
                            String item = resultItemFromJson(new String(bytes, StandardCharsets.UTF_8));
                            if (!item.isEmpty()) {
                                out.put(id, item);
                            }
                        } catch (Exception ignored) {
                            // skip
                        }
                    });
        } catch (Exception ignored) {
            // soft-skip
        }
    }
}

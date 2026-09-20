package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;
import com.skps9.packai.config.PackAiConfig;

/** Loose acquire facts plus in-memory jar routes, clipped to Plan B acquire budget. */
public final class AcquireAskTool implements AskTool {
    /** Exact noise keys. Any other code, including {@code blocks/*} and {@code artifact/…}, stays. */
    private static final Set<String> DROP_JAR_ROUTES = Set.of(
            "L|empty",
            "L|loot",
            "L|artifact",
            "L|chest/example_random_source_loot_table",
            "L|items/drinking_hat",
            "L|entity/treasure_goblin",
            "L|advancements/shader_epic");

    @Override
    public String name() {
        return "acquire";
    }

    @Override
    public String description() {
        return "Pack-local acquire path (loot/trade/quest/script). item=mod:id; dump_level=SLIM|OUTPUT. "
                + "Example: acquire(item='minecraft:iron_pickaxe', dump_level='OUTPUT').";
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"item\":{\"type\":\"string\"},\"variant_keys\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"dump_level\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"},\"card_index\":{\"type\":\"string\"}},\"required\":[\"item\"],\"additionalProperties\":false}";
    }

    @Override
    public String toolMissNote(String item) {
        String id = item == null ? "" : item;
        return "[TOOL_MISS] acquire empty — pack index has no loot/trade/quest/script path for '" + id
                + "'. Say unknown/obtain unknown; do not invent.";
    }

    @Override
    public String run(AskToolArgs args) {
        AskToolEnv env = AskToolEnv.current();
        if (env == null || env.index == null || args.itemId.isBlank()) {
            return "";
        }
        try {
            PackIndex.AcquireFacts bundle = env.index.acquireFactsDetailed(
                    args.itemId, args.lang, args.variantKeys);
            // Jar codes first, then loose. LinkedHashSet = stable dedupe (same input, same order).
            List<String> lines = AskToolContext.clipAcquireLines(
                    mergeJarRoutes(args.itemId, args.lang, bundle.lines()), args.question);
            return lines.isEmpty() ? "" : String.join("\n", lines);
        } catch (Throwable t) {
            return "";
        }
    }

    static List<String> mergeJarRoutes(String itemId, String lang, List<String> loose) {
        List<String> jarRoutes = new ArrayList<>();
        List<String> worldgenRoutes = List.of();
        if (PackAiConfig.scanModJars()) {
            LinkedHashSet<String> seenJar = new LinkedHashSet<>();
            for (String code : JarLightIndex.INSTANCE.routeLinesForItem(itemId)) {
                if (code == null || DROP_JAR_ROUTES.contains(code)) {
                    continue;
                }
                addLine(jarRoutes, seenJar, humanJarRoute(lang, code));
            }
            AskToolEnv env = AskToolEnv.current();
            Path gameDir = env == null ? null : env.gameDir;
            worldgenRoutes = humanWorldgenRoutes(lang, WorldgenIndex.routesForItem(itemId, gameDir));
        }
        return mergeRoutes(itemId, lang, loose, jarRoutes, worldgenRoutes);
    }

    /**
     * Stable order: jar routes, then worldgen routes, then loose. First copy wins.
     * Does not clip; caller uses {@link AskToolContext#clipAcquireLines}.
     */
    static List<String> mergeRoutes(
            String itemId,
            String lang,
            List<String> loose,
            List<String> jarRoutes,
            List<String> worldgenRoutes) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        addAll(out, seen, jarRoutes);
        addAll(out, seen, worldgenRoutes);
        addAll(out, seen, loose);
        return out;
    }

    static boolean droppedJarRoute(String code) {
        return code != null && DROP_JAR_ROUTES.contains(code);
    }

    /** {@code L|} uses existing {@code packai.reply.loot_table_obtain}. {@code R|}/{@code U|} use {@link JarLightIndex#formatFact}. */
    private static String humanJarRoute(String lang, String code) {
        if (code.length() >= 2 && code.charAt(0) == 'L' && code.charAt(1) == '|') {
            String table = code.substring(2);
            String line = ReplyLang.lootTableObtain(lang, table);
            if (!table.isEmpty() && (line == null || !line.contains(table))) {
                return (line == null || line.isBlank() ? "Loot table:" : line) + " " + table;
            }
            return line;
        }
        return JarLightIndex.formatFact(code, lang);
    }

    private static void addLine(List<String> out, LinkedHashSet<String> seen, String line) {
        if (line == null || line.isBlank() || !seen.add(line)) {
            return;
        }
        out.add(line);
    }

    private static void addAll(List<String> out, LinkedHashSet<String> seen, List<String> lines) {
        if (lines == null) {
            return;
        }
        for (String line : lines) {
            addLine(out, seen, line);
        }
    }

    /** One human line from one or more raw {@code [WORLDGEN]} rows of the same ore. */
    static String humanWorldgenRoute(String lang, String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return "";
        }
        String feature = null;
        String biome = null;
        String height = null;
        String size = null;
        String count = null;
        for (String line : rawLine.split("\\R")) {
            String rest = worldgenRest(line);
            if (rest == null) {
                continue;
            }
            if (rest.startsWith("placed_feature ") && rest.contains(" in biome ")) {
                int inAt = rest.indexOf(" in biome ");
                String id = rest.substring("placed_feature ".length(), inAt).trim();
                String b = rest.substring(inAt + " in biome ".length()).trim();
                if (feature == null && !id.isEmpty()) {
                    feature = id;
                }
                if (biome == null && !b.isEmpty()) {
                    biome = b;
                }
            } else if (rest.startsWith("placed_feature ")) {
                String id = firstToken(rest.substring("placed_feature ".length()));
                if (feature == null && !id.isEmpty()) {
                    feature = id;
                }
                String c = field(rest, "count");
                String h = field(rest, "height_range");
                if (c != null) {
                    count = c;
                }
                if (h != null) {
                    height = displayHeight(h);
                }
            } else if (rest.startsWith("configured_feature ")) {
                String id = firstToken(rest.substring("configured_feature ".length()));
                if (feature == null && !id.isEmpty()) {
                    feature = id;
                }
                String sz = field(rest, "size");
                if (sz != null) {
                    size = sz;
                }
            }
        }
        String dim = biome == null || biome.isEmpty() ? null : WorldgenIndex.dimensionOf(biome);
        if (feature == null && biome == null && height == null && size == null && count == null) {
            return "";
        }
        return ReplyLang.worldgenOre(lang, feature, biome, height, size, count, dim);
    }

    static List<String> humanWorldgenRoutes(String lang, List<String> rawLines) {
        if (rawLines == null || rawLines.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> groups = new LinkedHashMap<>();
        Map<String, String> configuredOwner = new LinkedHashMap<>();
        List<String> configured = new ArrayList<>();
        for (String line : rawLines) {
            String rest = worldgenRest(line);
            if (rest == null) {
                continue;
            }
            if (rest.startsWith("configured_feature ")) {
                configured.add(line);
                continue;
            }
            if (!rest.startsWith("placed_feature ")) {
                continue;
            }
            String id = placedId(rest);
            if (id.isEmpty()) {
                continue;
            }
            if (rest.contains(" configured=")) {
                String cfg = field(rest, "configured");
                if (cfg != null) {
                    configuredOwner.put(cfg, id);
                }
            }
            groups.computeIfAbsent(id, k -> new ArrayList<>()).add(line);
        }
        for (String line : configured) {
            String rest = worldgenRest(line);
            String id = firstToken(rest.substring("configured_feature ".length()));
            String key = configuredOwner.getOrDefault(id, id);
            if (key.isEmpty()) {
                continue;
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
        }
        List<String> out = new ArrayList<>();
        for (List<String> group : groups.values()) {
            String human = humanWorldgenRoute(lang, String.join("\n", group));
            if (human != null && !human.isBlank()) {
                out.add(human);
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private static String worldgenRest(String line) {
        if (line == null) {
            return null;
        }
        String t = line.trim();
        String prefix = WorldgenFacts.HEADER + " ";
        if (!t.startsWith(prefix)) {
            return null;
        }
        return t.substring(prefix.length());
    }

    private static String placedId(String rest) {
        String body = rest.substring("placed_feature ".length());
        int inBiome = body.indexOf(" in biome ");
        if (inBiome >= 0) {
            return body.substring(0, inBiome).trim();
        }
        return firstToken(body);
    }

    private static String firstToken(String body) {
        String s = body == null ? "" : body.trim();
        int sp = s.indexOf(' ');
        return sp < 0 ? s : s.substring(0, sp);
    }

    private static String field(String rest, String key) {
        String mark = " " + key + "=";
        int i = rest.indexOf(mark);
        if (i < 0) {
            return null;
        }
        int start = i + mark.length();
        int next = rest.length();
        for (String k : new String[] {"configured", "count", "height_range", "type", "size"}) {
            int j = rest.indexOf(" " + k + "=", start);
            if (j >= 0 && j < next) {
                next = j;
            }
        }
        String v = rest.substring(start, next).trim();
        return v.isEmpty() ? null : v;
    }

    /** Drop a literal {@code absolute } prefix. Keep {@code above_bottom } / {@code below_top }. */
    static String displayHeight(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int dots = raw.indexOf("..");
        if (dots < 0) {
            return stripAbsolute(raw.trim());
        }
        return stripAbsolute(raw.substring(0, dots).trim()) + ".." + stripAbsolute(raw.substring(dots + 2).trim());
    }

    private static String stripAbsolute(String part) {
        String prefix = "absolute ";
        return part.startsWith(prefix) ? part.substring(prefix.length()) : part;
    }
}

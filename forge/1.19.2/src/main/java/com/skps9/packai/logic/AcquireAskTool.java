package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        if (PackAiConfig.scanModJars()) {
            for (String code : JarLightIndex.INSTANCE.routeLinesForItem(itemId)) {
                if (code == null || DROP_JAR_ROUTES.contains(code)) {
                    continue;
                }
                addLine(out, seen, humanJarRoute(lang, code));
            }
        }
        if (loose != null) {
            for (String line : loose) {
                addLine(out, seen, line);
            }
        }
        return out;
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
}

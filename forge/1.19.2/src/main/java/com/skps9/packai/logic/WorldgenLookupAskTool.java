package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.List;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;

/** Pack-local structure / biome / ore-gen lookup. FACT {@code [WORLDGEN]} pin stays as fallback. */
public final class WorldgenLookupAskTool implements AskTool {
    @Override
    public String name() {
        return "worldgen_lookup";
    }

    @Override
    public String description() {
        return "Worldgen/ore/feature lookup. item=mod:id or query.";
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"item\":{\"type\":\"string\"},\"variant_keys\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"dump_level\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"},\"card_index\":{\"type\":\"string\"}},\"required\":[\"item\"],\"additionalProperties\":false}";
    }

    @Override
    public String run(AskToolArgs args) {
        String q = args == null ? "" : firstNonBlank(args.itemId, args.question);
        Path dir = args == null ? null : args.gameDir;
        if (dir == null) {
            AskToolEnv env = AskToolEnv.current();
            dir = env == null ? null : env.gameDir;
        }
        String lang = args == null || args.lang.isBlank() ? "zh_tw" : args.lang;
        try {
            List<String> lines = WorldgenIndex.lookup(q, dir, lang);
            if (lines == null || lines.isEmpty()) {
                return "";
            }
            return AskToolContext.clipChars(String.join("\n", lines), 1600);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b == null ? "" : b.trim();
    }
}

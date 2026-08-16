package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.List;

/** Pack-local structure / biome / ore-gen lookup. FACT {@code [WORLDGEN]} pin stays as fallback. */
public final class WorldgenLookupAskTool implements AskTool {
    @Override
    public String name() {
        return "worldgen_lookup";
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

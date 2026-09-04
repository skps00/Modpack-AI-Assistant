package com.skps9.packai.logic;

import java.util.List;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;

/** Thin wrapper: {@link PackIndex#acquireFactsDetailed} clipped to Plan B acquire budget. */
public final class AcquireAskTool implements AskTool {
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
    public String run(AskToolArgs args) {
        AskToolEnv env = AskToolEnv.current();
        if (env == null || env.index == null || args.itemId.isBlank()) {
            return "";
        }
        try {
            PackIndex.AcquireFacts bundle = env.index.acquireFactsDetailed(
                    args.itemId, args.lang, args.variantKeys);
            List<String> lines = AskToolContext.clipAcquireLines(bundle.lines(), args.question);
            return lines.isEmpty() ? "" : String.join("\n", lines);
        } catch (Throwable t) {
            return "";
        }
    }
}

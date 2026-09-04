package com.skps9.packai.logic;

import java.util.List;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;

/** Thin wrapper: {@link ItemConsumeUseFacts#purposeLinesFor}. */
public final class ConsumeUseAskTool implements AskTool {
    @Override
    public String name() {
        return "consume_use";
    }

    @Override
    public String description() {
        return "How to use via right-click consume. item=mod:id.";
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"item\":{\"type\":\"string\"},\"variant_keys\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"dump_level\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"},\"card_index\":{\"type\":\"string\"}},\"required\":[\"item\"],\"additionalProperties\":false}";
    }

    @Override
    public String run(AskToolArgs args) {
        AskToolEnv env = AskToolEnv.current();
        try {
            List<String> lines;
            if (env != null && env.stack != null && !env.stack.isEmpty()) {
                lines = ItemConsumeUseFacts.purposeLinesFor(env.stack);
            } else if (!args.itemId.isBlank()) {
                lines = ItemConsumeUseFacts.purposeLinesForItem(args.itemId);
            } else {
                lines = List.of();
            }
            if (lines == null || lines.isEmpty()) {
                return "";
            }
            return AskToolContext.clipChars(String.join("\n", lines), 800);
        } catch (Throwable t) {
            return "";
        }
    }
}

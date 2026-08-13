package com.skps9.packai.logic;

import java.util.List;

/** One allowlisted tool invocation (native tool_calls or {@code [[tools]]} JSON). */
public record AskToolCall(String name, String itemId, String dumpLevel, List<String> variantKeys) {
    public AskToolCall {
        name = name == null ? "" : name;
        itemId = itemId == null ? "" : itemId;
        dumpLevel = dumpLevel == null ? "" : dumpLevel;
        variantKeys = variantKeys == null ? List.of() : List.copyOf(variantKeys);
    }
}

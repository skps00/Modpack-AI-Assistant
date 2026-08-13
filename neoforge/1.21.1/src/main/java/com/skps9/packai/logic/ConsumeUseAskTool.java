package com.skps9.packai.logic;

import java.util.List;

/** Thin wrapper: {@link ItemConsumeUseFacts#purposeLinesFor}. */
public final class ConsumeUseAskTool implements AskTool {
    @Override
    public String name() {
        return "consume_use";
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

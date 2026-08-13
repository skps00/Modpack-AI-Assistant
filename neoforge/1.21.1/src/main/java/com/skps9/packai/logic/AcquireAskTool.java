package com.skps9.packai.logic;

import java.util.List;

/** Thin wrapper: {@link PackIndex#acquireFactsDetailed} clipped to Plan B acquire budget. */
public final class AcquireAskTool implements AskTool {
    @Override
    public String name() {
        return "acquire";
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

package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.List;

/**
 * Canonical tool args for fingerprinting. No Minecraft types — adapters read live
 * stack / index from {@link AskToolLoop#env()}.
 */
public final class AskToolArgs {
    public final String itemId;
    public final String dumpLevel;
    public final List<String> variantKeys;
    public final String question;
    public final String lang;
    public final Path gameDir;
    public final List<String> scanners;
    public final long deadlineMs;

    public AskToolArgs(
            String itemId,
            String dumpLevel,
            List<String> variantKeys,
            String question,
            String lang,
            Path gameDir,
            List<String> scanners,
            long deadlineMs
    ) {
        this.itemId = itemId == null ? "" : itemId;
        this.dumpLevel = dumpLevel == null ? "" : dumpLevel;
        this.variantKeys = variantKeys == null ? List.of() : List.copyOf(variantKeys);
        this.question = question == null ? "" : question;
        this.lang = lang == null ? "" : lang;
        this.gameDir = gameDir;
        this.scanners = scanners == null ? List.of() : List.copyOf(scanners);
        this.deadlineMs = deadlineMs;
    }

    public static AskToolArgs from(AskLoopState state) {
        return from(state, state.dumpLevel(), state.variantKeys());
    }

    public static AskToolArgs from(AskLoopState state, String dumpLevel, List<String> variantKeys) {
        return new AskToolArgs(
                state.itemId(),
                dumpLevel,
                variantKeys,
                state.question(),
                state.lang(),
                state.gameDir(),
                state.scanners(),
                state.deadlineMs());
    }
}

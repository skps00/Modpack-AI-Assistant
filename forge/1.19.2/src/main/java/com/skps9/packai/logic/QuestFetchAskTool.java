package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.List;

/** Thin wrapper: {@link QuestGuide} index + match, clipped. */
public final class QuestFetchAskTool implements AskTool {
    @Override
    public String name() {
        return "quest_fetch";
    }

    @Override
    public String run(AskToolArgs args) {
        Path dir = args.gameDir;
        AskToolEnv env = AskToolEnv.current();
        if (dir == null && env != null) {
            dir = env.gameDir;
        }
        List<String> scanners = args.scanners;
        if ((scanners == null || scanners.isEmpty()) && env != null) {
            scanners = env.scanners;
        }
        if (dir == null) {
            return "";
        }
        try {
            List<QuestGuide.Hit> all = QuestGuide.index(dir, scanners, args.lang);
            QuestGuide.MatchResult match = QuestGuide.matchResult(
                    all, args.question, args.itemId, List.of(), args.variantKeys);
            if (match.hits().isEmpty()) {
                return "";
            }
            return QuestGuide.formatGuide(
                    match.hits(), false, null, match.totalMatched(), false, args.lang);
        } catch (Throwable t) {
            return "";
        }
    }
}

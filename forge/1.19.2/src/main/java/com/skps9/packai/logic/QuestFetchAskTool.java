package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.List;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;

/** Thin wrapper: {@link QuestGuide} index + match, clipped. */
public final class QuestFetchAskTool implements AskTool {
    @Override
    public String name() {
        return "quest_fetch";
    }

    @Override
    public String description() {
        return "Fetch quest-book entry. item=mod:id or query=text.";
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"item\":{\"type\":\"string\"},\"variant_keys\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"dump_level\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"},\"card_index\":{\"type\":\"string\"}},\"required\":[\"item\"],\"additionalProperties\":false}";
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

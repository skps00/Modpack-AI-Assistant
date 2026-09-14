package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.List;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;

/** On-demand local knowledge facts. Miss returns explicit 未收錄 — do not invent. */
public final class KnowledgeLookupAskTool implements AskTool {
    public static final String MISS = "未收錄";

    @Override
    public String name() {
        return "knowledge_lookup";
    }

    @Override
    public String description() {
        return "Local Pack AI knowledge-base facts for one item id (obtain/use/worn, source+tier). "
                + "item=mod:id required. Empty store → 未收錄; do not invent.";
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"item\":{\"type\":\"string\"},\"variant_keys\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"dump_level\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"},\"card_index\":{\"type\":\"string\"}},\"required\":[\"item\"],\"additionalProperties\":false}";
    }

    @Override
    public String toolMissNote(String item) {
        String id = item == null ? "" : item;
        return "[TOOL_MISS] knowledge_lookup empty — no knowledge entry for '" + id
                + "'. State not listed; do not invent.";
    }

    @Override
    public String run(AskToolArgs args) {
        String id = args == null ? "" : args.itemId;
        if (id == null || id.isBlank()) {
            return "";
        }
        Path dir = args.gameDir;
        if (dir == null) {
            AskToolEnv env = AskToolEnv.current();
            dir = env == null ? null : env.gameDir;
        }
        try {
            List<String> facts = KnowledgeLookup.factsForItem(dir, id, KnowledgeLookup.MAX_FACTS);
            if (facts == null || facts.isEmpty()) {
                return MISS;
            }
            return AskToolContext.clipChars(String.join("\n", facts), 1600);
        } catch (Throwable t) {
            return MISS;
        }
    }
}

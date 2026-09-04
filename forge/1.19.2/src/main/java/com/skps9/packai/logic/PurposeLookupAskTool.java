package com.skps9.packai.logic;

import java.util.List;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;

/** On-demand PURPOSE block. FACT pin stays when tools off / unused. */
public final class PurposeLookupAskTool implements AskTool {
    @Override
    public String name() {
        return "purpose_lookup";
    }

    @Override
    public String description() {
        return "Item purpose/how-to-use facts. item=mod:id.";
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"item\":{\"type\":\"string\"},\"variant_keys\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"dump_level\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"},\"card_index\":{\"type\":\"string\"}},\"required\":[\"item\"],\"additionalProperties\":false}";
    }

    @Override
    public String run(AskToolArgs args) {
        AskToolEnv env = AskToolEnv.current();
        String tip = env == null || env.purposeTooltip == null ? "" : env.purposeTooltip;
        try {
            String block = AskPurposeContext.buildPurposeBlock(tip, List.of(), null);
            return block == null ? "" : AskToolContext.clipChars(block, 1600);
        } catch (Throwable t) {
            return "";
        }
    }
}

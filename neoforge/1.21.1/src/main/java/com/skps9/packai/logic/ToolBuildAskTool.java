package com.skps9.packai.logic;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;

import net.minecraft.world.item.ItemStack;

/** On-demand {@code [TOOL_BUILD]}. FACT pin stays when tools off / unused. */
public final class ToolBuildAskTool implements AskTool {
    @Override
    public String name() {
        return "tool_build";
    }

    @Override
    public String description() {
        return "Tetra tool build parts/slots. item=mod:id.";
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"item\":{\"type\":\"string\"},\"variant_keys\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"dump_level\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"},\"card_index\":{\"type\":\"string\"}},\"required\":[\"item\"],\"additionalProperties\":false}";
    }

    @Override
    public String toolMissNote(String item) {
        String id = item == null ? "" : item;
        return "[TOOL_MISS] tool_build empty — no Tetra build parts for '" + id
                + "'. Check JEI recipe instead; do not invent.";
    }

    @Override
    public String run(AskToolArgs args) {
        AskToolEnv env = AskToolEnv.current();
        ItemStack stack = env == null ? ItemStack.EMPTY : env.stack;
        try {
            String block = ModularToolScan.purposeLines(stack);
            return block == null ? "" : AskToolContext.clipChars(block, 1200);
        } catch (Throwable t) {
            return "";
        }
    }
}

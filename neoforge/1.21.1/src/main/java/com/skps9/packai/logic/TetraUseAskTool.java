package com.skps9.packai.logic;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;

import net.minecraft.world.item.ItemStack;

/** On-demand {@code [TETRA_USE]}. FACT pin stays when tools off / unused. */
public final class TetraUseAskTool implements AskTool {
    @Override
    public String name() {
        return "tetra_use";
    }

    @Override
    public String description() {
        return "Tetra workbench install/use. item=mod:id.";
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"item\":{\"type\":\"string\"},\"variant_keys\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"dump_level\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"},\"card_index\":{\"type\":\"string\"}},\"required\":[\"item\"],\"additionalProperties\":false}";
    }

    @Override
    public String run(AskToolArgs args) {
        AskToolEnv env = AskToolEnv.current();
        ItemStack stack = env == null ? ItemStack.EMPTY : env.stack;
        try {
            String block = TetraMaterialItems.purposeLines(stack);
            return block == null ? "" : AskToolContext.clipChars(block, 1200);
        } catch (Throwable t) {
            return "";
        }
    }
}

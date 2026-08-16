package com.skps9.packai.logic;

import net.minecraft.world.item.ItemStack;

/** On-demand {@code [TOOL_BUILD]}. FACT pin stays when tools off / unused. */
public final class ToolBuildAskTool implements AskTool {
    @Override
    public String name() {
        return "tool_build";
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

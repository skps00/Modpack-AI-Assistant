package com.skps9.packai.logic;

import net.minecraft.world.item.ItemStack;

/** On-demand {@code [TETRA_USE]}. FACT pin stays when tools off / unused. */
public final class TetraUseAskTool implements AskTool {
    @Override
    public String name() {
        return "tetra_use";
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

package com.skps9.packai.logic;

import com.skps9.packai.client.jei.AskJeiClient;

import net.minecraft.world.item.ItemStack;

/** Thin wrapper: {@link com.skps9.packai.client.jei.JeiLookup#summarize} on the client thread. */
public final class JeiLookupAskTool implements AskTool {
    @Override
    public String name() {
        return "jei_lookup";
    }

    @Override
    public String run(AskToolArgs args) {
        AskToolEnv env = AskToolEnv.current();
        ItemStack stack = env == null ? ItemStack.EMPTY : env.stack;
        if (args != null && args.itemId != null && !args.itemId.isBlank()) {
            ItemStack named = ItemResolver.stackFromId(args.itemId);
            if (!named.isEmpty()) {
                stack = named;
            }
        }
        AskToolContext.JeiDumpLevel level = AskToolContext.parseJeiDumpLevel(
                args.dumpLevel == null || args.dumpLevel.isBlank() ? "OUTPUT" : args.dumpLevel);
        try {
            String text = AskJeiClient.summarize(stack, level, args.deadlineMs);
            return AskToolContext.clipChars(text, level.outputBudget());
        } catch (Throwable t) {
            return "";
        }
    }
}

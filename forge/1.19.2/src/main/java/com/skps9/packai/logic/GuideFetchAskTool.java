package com.skps9.packai.logic;

import com.skps9.packai.client.patchouli.PatchouliGuideLookup;

import net.minecraft.world.item.ItemStack;

/** Thin wrapper: {@link PatchouliGuideLookup#lookup} / GuidebookIndex pins. */
public final class GuideFetchAskTool implements AskTool {
    @Override
    public String name() {
        return "guide_fetch";
    }

    @Override
    public String run(AskToolArgs args) {
        AskToolEnv env = AskToolEnv.current();
        ItemStack stack = env == null ? ItemStack.EMPTY : env.stack;
        try {
            String text = PatchouliGuideLookup.lookup(stack, args.question);
            return AskToolContext.clipChars(text, 2000);
        } catch (Throwable t) {
            return "";
        }
    }
}

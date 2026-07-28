package com.skps9.packai.compat;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Soft-dep entry for GuideME. Never imports GuideME types —
 * {@link GuideMeBridgeImpl} loads only when {@code guideme} is present.
 */
public final class GuideMeBridge {
    private static final String IMPL = "com.skps9.packai.compat.GuideMeBridgeImpl";
    private static Boolean loaded;
    private static boolean implFailed;

    private GuideMeBridge() {}

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("guideme");
        }
        return loaded;
    }

    /**
     * Guide page text from live ItemIndex (frontmatter item_ids).
     * Empty when absent, unloaded, or no hit.
     */
    public static String lookupGuideText(ItemStack stack) {
        if (!isLoaded() || stack == null || stack.isEmpty() || implFailed) {
            return "";
        }
        try {
            Object text = Class.forName(IMPL)
                    .getMethod("lookupGuideText", ItemStack.class)
                    .invoke(null, stack);
            return text instanceof String s ? s : "";
        } catch (Throwable t) {
            implFailed = true;
            return "";
        }
    }
}

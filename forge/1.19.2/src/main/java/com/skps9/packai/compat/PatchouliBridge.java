package com.skps9.packai.compat;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * Soft-dep entry for Patchouli. Never imports Patchouli types —
 * {@link PatchouliBridgeImpl} loads only when {@code patchouli} is present.
 */
public final class PatchouliBridge {
    private static final String IMPL = "com.skps9.packai.compat.PatchouliBridgeImpl";
    private static Boolean loaded;
    private static boolean implFailed;

    private PatchouliBridge() {}

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("patchouli");
        }
        return loaded;
    }

    /**
     * Guide text from live Patchouli recipeMappings (icon / extra_recipe / crafting pages).
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

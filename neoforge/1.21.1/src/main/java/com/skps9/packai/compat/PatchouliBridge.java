package com.skps9.packai.compat;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Soft-dep entry for Patchouli. Never imports Patchouli types ??
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
     * Empty when absent, unloaded, or no hit. Unscoped (any book).
     */
    public static String lookupGuideText(ItemStack stack) {
        return lookupGuideText(stack, "any_mod", null);
    }

    /**
     * Ask fallback: index miss only. {@code same_mod} drops books whose id namespace ??itemNs.
     * Extension books skipped in impl (same as before). Never dual-pins with index.
     */
    public static String lookupGuideText(ItemStack stack, String guidebookScope, String itemNs) {
        if (!isLoaded() || stack == null || stack.isEmpty() || implFailed) {
            return "";
        }
        try {
            Object text = Class.forName(IMPL)
                    .getMethod("lookupGuideText", ItemStack.class, String.class, String.class)
                    .invoke(null, stack, guidebookScope, itemNs);
            return text instanceof String s ? s : "";
        } catch (Throwable t) {
            // Fallback to legacy 1-arg if older impl somehow present
            try {
                Object text = Class.forName(IMPL)
                        .getMethod("lookupGuideText", ItemStack.class)
                        .invoke(null, stack);
                return text instanceof String s ? s : "";
            } catch (Throwable t2) {
                implFailed = true;
                return "";
            }
        }
    }
}

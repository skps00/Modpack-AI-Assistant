package com.skps9.packai.compat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * Soft-dep entry for Patchouli. Never imports Patchouli types —
 * {@link PatchouliBridgeImpl} loads only when {@code patchouli} is present.
 */
public final class PatchouliBridge {
    private static final String IMPL = "com.skps9.packai.compat.PatchouliBridgeImpl";
    private static final long API_WAIT_MS = 2000L;
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
     * Ask fallback: index miss only. Same lookup as Ctrl quick-search
     * ({@code BookContents.getEntryForStack}) but all registered books — no inventory book,
     * no Patchouli GUI. {@code same_mod} drops books whose id namespace ≠ itemNs.
     * Runs on the client thread (I18n / page text). Never dual-pins with index.
     */
    public static String lookupGuideText(ItemStack stack, String guidebookScope, String itemNs) {
        if (!isLoaded() || stack == null || stack.isEmpty() || implFailed) {
            return "";
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && !mc.isSameThread()) {
            ItemStack copy = stack.copy();
            CompletableFuture<String> future = new CompletableFuture<>();
            mc.execute(() -> {
                try {
                    future.complete(invokeImpl(copy, guidebookScope, itemNs));
                } catch (Throwable t) {
                    future.complete("");
                }
            });
            try {
                String s = future.get(API_WAIT_MS, TimeUnit.MILLISECONDS);
                return s == null ? "" : s;
            } catch (Exception e) {
                return "";
            }
        }
        return invokeImpl(stack, guidebookScope, itemNs);
    }

    private static String invokeImpl(ItemStack stack, String guidebookScope, String itemNs) {
        try {
            Object text = Class.forName(IMPL)
                    .getMethod("lookupGuideText", ItemStack.class, String.class, String.class)
                    .invoke(null, stack, guidebookScope, itemNs);
            return text instanceof String s ? s : "";
        } catch (Throwable t) {
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

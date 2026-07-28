package com.skps9.packai.compat;

import java.util.List;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Soft-dep entry for Curios. Never imports Curios types — {@link CuriosBridgeImpl} loads
 * only when {@code curios} is present (Class.forName).
 */
public final class CuriosBridge {
    private static final String IMPL = "com.skps9.packai.compat.CuriosBridgeImpl";
    private static Boolean loaded;
    private static boolean implFailed;

    private CuriosBridge() {}

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("curios");
        }
        return loaded;
    }

    public static void appendSlotKeys(LivingEntity entity, List<String> out) {
        if (!isLoaded() || entity == null || out == null || implFailed) {
            return;
        }
        try {
            Class.forName(IMPL)
                    .getMethod("appendSlotKeys", LivingEntity.class, List.class)
                    .invoke(null, entity, out);
        } catch (Throwable t) {
            implFailed = true;
        }
    }

    public static ItemStack stackAt(LivingEntity entity, String key) {
        if (!isLoaded() || entity == null || key == null || !key.startsWith("curios:") || implFailed) {
            return ItemStack.EMPTY;
        }
        try {
            Object stack = Class.forName(IMPL)
                    .getMethod("stackAt", LivingEntity.class, String.class)
                    .invoke(null, entity, key);
            return stack instanceof ItemStack s ? s : ItemStack.EMPTY;
        } catch (Throwable t) {
            implFailed = true;
            return ItemStack.EMPTY;
        }
    }
}

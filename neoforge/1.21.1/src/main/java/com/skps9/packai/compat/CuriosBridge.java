package com.skps9.packai.compat;

import java.util.List;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Neo 1.21.1 stub — Curios soft-dep deferred (API / artifact not wired).
 * InvPick stays crash-free; {@link #isLoaded()} always false.
 */
public final class CuriosBridge {
    private CuriosBridge() {}

    public static boolean isLoaded() {
        return false;
    }

    public static void appendSlotKeys(LivingEntity entity, List<String> out) {
        // no-op stub
    }

    public static ItemStack stackAt(LivingEntity entity, String key) {
        return ItemStack.EMPTY;
    }
}

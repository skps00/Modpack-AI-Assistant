package com.skps9.packai.logic;

import net.minecraft.world.item.ItemStack;

public final class CardSlotPick {
    private CardSlotPick() {}

    /** 依 JEI 節奏揀候選；options 為 null／單一／索引越界 → 回 fallback。 */
    public static ItemStack pick(ItemStack fallback, java.util.List<SlotOptions> options, int index, long ticks) {
        if (options == null || index < 0 || index >= options.size()) return fallback;
        SlotOptions o = options.get(index);
        if (o == null || o.shown() == null || o.shown().isEmpty() || o.shown().size() <= 1) return fallback;
        return o.shown().get(SlotCycler.index(ticks, o.shown().size()));
    }
}

package com.skps9.packai.logic;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/** One recipe input slot: the full candidate list (JEI keeps these; see CycleTicker). */
public record SlotOptions(String tagId, List<ItemStack> shown, int total) {
    public static final int MAX_SHOWN = 64;

    public SlotOptions {
        tagId = tagId == null || tagId.isBlank() ? "" : tagId;
        shown = shown == null ? List.of() : List.copyOf(shown.subList(0, Math.min(MAX_SHOWN, shown.size())));
        total = Math.max(total, shown.size());
    }

    public int count() {
        return total;
    }

    public boolean isSingle() {
        return tagId.isEmpty() && total <= 1;
    }
}

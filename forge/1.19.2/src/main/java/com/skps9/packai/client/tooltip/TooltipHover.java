package com.skps9.packai.client.tooltip;

import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;

/** Last item that showed a tooltip (JEI / inventory hover). */
public final class TooltipHover {
    private static ItemStack last = ItemStack.EMPTY;

    private TooltipHover() {}

    public static void note(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        last = stack.copy();
    }

    public static ItemStack current() {
        return last;
    }

    public static void clear() {
        last = ItemStack.EMPTY;
    }

    public static boolean sameItem(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (!ItemStack.isSameItemSameTags(a, b)) {
            return false;
        }
        var ka = Registry.ITEM.getKey(a.getItem());
        var kb = Registry.ITEM.getKey(b.getItem());
        return ka != null && ka.equals(kb);
    }
}

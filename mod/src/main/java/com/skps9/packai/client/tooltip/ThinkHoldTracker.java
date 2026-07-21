package com.skps9.packai.client.tooltip;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * Hold-to-think progress (Create Ponder style) for JEI / inventory tooltips.
 */
public final class ThinkHoldTracker {
    /** ponytail: fixed 1.2s hold; no config until someone asks */
    private static final int HOLD_TICKS = 24;

    private static ItemStack target = ItemStack.EMPTY;
    private static int progressTicks;
    private static boolean fired;
    private static boolean keyDown;
    private static boolean gatherBarAdded;

    private ThinkHoldTracker() {}

    public static void beginTooltipFrame() {
        gatherBarAdded = false;
    }

    public static void markGatherBarAdded() {
        gatherBarAdded = true;
    }

    public static boolean gatherBarAdded() {
        return gatherBarAdded;
    }

    public static void tick(boolean keyHeld, ItemStack hover) {
        keyDown = keyHeld;
        ItemStack use = !hover.isEmpty() ? hover : TooltipHover.current();
        if (!keyHeld || use.isEmpty()) {
            reset();
            return;
        }
        if (!sameItem(target, use)) {
            target = use.copy();
            progressTicks = 0;
            fired = false;
        }
        if (fired) {
            return;
        }
        progressTicks++;
        if (progressTicks >= HOLD_TICKS) {
            fired = true;
        }
    }

    public static boolean ready() {
        return fired && !target.isEmpty();
    }

    public static ItemStack target() {
        return target;
    }

    public static void consumeReady() {
        fired = false;
        progressTicks = 0;
        target = ItemStack.EMPTY;
    }

    public static void reset() {
        target = ItemStack.EMPTY;
        progressTicks = 0;
        fired = false;
    }

    /** True while Y is held on this tooltip item (show bar even at 0 fill). */
    public static boolean holdingFor(ItemStack stack) {
        if (!keyDown || target.isEmpty() || fired) {
            return false;
        }
        return sameItem(target, stack) || TooltipHover.sameItem(TooltipHover.current(), stack);
    }

    public static boolean matches(ItemStack stack) {
        return !target.isEmpty() && sameItem(target, stack);
    }

    public static int filledSegments(int segments) {
        if (segments <= 0 || target.isEmpty() || !keyDown || fired) {
            return 0;
        }
        if (progressTicks <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(segments, progressTicks * segments / HOLD_TICKS));
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (!ItemStack.isSameItemSameComponents(a, b)) {
            return false;
        }
        var ka = BuiltInRegistries.ITEM.getKey(a.getItem());
        var kb = BuiltInRegistries.ITEM.getKey(b.getItem());
        return ka != null && ka.equals(kb);
    }
}

package com.skps9.packai.client.tooltip;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * Hold-to-think tracking, modeled after Create Ponder's {@code PonderTooltipHandler}:
 * progress updates on tooltip render (deferred tick), not only on client tick.
 */
public final class ThinkHoldTracker {
    private static ItemStack hoveredStack = ItemStack.EMPTY;
    private static ItemStack trackingStack = ItemStack.EMPTY;
    private static float holdProgress;
    private static boolean deferTick;
    private static boolean keyDown;
    private static boolean fired;
    private static Consumer<ItemStack> onComplete;

    private ThinkHoldTracker() {}

    public static void setOnComplete(Consumer<ItemStack> callback) {
        onComplete = callback;
    }

    /** Called from client tick — real work runs in {@link #deferredTick} during tooltip. */
    public static void tick(boolean keyHeld) {
        keyDown = keyHeld;
        deferTick = true;
        if (!keyHeld) {
            holdProgress = Math.max(0f, holdProgress - 0.05f);
            if (holdProgress <= 0f) {
                resetSoft();
            }
        }
    }

    public static void deferredTick() {
        deferTick = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || hoveredStack.isEmpty() || trackingStack.isEmpty()) {
            trackingStack = ItemStack.EMPTY;
            holdProgress = 0f;
            fired = false;
            return;
        }

        if (keyDown && !fired) {
            if (holdProgress >= 1f) {
                ItemStack done = trackingStack.copy();
                holdProgress = 0f;
                fired = true;
                trackingStack = ItemStack.EMPTY;
                hoveredStack = ItemStack.EMPTY;
                if (onComplete != null && !done.isEmpty()) {
                    onComplete.accept(done);
                }
                return;
            }
            // Same easing as Create Ponder
            holdProgress = Math.min(1f, holdProgress + Math.max(0.25f, holdProgress) * 0.25f);
        } else if (!keyDown) {
            holdProgress = Math.max(0f, holdProgress - 0.05f);
        }

        hoveredStack = ItemStack.EMPTY;
    }

    /**
     * Track the stack currently building a tooltip (Create: updateHovered).
     *
     * @return true if this stack is the active hold target
     */
    public static boolean updateHovered(ItemStack stack) {
        ItemStack prev = trackingStack;
        hoveredStack = ItemStack.EMPTY;

        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (prev.isEmpty() || !sameItemLoose(prev, stack)) {
            holdProgress = 0f;
            fired = false;
        }

        hoveredStack = stack;
        trackingStack = stack;
        return true;
    }

    public static boolean needsDeferredTick() {
        return deferTick;
    }

    public static boolean tracking(ItemStack stack) {
        return !trackingStack.isEmpty() && sameItemLoose(trackingStack, stack);
    }

    /** 0..1 hold fill used for the ||| text bar. */
    public static float progress() {
        return Math.min(1f, holdProgress * 8f / 7f);
    }

    public static boolean keyDown() {
        return keyDown;
    }

    public static void reset() {
        hoveredStack = ItemStack.EMPTY;
        trackingStack = ItemStack.EMPTY;
        holdProgress = 0f;
        fired = false;
        keyDown = false;
        deferTick = false;
    }

    private static void resetSoft() {
        trackingStack = ItemStack.EMPTY;
        hoveredStack = ItemStack.EMPTY;
        fired = false;
    }

    static boolean sameItemLoose(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.getItem() != b.getItem()) {
            return false;
        }
        var ka = BuiltInRegistries.ITEM.getKey(a.getItem());
        var kb = BuiltInRegistries.ITEM.getKey(b.getItem());
        return ka != null && ka.equals(kb);
    }
}

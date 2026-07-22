package com.skps9.packai.client.tooltip;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Hold-to-think tracking, inspired by Create Ponder's {@code PonderTooltipHandler}.
 * Ponder advances progress only in tooltip {@code deferredTick}; that stalls when JEI
 * indexing after world-join stops rebuilding tooltips. We advance on the client tick
 * instead; tooltips only refresh hover target + draw the bar.
 */
public final class ThinkHoldTracker {
    private static ItemStack trackingStack = ItemStack.EMPTY;
    /** Current chase value (updated in {@link #tick}). */
    private static float holdProgress;
    /** Value from previous tick — for smooth lerp between ticks. */
    private static float holdProgressO;
    private static boolean keyDown;
    private static boolean fired;
    /** When true (AI busy), hold does nothing and progress stays at 0. */
    private static boolean locked;
    private static Consumer<ItemStack> onComplete;

    private ThinkHoldTracker() {}

    public static void setOnComplete(Consumer<ItemStack> callback) {
        onComplete = callback;
    }

    public static void setLocked(boolean value) {
        if (value == locked) {
            return;
        }
        locked = value;
        if (locked) {
            holdProgress = 0f;
            holdProgressO = 0f;
            fired = false;
        }
    }

    public static boolean isLocked() {
        return locked;
    }

    /**
     * Client tick: read key + advance hold. Must not depend on tooltip FPS
     * (JEI indexing after join often stalls tooltip rebuilds).
     */
    public static void tick(boolean keyHeld) {
        keyDown = keyHeld && !locked;
        Minecraft mc = Minecraft.getInstance();
        if (locked || mc.screen == null) {
            holdProgressO = 0f;
            holdProgress = 0f;
            return;
        }
        if (trackingStack.isEmpty()) {
            holdProgressO = 0f;
            holdProgress = 0f;
            fired = false;
            return;
        }

        holdProgressO = holdProgress;

        if (keyDown && !fired) {
            if (holdProgress >= 1f) {
                ItemStack done = trackingStack.copy();
                holdProgress = 0f;
                holdProgressO = 0f;
                fired = true;
                trackingStack = ItemStack.EMPTY;
                if (onComplete != null && !done.isEmpty()) {
                    onComplete.accept(done);
                }
                return;
            }
            // Same easing as Create Ponder
            holdProgress = Math.min(1f, holdProgress + Math.max(0.25f, holdProgress) * 0.25f);
        } else if (!keyDown) {
            holdProgress = Math.max(0f, holdProgress - 0.05f);
            if (holdProgress <= 0f) {
                resetSoft();
            }
        }
    }

    /**
     * Track the stack currently under the cursor (from tooltip or last hover note).
     */
    public static boolean updateHovered(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        ItemStack prev = trackingStack;
        // Same item type is not enough (SlashBlade NBT variants).
        if (prev.isEmpty() || !ItemStack.isSameItemSameComponents(prev, stack)) {
            holdProgress = 0f;
            holdProgressO = 0f;
            fired = false;
        }

        trackingStack = stack.copy();
        return true;
    }

    public static boolean tracking(ItemStack stack) {
        return !trackingStack.isEmpty()
                && stack != null
                && !stack.isEmpty()
                && (trackingStack == stack || ItemStack.isSameItemSameComponents(trackingStack, stack));
    }

    /**
     * 0..1 hold fill with partial-tick lerp (Ponder: {@code getValue(pt) * 8/7}).
     */
    public static float progress() {
        float pt = partialTick();
        float v = holdProgressO + (holdProgress - holdProgressO) * pt;
        return Math.min(1f, v * 8f / 7f);
    }

    public static boolean keyDown() {
        return keyDown;
    }

    public static void reset() {
        trackingStack = ItemStack.EMPTY;
        holdProgress = 0f;
        holdProgressO = 0f;
        fired = false;
        keyDown = false;
        locked = false;
    }

    private static void resetSoft() {
        trackingStack = ItemStack.EMPTY;
        fired = false;
    }

    private static float partialTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return 0f;
        }
        return mc.getTimer().getGameTimeDeltaPartialTick(false);
    }
}

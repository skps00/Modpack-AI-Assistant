package com.skps9.packai.client.jei;

import java.util.Optional;

import com.skps9.packai.client.tooltip.TooltipHover;

import com.skps9.packai.logic.ItemResolver;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Pick JEI lookup target: main hand, JEI hover, or id in question text.
 */
public final class JeiTargetResolver {
    private static ItemStack pin = ItemStack.EMPTY;

    private JeiTargetResolver() {}

    /** Pin one item for the next {@link #resolve} (tooltip think). */
    public static void pin(ItemStack stack) {
        pin = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    public static void clearPin() {
        pin = ItemStack.EMPTY;
    }

    /** Pinned item for in-flight tooltip think (not yet consumed by AskService). */
    public static ItemStack pinnedOrEmpty() {
        return pin;
    }

    /** JEI hover, else container slot under mouse. */
    public static ItemStack hoveredItem(Minecraft mc) {
        if (mc == null || mc.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack jei = jeiHoveredStack();
        if (!jei.isEmpty()) {
            return jei;
        }
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> scr) {
            var slot = scr.getSlotUnderMouse();
            if (slot != null && slot.hasItem()) {
                return slot.getItem();
            }
        }
        ItemStack tip = TooltipHover.current();
        if (!tip.isEmpty()) {
            return tip;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack resolve(Minecraft mc, String question) {
        if (mc == null || mc.player == null) {
            return ItemStack.EMPTY;
        }
        if (!pin.isEmpty()) {
            return pin;
        }
        // Explicit mod:id in the question wins over whatever is in hand.
        Optional<String> inQ = ItemResolver.idInQuestion(question);
        if (inQ.isPresent()) {
            ItemStack fromQ = ItemResolver.stackFromId(inQ.get());
            if (!fromQ.isEmpty()) {
                return fromQ;
            }
        }
        ItemStack held = mc.player.getMainHandItem();
        if (!held.isEmpty()) {
            return held;
        }
        if (ModList.get().isLoaded("jei")) {
            ItemStack hover = jeiHoveredStack();
            if (!hover.isEmpty()) {
                return hover;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack jeiHoveredStack() {
        try {
            Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
            if (opt.isEmpty()) {
                return ItemStack.EMPTY;
            }
            IJeiRuntime runtime = opt.get();
            ItemStack stack = stackFromOverlay(runtime.getIngredientListOverlay());
            if (!stack.isEmpty()) {
                return stack;
            }
            stack = stackFromRuntimeOverlay(runtime, "getBookmarkOverlay");
            if (!stack.isEmpty()) {
                return stack;
            }
            stack = stackFromRuntimeOverlay(runtime, "getRecipesGui");
            if (!stack.isEmpty()) {
                return stack;
            }
            return ItemStack.EMPTY;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /** JEI overlays expose getIngredientUnderMouse(VanillaTypes.ITEM_STACK). */
    private static ItemStack stackFromRuntimeOverlay(IJeiRuntime runtime, String getter) {
        try {
            var method = runtime.getClass().getMethod(getter);
            return stackFromOverlay(method.invoke(runtime));
        } catch (ReflectiveOperationException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack stackFromOverlay(Object overlay) {
        if (overlay == null) {
            return ItemStack.EMPTY;
        }
        try {
            var stack = overlay.getClass()
                    .getMethod("getIngredientUnderMouse", mezz.jei.api.ingredients.IIngredientType.class)
                    .invoke(overlay, VanillaTypes.ITEM_STACK);
            if (stack instanceof ItemStack item && !item.isEmpty()) {
                return item;
            }
        } catch (ReflectiveOperationException ignored) {
            // ponytail: overlay API mismatch — skip
        }
        return ItemStack.EMPTY;
    }
}

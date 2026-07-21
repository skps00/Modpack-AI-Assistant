package com.skps9.packai.client.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Create Ponder–style hold line: hint text, or {@code |||} progress replacing that same line.
 */
public final class PackAiTooltipHandler {
    private PackAiTooltipHandler() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        TooltipHover.note(stack);
        ThinkHoldTracker.updateHovered(stack);

        if (ThinkHoldTracker.needsDeferredTick()) {
            ThinkHoldTracker.deferredTick();
        }

        if (!ThinkHoldTracker.tracking(stack)) {
            // Still show static hint when this item is eligible (always for Pack AI).
            insertThinkLine(event, PonderStyle.thinkHint());
            return;
        }

        insertThinkLine(event, PonderStyle.progressOrHint(ThinkHoldTracker.progress()));
    }

    /** Insert after item name (index 1), like Create Ponder. */
    private static void insertThinkLine(ItemTooltipEvent event, Component line) {
        var tip = event.getToolTip();
        // Remove any previous Pack AI think / bar line so rebuilds don't stack.
        tip.removeIf(PonderStyle::isThinkHint);
        if (tip.size() < 2) {
            tip.add(line);
        } else {
            tip.add(1, line);
        }
    }
}

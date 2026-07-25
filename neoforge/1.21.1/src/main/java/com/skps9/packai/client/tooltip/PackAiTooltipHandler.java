package com.skps9.packai.client.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
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

        // AI busy: static wait line, no hold tracking / progress (keeps JEI smooth).
        if (ThinkHoldTracker.isLocked()) {
            insertThinkLine(event, PonderStyle.busyHint());
            return;
        }

        ThinkHoldTracker.updateHovered(stack);

        if (!ThinkHoldTracker.tracking(stack)) {
            insertThinkLine(event, PonderStyle.thinkHint());
            return;
        }

        insertThinkLine(event, PonderStyle.progressOrHint(ThinkHoldTracker.progress()));
    }

    /** Ponder-style border tint while holding to think. */
    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        if (ThinkHoldTracker.isLocked()) {
            return;
        }
        if (!ThinkHoldTracker.tracking(event.getItemStack())) {
            return;
        }
        float progress = ThinkHoldTracker.progress();
        if (progress <= 0f) {
            return;
        }
        int color = PonderStyle.borderColorForProgress(progress);
        event.setBorderStart(color);
        event.setBorderEnd(color);
    }

    /** Insert after item name (index 1), like Create Ponder. */
    private static void insertThinkLine(ItemTooltipEvent event, Component line) {
        var tip = event.getToolTip();
        tip.removeIf(PonderStyle::isThinkHint);
        if (tip.size() < 2) {
            tip.add(line);
        } else {
            tip.add(1, line);
        }
    }
}

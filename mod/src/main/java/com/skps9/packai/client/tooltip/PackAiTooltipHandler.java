package com.skps9.packai.client.tooltip;

import com.mojang.datafixers.util.Either;
import com.skps9.packai.client.ClientSetup;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Ponder-style hint + hold progress inside item tooltips. */
public final class PackAiTooltipHandler {
    public static final int SEGMENTS = 32;

    private PackAiTooltipHandler() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        TooltipHover.note(stack);
        event.getToolTip().add(Component.translatable(
                "packai.tooltip.think",
                ClientSetup.THINK_JEI.get().getTranslatedKeyMessage()));
    }

    /** Segmented bar as a tooltip row (inventory + JEI when GatherComponents fires). */
    @SubscribeEvent
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (!ThinkHoldTracker.holdingFor(stack)) {
            return;
        }
        int filled = ThinkHoldTracker.filledSegments(SEGMENTS);
        event.getTooltipElements().add(Either.right(new ThinkProgressBar.Tooltip(filled, SEGMENTS)));
        ThinkHoldTracker.markGatherBarAdded();
    }

    /** Fallback draw under tooltip when GatherComponents is skipped (some JEI builds). */
    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        if (!ThinkHoldTracker.holdingFor(stack)) {
            return;
        }
        if (ThinkHoldTracker.gatherBarAdded()) {
            return;
        }
        int filled = ThinkHoldTracker.filledSegments(SEGMENTS);
        int x = event.getX();
        int y = event.getY();
        for (var part : event.getComponents()) {
            y += part.getHeight();
        }
        y += 2;
        ThinkProgressBar.draw(event.getGraphics(), x, y, filled, SEGMENTS);
    }
}

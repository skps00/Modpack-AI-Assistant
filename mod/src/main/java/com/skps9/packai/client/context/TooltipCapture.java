package com.skps9.packai.client.context;

import java.util.List;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Captures the tooltip text the player would see, including Shift/Ctrl-gated lines.
 * ScreenMixin forces modifier keys while {@link #forceExpanded()} is true.
 */
public final class TooltipCapture {
    private static final int MAX_CHARS = 900;
    private static final ThreadLocal<Boolean> FORCE = ThreadLocal.withInitial(() -> false);

    private TooltipCapture() {}

    public static boolean forceExpanded() {
        return Boolean.TRUE.equals(FORCE.get());
    }

    /**
     * Full tooltip as plain text (name + lore + mod Shift details when possible).
     */
    public static String capture(ItemStack stack, LocalPlayer player) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        if (player == null) {
            return stack.getHoverName().getString();
        }
        FORCE.set(true);
        try {
            Item.TooltipContext ctx = Item.TooltipContext.of(player.level());
            List<Component> lines = stack.getTooltipLines(ctx, player, TooltipFlag.Default.ADVANCED);
            StringBuilder sb = new StringBuilder();
            for (Component line : lines) {
                String s = line.getString().trim();
                if (s.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(s);
                if (sb.length() >= MAX_CHARS) {
                    break;
                }
            }
            return sb.isEmpty() ? stack.getHoverName().getString() : sb.toString();
        } catch (Exception e) {
            return stack.getHoverName().getString();
        } finally {
            FORCE.set(false);
        }
    }
}

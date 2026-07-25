package com.skps9.packai.client.context;

import java.util.List;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Captures tooltip text. MinPlay: no ScreenMixin force-Shift (Parity).
 */
public final class TooltipCapture {
    private static final int MAX_CHARS = 900;

    private TooltipCapture() {}

    public static boolean forceExpanded() {
        return false;
    }

    public static String capture(ItemStack stack, LocalPlayer player) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        if (player == null) {
            return stack.getHoverName().getString();
        }
        try {
            List<Component> lines = stack.getTooltipLines(player, TooltipFlag.Default.ADVANCED);
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
        }
    }
}

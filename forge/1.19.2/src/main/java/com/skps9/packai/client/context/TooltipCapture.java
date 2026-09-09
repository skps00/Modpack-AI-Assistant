package com.skps9.packai.client.context;

import java.util.List;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Captures tooltip text the player would see, including Shift/Ctrl-gated lines.
 */
public final class TooltipCapture {
    private static final int MAX_CHARS = 1400;
    private static final ThreadLocal<Boolean> FORCE = ThreadLocal.withInitial(() -> false);

    private TooltipCapture() {}

    public static boolean forceExpanded() {
        return Boolean.TRUE.equals(FORCE.get());
    }

    public static String capture(ItemStack stack, LocalPlayer player) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        if (player == null) {
            return stack.getHoverName().getString();
        }
        FORCE.set(true);
        try {
            List<Component> lines = stack.getTooltipLines(player, TooltipFlag.Default.ADVANCED);
            if (com.skps9.packai.PackAiMod.LOGGER.isDebugEnabled() || stack.getItem().toString().contains("wuren")) {
                List<String> strs = new java.util.ArrayList<>();
                for (Component line : lines) { String s = line.getString().trim(); if (!s.isEmpty()) { strs.add(s); } }
                com.skps9.packai.PackAiMod.LOGGER.debug("PAI TooltipCapture id={} lines={} dump=[{}]",
                        stack.getItem().toString(), strs.size(), String.join(" | ", strs.subList(0, Math.min(strs.size(), 15))));
            }
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
            String raw = sb.isEmpty() ? stack.getHoverName().getString() : sb.toString();
            return com.skps9.packai.logic.AskReplyScrub.scrubPackAiTooltipChrome(raw);
        } catch (Exception e) {
            return stack.getHoverName().getString();
        } finally {
            FORCE.set(false);
        }
    }
}

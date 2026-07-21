package com.skps9.packai.client.tooltip;

import com.skps9.packai.client.ClientSetup;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Create Ponder–style gray hint / ||| progress text. */
public final class PonderStyle {
    private static final int BODY = 0xA0A0A0;
    private static final int KEY = 0xFFFFFF;

    private PonderStyle() {}

    public static Component thinkHint() {
        MutableComponent key = ClientSetup.THINK_JEI.get().getTranslatedKeyMessage().copy();
        key.setStyle(Style.EMPTY.withColor(KEY));
        return Component.translatable("packai.tooltip.think.prefix")
                .withStyle(body())
                .append(key)
                .append(Component.translatable("packai.tooltip.think.suffix").withStyle(body()));
    }

    /**
     * Create-style bar: filled {@code |} in gray, remainder in dark gray.
     * When progress is 0, returns the hold hint instead.
     */
    public static Component progressOrHint(float progress) {
        if (progress <= 0f) {
            return thinkHint();
        }
        Minecraft mc = Minecraft.getInstance();
        Component holdW = thinkHint();
        float charWidth = Math.max(1f, mc.font.width("|"));
        float tipWidth = Math.max(charWidth, mc.font.width(holdW));
        int total = Math.max(1, (int) (tipWidth / charWidth));
        int current = Math.min(total, Math.max(1, (int) (progress * total)));

        StringBuilder bars = new StringBuilder();
        bars.append(ChatFormatting.GRAY);
        bars.append("|".repeat(current));
        if (progress < 1f && current < total) {
            bars.append(ChatFormatting.DARK_GRAY);
            bars.append("|".repeat(total - current));
        }
        return Component.literal(bars.toString());
    }

    public static boolean isThinkHint(Component c) {
        if (c == null) {
            return false;
        }
        if (c.getContents() instanceof TranslatableContents tc) {
            return "packai.tooltip.think.prefix".equals(tc.getKey());
        }
        String s = c.getString();
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '|') {
                return false;
            }
        }
        return true;
    }

    private static Style body() {
        return Style.EMPTY.withColor(BODY).withItalic(false);
    }
}

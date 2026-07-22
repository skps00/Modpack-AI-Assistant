package com.skps9.packai.client.tooltip;

import com.skps9.packai.client.ClientSetup;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Create Ponder–style gray hint / ||| progress text / tooltip border tint. */
public final class PonderStyle {
    private static final int BODY = 0xA0A0A0;
    private static final int KEY = 0xFFFFFF;

    /** Same RGB ramps as Create Ponder {@code PonderTooltipHandler} border colors. */
    private static final int BORDER_A = 0xFF5000FF;
    private static final int BORDER_B = 0xFF5555FF;
    private static final int BORDER_C = 0xFFFFFFFF;

    private static int cachedTotalBars = -1;
    private static int cachedFilledBars = -1;
    private static Component cachedBar;

    private PonderStyle() {}

    public static Component thinkHint() {
        MutableComponent key = ClientSetup.THINK_JEI.get().getTranslatedKeyMessage().copy();
        key.setStyle(Style.EMPTY.withColor(KEY));
        return Component.translatable("packai.tooltip.think.prefix")
                .withStyle(body())
                .append(key)
                .append(Component.translatable("packai.tooltip.think.suffix").withStyle(body()));
    }

    public static Component busyHint() {
        return Component.translatable("packai.tooltip.think.busy").withStyle(body());
    }

    /**
     * Create-style bar: filled {@code |} in gray, remainder in dark gray.
     * When progress is 0, returns the hold hint instead.
     */
    public static Component progressOrHint(float progress) {
        if (progress <= 0f) {
            cachedFilledBars = -1;
            return thinkHint();
        }
        Minecraft mc = Minecraft.getInstance();
        float charWidth = Math.max(1f, mc.font.width("|"));
        float tipWidth = Math.max(charWidth, mc.font.width(thinkHint()));
        int total = Math.max(1, (int) (tipWidth / charWidth));
        int current = Math.min(total, Math.max(1, (int) (progress * total)));

        if (current == cachedFilledBars && total == cachedTotalBars && cachedBar != null) {
            return cachedBar;
        }

        StringBuilder bars = new StringBuilder();
        bars.append(ChatFormatting.GRAY);
        bars.append("|".repeat(current));
        if (progress < 1f && current < total) {
            bars.append(ChatFormatting.DARK_GRAY);
            bars.append("|".repeat(total - current));
        }
        cachedTotalBars = total;
        cachedFilledBars = current;
        cachedBar = Component.literal(bars.toString());
        return cachedBar;
    }

    /**
     * Border ARGB for hold progress (Ponder: A→B below 0.5, B→C above).
     * Both tooltip border ends use the same color so the frame animates over time.
     */
    public static int borderColorForProgress(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        if (p < 0.5f) {
            return mixArgb(BORDER_A, BORDER_B, p * 2f);
        }
        return mixArgb(BORDER_B, BORDER_C, (p - 0.5f) * 2f);
    }

    public static boolean isThinkHint(Component c) {
        if (c == null) {
            return false;
        }
        if (c.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey();
            return "packai.tooltip.think.prefix".equals(key)
                    || "packai.tooltip.think.busy".equals(key);
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

    private static int mixArgb(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = mixChannel((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = mixChannel((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
        int g = mixChannel((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
        int b = mixChannel(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int mixChannel(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }

    private static Style body() {
        return Style.EMPTY.withColor(BODY).withItalic(false);
    }
}

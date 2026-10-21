package com.skps9.packai.client.gui;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.TooltipAccessor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Small 1.19.2 widget helpers for APIs that are newer in 1.21 (tooltips on Button/EditBox).
 * <p>
 * Vanilla {@code Screen#render} does not call {@link AbstractWidget#renderToolTip}; tips only show
 * when the screen paints {@link TooltipAccessor} after widgets (see Options screens). Call
 * {@link #renderHoveredTips} after {@code super.render}.
 */
public final class WidgetCompat {
    private WidgetCompat() {}

    public static Button button(int x, int y, int width, int height, Component label, Button.OnPress onPress) {
        return button(x, y, width, height, label, onPress, null);
    }

    public static Button button(int x, int y, int width, int height, Component label, Button.OnPress onPress,
            Component tip) {
        return new TipButton(x, y, width, height, label, onPress, tip);
    }

    public static EditBox editBox(int x, int y, int w, int h, Component message, Component tip) {
        return new TipEditBox(x, y, w, h, message, tip);
    }

    /** Flat search field (G3): height 20, placeholder when empty even while focused. */
    public static EditBox searchBox(int x, int y, int w, int h, Component placeholder, Component tip) {
        TipEditBox box = new TipEditBox(x, y, w, h, Component.empty(), tip);
        box.setPlaceholder(placeholder);
        box.setBordered(false);
        box.setTextColor(GuiShell.TITLE);
        return box;
    }

    /** EditBox that never shows a hover tip (Settings value rows — desc panel already explains). */
    public static EditBox editBoxNoTip(int x, int y, int w, int h, Component message) {
        return new TipEditBox(x, y, w, h, message, null);
    }

    /** Wrap tooltip text for CycleButton.withTooltip on 1.19.2. */
    public static List<FormattedCharSequence> tipLines(Component tip) {
        if (tip == null) {
            return List.of();
        }
        Minecraft mc = Minecraft.getInstance();
        int wrap = mc != null && mc.screen != null ? Math.max(mc.screen.width / 2, 200) : 200;
        return mc != null ? mc.font.split(tip, wrap) : List.of();
    }

    public static List<FormattedCharSequence> tipLines(String langKey) {
        return tipLines(Component.translatable(langKey));
    }

    /**
     * Paint tip for the hovered {@link TooltipAccessor} (CycleButton / TipButton / TipEditBox).
     * Also paints {@link ShiftTipHost} lines when Shift is held.
     * Call after {@code super.render} so tip draws on top.
     */
    public static void renderHoveredTips(Screen screen, PoseStack pose, int mouseX, int mouseY) {
        if (screen == null) {
            return;
        }
        if (screen instanceof ShiftTipHost host && Screen.hasShiftDown()) {
            List<FormattedCharSequence> shift = host.shiftTipLines(mouseX, mouseY);
            if (shift != null && !shift.isEmpty()) {
                screen.renderTooltip(pose, shift, mouseX, mouseY);
                return;
            }
        }
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.isHoveredOrFocused()) {
                continue;
            }
            // Prefer mouse-hover only: focused EditBox must not pin tip while cursor elsewhere.
            if (!widget.isMouseOver(mouseX, mouseY)) {
                continue;
            }
            if (!(child instanceof TooltipAccessor accessor)) {
                continue;
            }
            List<FormattedCharSequence> lines = accessor.getTooltip();
            if (lines != null && !lines.isEmpty()) {
                screen.renderTooltip(pose, lines, mouseX, mouseY);
                return;
            }
        }
    }

    private static final class TipButton extends Button implements TooltipAccessor {
        private final Component tip;

        TipButton(int x, int y, int width, int height, Component label, OnPress onPress, Component tip) {
            super(x, y, width, height, label, b -> {
                if (b.active) {
                    onPress.onPress(b);
                }
            });
            this.tip = tip;
        }

        @Override
        public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTick) {
            boolean hovered = this.isHoveredOrFocused();
            GuiShell.flatButton(pose, this.x, this.y, this.width, this.height, hovered, this.active);
            Font font = Minecraft.getInstance().font;
            int color = this.active ? GuiShell.TITLE : GuiShell.MUTED;
            int ty = this.y + (this.height - font.lineHeight) / 2;
            net.minecraft.client.gui.GuiComponent.drawCenteredString(
                    pose, font, this.getMessage(), this.x + this.width / 2, ty, color);
        }

        @Override
        public List<FormattedCharSequence> getTooltip() {
            return tipLines(this.tip);
        }
    }

    private static final class TipEditBox extends EditBox implements TooltipAccessor {
        private final Component tip;
        private Component placeholder;

        TipEditBox(int x, int y, int w, int h, Component message, Component tip) {
            super(Minecraft.getInstance().font, x, y, w, h, message);
            this.tip = tip;
        }

        public void setPlaceholder(Component c) {
            this.placeholder = c;
        }

        @Override
        public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTick) {
            if (this.placeholder == null) {
                super.renderButton(pose, mouseX, mouseY, partialTick);
                return;
            }
            int x0 = this.x;
            int y0 = this.y;
            int x1 = x0 + this.width;
            int y1 = y0 + this.height;
            net.minecraft.client.gui.GuiComponent.fill(pose, x0, y0, x1, y1, GuiShell.FILL_PRIMARY);
            int border = this.isFocused() ? GuiShell.ACCENT : GuiShell.ACCENT_DIM;
            net.minecraft.client.gui.GuiComponent.fill(pose, x0, y0, x1, y0 + 1, border);
            net.minecraft.client.gui.GuiComponent.fill(pose, x0, y1 - 1, x1, y1, border);
            net.minecraft.client.gui.GuiComponent.fill(pose, x0, y0, x0 + 1, y1, border);
            net.minecraft.client.gui.GuiComponent.fill(pose, x1 - 1, y0, x1, y1, border);
            Font font = Minecraft.getInstance().font;
            int ty = y0 + (this.height - font.lineHeight) / 2;
            String value = this.getValue();
            if (value.isEmpty() && this.placeholder != null) {
                net.minecraft.client.gui.GuiComponent.drawString(
                        pose, font, this.placeholder, x0 + 4, ty, GuiShell.MUTED);
                if (this.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
                    net.minecraft.client.gui.GuiComponent.fill(
                            pose, x0 + 4, ty, x0 + 5, ty + font.lineHeight, GuiShell.TITLE);
                }
            } else {
                int inner = this.getInnerWidth();
                String visible = font.plainSubstrByWidth(value, inner);
                net.minecraft.client.gui.GuiComponent.drawString(pose, font, visible, x0 + 4, ty, GuiShell.TITLE);
                if (this.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
                    int caret = Math.min(this.getCursorPosition(), value.length());
                    String beforeCaret = font.plainSubstrByWidth(value.substring(0, caret), inner);
                    int cx = x0 + 4 + font.width(beforeCaret);
                    net.minecraft.client.gui.GuiComponent.fill(
                            pose, cx, ty, cx + 1, ty + font.lineHeight, GuiShell.TITLE);
                }
            }
        }

        @Override
        public List<FormattedCharSequence> getTooltip() {
            if (this.tip == null) {
                return List.of();
            }
            return tipLines(this.tip);
        }
    }
}

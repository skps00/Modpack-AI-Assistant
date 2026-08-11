package com.skps9.packai.client.gui;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
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
        return new Button(x, y, width, height, label, onPress);
    }

    public static Button button(int x, int y, int width, int height, Component label, Button.OnPress onPress,
            Component tip) {
        return new TipButton(x, y, width, height, label, onPress, tip);
    }

    public static EditBox editBox(int x, int y, int w, int h, Component message, Component tip) {
        return new TipEditBox(x, y, w, h, message, tip);
    }

    /** Wrap tooltip text for CycleButton.withTooltip on 1.19.2. */
    public static List<FormattedCharSequence> tipLines(Component tip) {
        Minecraft mc = Minecraft.getInstance();
        int wrap = mc != null && mc.screen != null ? Math.max(mc.screen.width / 2, 200) : 200;
        return mc != null ? mc.font.split(tip, wrap) : List.of();
    }

    public static List<FormattedCharSequence> tipLines(String langKey) {
        return tipLines(Component.translatable(langKey));
    }

    /**
     * Paint tip for the hovered {@link TooltipAccessor} (CycleButton / TipButton / TipEditBox).
     * Call after {@code super.render} so tip draws on top.
     */
    public static void renderHoveredTips(Screen screen, PoseStack pose, int mouseX, int mouseY) {
        if (screen == null) {
            return;
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
            super(x, y, width, height, label, onPress);
            this.tip = tip;
        }

        @Override
        public List<FormattedCharSequence> getTooltip() {
            return tipLines(this.tip);
        }
    }

    private static final class TipEditBox extends EditBox implements TooltipAccessor {
        private final Component tip;

        TipEditBox(int x, int y, int w, int h, Component message, Component tip) {
            super(Minecraft.getInstance().font, x, y, w, h, message);
            this.tip = tip;
        }

        @Override
        public List<FormattedCharSequence> getTooltip() {
            return tipLines(this.tip);
        }
    }
}

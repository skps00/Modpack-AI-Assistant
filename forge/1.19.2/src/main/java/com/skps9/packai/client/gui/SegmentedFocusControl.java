package com.skps9.packai.client.gui;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Three-way focus mode selector (item / block / entity) for the assistant bottom row.
 */
public class SegmentedFocusControl extends AbstractWidget {
    public static final int SEGMENT_WIDTH = 44;
    public static final int DEFAULT_WIDTH = SEGMENT_WIDTH * 3;
    public static final int DEFAULT_HEIGHT = 20;

    private static final String[] MODES = {"auto", "block", "entity"};
    private static final Component[] LABELS = {
            Component.translatable("packai.focus.pick_item"),
            Component.translatable("packai.focus.pick_block"),
            Component.translatable("packai.focus.pick_entity")
    };

    private final Supplier<String> selectedMode;
    private final Consumer<String> onSelect;

    public SegmentedFocusControl(
            int x, int y, int width, int height, Supplier<String> selectedMode, Consumer<String> onSelect) {
        super(x, y, width, height, Component.empty());
        this.selectedMode = selectedMode;
        this.onSelect = onSelect;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible || !this.isValidClickButton(button)) {
            return false;
        }
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int segW = SEGMENT_WIDTH;
        int rel = (int) (mouseX - this.x) - 1;
        int idx = Mth.clamp(rel / segW, 0, 2);
        onSelect.accept(MODES[idx]);
        this.playDownSound(Minecraft.getInstance().getSoundManager());
        return true;
    }

    @Override
    public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        String selected = selectedMode.get();
        int segW = SEGMENT_WIDTH;
        int y0 = this.y;
        int y1 = this.y + this.height;

        fillRect(pose, this.x, y0, this.x + this.width, y1, GuiShell.FILL_SECONDARY);
        fillRect(pose, this.x, y0, this.x + this.width, y0 + 1, GuiShell.BORDER);
        fillRect(pose, this.x, y1 - 1, this.x + this.width, y1, GuiShell.BORDER);
        fillRect(pose, this.x, y0, this.x + 1, y1, GuiShell.BORDER);
        fillRect(pose, this.x + this.width - 1, y0, this.x + this.width, y1, GuiShell.BORDER);

        for (int i = 0; i < 3; i++) {
            int sx = this.x + 1 + i * segW;
            int ex = i == 2 ? this.x + this.width - 1 : sx + segW;
            boolean sel = MODES[i].equals(selected);
            int segFill = sel ? GuiShell.ACCENT_DIM : GuiShell.FILL_PRIMARY;
            fillRect(pose, sx, y0 + 1, ex, y1 - 1, segFill);
            if (i > 0) {
                fillRect(pose, sx, y0 + 1, sx + 1, y1 - 1, GuiShell.HAIRLINE);
            }
            int textColor = sel ? GuiShell.TITLE : GuiShell.MUTED;
            Component label = LABELS[i];
            int tw = font.width(label);
            int tx = sx + (ex - sx - tw) / 2;
            int ty = y0 + (this.height - font.lineHeight) / 2;
            font.draw(pose, label, tx, ty, textColor);
        }
    }

    private static void fillRect(PoseStack pose, int x0, int y0, int x1, int y1, int color) {
        net.minecraft.client.gui.GuiComponent.fill(pose, x0, y0, x1, y1, color);
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}

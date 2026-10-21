package com.skps9.packai.client.gui;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.TooltipAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/** Sidebar button with a 16×16 vanilla item icon and label centered to the right of the icon. */
public class IconTextButton extends Button implements TooltipAccessor {
    private final Component label;
    private final ItemStack icon;
    private final Component tooltip;

    public IconTextButton(
            int x,
            int y,
            int width,
            int height,
            Component label,
            ItemStack icon,
            OnPress onPress,
            Component tooltip) {
        super(x, y, width, height, Component.empty(), onPress);
        this.label = label;
        this.icon = icon == null ? ItemStack.EMPTY : icon;
        this.tooltip = tooltip;
    }

    @Override
    public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHoveredOrFocused();
        GuiShell.flatButton(pose, this.x, this.y, this.width, this.height, hovered, this.active);
        Minecraft mc = Minecraft.getInstance();
        if (!this.icon.isEmpty()) {
            int ix = this.x + 4;
            int iy = this.y + (this.height - 16) / 2;
            mc.getItemRenderer().renderGuiItem(this.icon, ix, iy);
            mc.getItemRenderer().renderGuiItemDecorations(mc.font, this.icon, ix, iy);
        }
        Font font = mc.font;
        int cx = this.x + 20 + (this.width - 24) / 2;
        int ty = this.y + (this.height - font.lineHeight) / 2;
        int color = this.active ? GuiShell.TITLE : GuiShell.MUTED;
        net.minecraft.client.gui.GuiComponent.drawCenteredString(pose, font, this.label, cx, ty, color);
    }

    @Override
    public List<FormattedCharSequence> getTooltip() {
        return WidgetCompat.tipLines(this.tooltip);
    }
}

package com.skps9.packai.client.gui;

import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Minimal 1.19.2 shim for NeoForge-style screen code that expects GuiGraphics.
 */
public final class GuiGraphics {
    private final Minecraft minecraft;
    private final Screen screen;
    private final PoseStack pose;

    public GuiGraphics(Minecraft minecraft, Screen screen, PoseStack pose) {
        this.minecraft = minecraft;
        this.screen = screen;
        this.pose = pose;
    }

    public PoseStack pose() {
        return this.pose;
    }

    public void fill(int left, int top, int right, int bottom, int color) {
        GuiComponent.fill(this.pose, left, top, right, bottom, color);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        return drawString(font, text == null ? FormattedCharSequence.EMPTY : text.getVisualOrderText(), x, y, color, shadow);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        String shown = text == null ? "" : text;
        return shadow ? font.drawShadow(this.pose, shown, x, y, color) : font.draw(this.pose, shown, x, y, color);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        FormattedCharSequence shown = text == null ? FormattedCharSequence.EMPTY : text;
        return shadow ? font.drawShadow(this.pose, shown, x, y, color) : font.draw(this.pose, shown, x, y, color);
    }

    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        GuiComponent.drawCenteredString(this.pose, font, text, x, y, color);
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        GuiComponent.drawCenteredString(this.pose, font, text, x, y, color);
    }

    public void drawCenteredString(Font font, FormattedCharSequence text, int x, int y, int color) {
        FormattedCharSequence shown = text == null ? FormattedCharSequence.EMPTY : text;
        drawString(font, shown, x - font.width(shown) / 2, y, color, false);
    }

    public void renderItem(ItemStack stack, int x, int y) {
        if (this.minecraft == null || stack == null || stack.isEmpty()) {
            return;
        }
        RenderSystem.enableDepthTest();
        // Forge shim: one-call sites (AiAssistant) expect count overlay here.
        this.minecraft.getItemRenderer().renderGuiItem(stack, x, y);
        this.minecraft.getItemRenderer().renderGuiItemDecorations(this.minecraft.font, stack, x, y);
    }

    /** Count / durability overlay — Neo GuiGraphics.renderItemDecorations. */
    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        if (this.minecraft == null || stack == null || stack.isEmpty()) {
            return;
        }
        this.minecraft.getItemRenderer().renderGuiItemDecorations(
                font != null ? font : this.minecraft.font, stack, x, y);
    }

    public void renderTooltip(Font font, ItemStack stack, int mouseX, int mouseY) {
        if (this.screen == null || stack == null || stack.isEmpty()) {
            return;
        }
        // Direct calls remap in reobf jar. Reflection on "renderTooltip" string fails in NFWC (SRG names).
        this.screen.renderComponentTooltip(this.pose, this.screen.getTooltipFromItem(stack), mouseX, mouseY);
    }

    public void renderTooltip(Font font, List<Component> lines, Optional<?> tooltip, int mouseX, int mouseY) {
        if (this.screen == null || lines == null || lines.isEmpty()) {
            return;
        }
        this.screen.renderComponentTooltip(this.pose, lines, mouseX, mouseY);
    }

    public void enableScissor(int left, int top, int right, int bottom) {
        if (this.minecraft == null) {
            return;
        }
        Window window = this.minecraft.getWindow();
        double scale = window.getGuiScale();
        int x = (int) Math.floor(left * scale);
        int y = (int) Math.floor(window.getHeight() - bottom * scale);
        int width = Math.max(0, (int) Math.ceil((right - left) * scale));
        int height = Math.max(0, (int) Math.ceil((bottom - top) * scale));
        RenderSystem.enableScissor(x, y, width, height);
    }

    public void disableScissor() {
        RenderSystem.disableScissor();
    }

    public void blit(int x, int y, int z, int width, int height, TextureAtlasSprite sprite, float r, float g, float b, float a) {
        // ponytail: 1.19.2 shim uses flat tint blocks for atlas sprites; upgrade to textured quad blit if exact fluid art becomes necessary.
        int rr = clampColor(r);
        int gg = clampColor(g);
        int bb = clampColor(b);
        int aa = clampColor(a);
        fill(x, y, x + width, y + height, (aa << 24) | (rr << 16) | (gg << 8) | bb);
    }

    private static int clampColor(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }
}

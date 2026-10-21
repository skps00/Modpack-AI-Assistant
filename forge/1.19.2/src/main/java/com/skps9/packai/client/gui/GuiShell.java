package com.skps9.packai.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Shared Pack AI screen chrome (Ask + settings). Visual only — no Ask/search semantics.
 */
public final class GuiShell {
    /** Primary chat / content well. */
    public static final int FILL_PRIMARY = 0xFF0A0E16;
    /** Sidebar / secondary column. */
    public static final int FILL_SECONDARY = 0xFF0E121C;
    /** Nested settings body. */
    public static final int FILL_BODY = 0xFF0C1018;
    /** Search popover / floating lists. */
    public static final int FILL_POPOVER = 0xF010141E;
    public static final int BORDER = 0xFF3A4658;
    public static final int BORDER_SOFT = 0xFF2A3344;
    public static final int ACCENT = 0xFF6EC8FF;
    public static final int ACCENT_DIM = 0xFF3A6A88;
    public static final int TITLE = 0xFFF2F5FA;
    public static final int MUTED = 0xFF9AA4B2;
    public static final int STATUS_OK = 0xFF8CFFB0;
    public static final int HAIRLINE = 0xFF2E3848;

    private GuiShell() {}

    /** Flat sidebar / chrome button fill + 1px border (no vanilla stone texture). */
    public static void flatButton(PoseStack pose, int x, int y, int w, int h, boolean hovered, boolean enabled) {
        int fill = enabled && hovered ? FILL_PRIMARY : FILL_SECONDARY;
        int border = !enabled ? BORDER_SOFT : (enabled && hovered ? ACCENT_DIM : BORDER_SOFT);
        net.minecraft.client.gui.GuiComponent.fill(pose, x, y, x + w, y + h, fill);
        net.minecraft.client.gui.GuiComponent.fill(pose, x, y, x + w, y + 1, border);
        net.minecraft.client.gui.GuiComponent.fill(pose, x, y + h - 1, x + w, y + h, border);
        net.minecraft.client.gui.GuiComponent.fill(pose, x, y, x + 1, y + h, border);
        net.minecraft.client.gui.GuiComponent.fill(pose, x + w - 1, y, x + w, y + h, border);
    }

    /** Filled rect + 1px border. */
    public static void panel(GuiGraphics g, int left, int top, int right, int bottom, int fill, int border) {
        g.fill(left, top, right, bottom, fill);
        // top / bottom
        g.fill(left, top, right, top + 1, border);
        g.fill(left, bottom - 1, right, bottom, border);
        // left / right
        g.fill(left, top, left + 1, bottom, border);
        g.fill(right - 1, top, right, bottom, border);
    }

    /** Accent stripe under the top border (hierarchy cue). */
    public static void accentBar(GuiGraphics g, int left, int top, int right) {
        g.fill(left + 1, top + 1, right - 1, top + 3, ACCENT);
        g.fill(left + 1, top + 3, right - 1, top + 4, ACCENT_DIM);
    }

    public static void hairlineH(GuiGraphics g, int left, int y, int right) {
        g.fill(left, y, right, y + 1, HAIRLINE);
    }

    /** Centered title + short underline (type hierarchy without changing font size). */
    public static void title(GuiGraphics g, Font font, Component text, int centerX, int y) {
        g.drawCenteredString(font, text, centerX, y, TITLE);
        int tw = font.width(text);
        int ux0 = centerX - tw / 2;
        int ux1 = ux0 + tw;
        int uy = y + font.lineHeight + 1;
        g.fill(ux0, uy, ux1, uy + 1, ACCENT);
    }

    public static void mutedCentered(GuiGraphics g, Font font, Component text, int centerX, int y) {
        g.drawCenteredString(font, text, centerX, y, MUTED);
    }

    public static void statusOk(GuiGraphics g, Font font, String text, int centerX, int y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        g.drawCenteredString(font, text, centerX, y, STATUS_OK);
    }

    /** Inner padding from screen edge for panels and header chrome. */
    public static int frameInset() {
        return 8;
    }

    /** Outer nested frame (first draw call on standard screens). */
    public static void screenFrame(GuiGraphics g, int w, int h) {
        nestedShell(g, w, h);
    }

    /** Centered title at y=8 plus full-width accent under the header band. */
    public static void header(GuiGraphics g, Font font, Component title, int w) {
        title(g, font, title, w / 2, 8);
        int inset = frameInset();
        accentBar(g, inset, 20, w - inset);
    }

    /** Screen title only (no accent stripe). */
    public static void titleOnly(GuiGraphics g, Font font, Component title, int w) {
        g.drawCenteredString(font, title, w / 2, 8, TITLE);
    }

    /** Standard nested settings / picker shell (centered body + accent). */
    public static void nestedShell(GuiGraphics g, int screenW, int screenH) {
        bodyShell(g, screenW, screenH);
        int w = Math.min(420, screenW - 40);
        int left = (screenW - w) / 2;
        accentBar(g, left - 8, 18, left + w + 8);
    }

    /** Centered body panel without accent stripe (pickers). */
    public static void bodyShell(GuiGraphics g, int screenW, int screenH) {
        int w = Math.min(420, screenW - 40);
        int left = (screenW - w) / 2;
        int l = left - 8;
        int r = left + w + 8;
        int t = 18;
        int b = screenH - 28;
        panel(g, l, t, r, b, FILL_BODY, BORDER);
    }
}

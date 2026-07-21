package com.skps9.packai.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Segmented hold progress bar (Create Ponder style). */
public final class ThinkProgressBar {
    private static final int SEG_W = 2;
    private static final int SEG_H = 9;
    private static final int GAP = 1;

    private ThinkProgressBar() {}

    public static int width(int segments) {
        int n = Math.max(1, segments);
        return n * SEG_W + (n - 1) * GAP;
    }

    public static void draw(GuiGraphics graphics, int x, int y, int filled, int segments) {
        int n = Math.max(1, segments);
        int fill = Math.min(n, Math.max(0, filled));
        for (int i = 0; i < n; i++) {
            int sx = x + i * (SEG_W + GAP);
            int color = i < fill ? 0xFF_E8_E8_E8 : 0xFF_55_55_55;
            graphics.fill(sx, y, sx + SEG_W, y + SEG_H, color);
        }
    }

    /** Legacy TooltipComponent path (inventory tooltips that use GatherComponents). */
    public record Tooltip(int filled, int segments) implements TooltipComponent {
        public static ClientTooltipComponent create(Tooltip tip) {
            return new Component(tip.filled, tip.segments);
        }

        private static final class Component implements ClientTooltipComponent {
            private final int filled;
            private final int segments;

            Component(int filled, int segments) {
                this.filled = filled;
                this.segments = Math.max(1, segments);
            }

            @Override
            public int getHeight() {
                return SEG_H;
            }

            @Override
            public int getWidth(Font font) {
                return ThinkProgressBar.width(segments);
            }

            @Override
            public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
                ThinkProgressBar.draw(graphics, x, y, filled, segments);
            }
        }
    }
}

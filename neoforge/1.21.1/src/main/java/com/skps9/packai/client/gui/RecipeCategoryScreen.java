package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.skps9.packai.client.jei.JeiCategoryCatalog;
import com.skps9.packai.config.PackAiConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Toggle JEI recipe categories on/off and drag to set display priority.
 */
public class RecipeCategoryScreen extends Screen {
    private static final int ROW_H = 20;
    private static final int TOGGLE_W = 22;
    private static final int HANDLE_W = 14;

    private final Screen parent;
    private EditBox search;
    private List<JeiCategoryCatalog.Row> allRows = List.of();
    private List<Integer> filteredIdx = List.of();
    private int scrollOffset;
    private int listLeft;
    private int listWidth;
    private int listTop;
    private int listBottom;
    /** Index into {@link #allRows} while dragging; -1 = idle. */
    private int dragFrom = -1;
    private int dragHoverInsert = -1;

    public RecipeCategoryScreen(Screen parent) {
        super(Component.translatable("packai.recipe_cats.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(400, this.width - 40);
        this.listLeft = (this.width - w) / 2;
        this.listWidth = w;
        this.listTop = 58;
        this.listBottom = this.height - 56;

        this.search = new EditBox(this.font, this.listLeft, 32, w, 20,
                Component.translatable("packai.recipe_cats.search"));
        this.search.setMaxLength(128);
        this.search.setHint(Component.translatable("packai.recipe_cats.search_hint"));
        this.search.setTooltip(Tooltip.create(Component.translatable("packai.recipe_cats.tooltip.search")));
        this.search.setResponder(s -> applyFilter());
        this.addRenderableWidget(this.search);

        int half = (w - 8) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("packai.recipe_cats.reset"), b -> resetPrefs())
                .bounds(this.listLeft, this.height - 28, half, 20)
                .tooltip(Tooltip.create(Component.translatable("packai.recipe_cats.tooltip.reset")))
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.listLeft + half + 8, this.height - 28, half, 20)
                .tooltip(Tooltip.create(Component.translatable("packai.recipe_cats.tooltip.done")))
                .build());

        reloadRows();
        this.setInitialFocus(this.search);
    }

    private void reloadRows() {
        this.allRows = new ArrayList<>(JeiCategoryCatalog.rowsForUi());
        applyFilter();
    }

    private void resetPrefs() {
        PackAiConfig.resetRecipeCategoryPrefs();
        this.dragFrom = -1;
        reloadRows();
    }

    private void applyFilter() {
        String q = this.search == null ? "" : this.search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Integer> next = new ArrayList<>();
        for (int i = 0; i < this.allRows.size(); i++) {
            JeiCategoryCatalog.Row r = this.allRows.get(i);
            if (q.isEmpty()
                    || r.title().toLowerCase(Locale.ROOT).contains(q)
                    || r.uid().toLowerCase(Locale.ROOT).contains(q)) {
                next.add(i);
            }
        }
        this.filteredIdx = next;
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll());
    }

    private int visibleRows() {
        return Math.max(1, (this.listBottom - this.listTop) / ROW_H);
    }

    private int maxScroll() {
        return Math.max(0, this.filteredIdx.size() - visibleRows());
    }

    private void persist() {
        JeiCategoryCatalog.saveRows(this.allRows);
    }

    private void toggleAt(int allIndex) {
        if (allIndex < 0 || allIndex >= this.allRows.size()) {
            return;
        }
        JeiCategoryCatalog.Row r = this.allRows.get(allIndex);
        this.allRows.set(allIndex, r.withEnabled(!r.enabled()));
        persist();
    }

    /**
     * Move row from {@code from} so it ends up at {@code insertBefore}
     * (0 = top, {@code size} = bottom).
     */
    private void moveRow(int from, int insertBefore) {
        if (from < 0 || from >= this.allRows.size()) {
            return;
        }
        int to = Mth.clamp(insertBefore, 0, this.allRows.size());
        if (from == to || from + 1 == to) {
            return;
        }
        JeiCategoryCatalog.Row row = this.allRows.remove(from);
        if (from < to) {
            to--;
        }
        to = Mth.clamp(to, 0, this.allRows.size());
        this.allRows.add(to, row);
        persist();
        applyFilter();
    }

    /** Insert-before index in {@link #allRows} from mouse Y (unfiltered list only). */
    private int insertIndexAt(double mouseY) {
        double rel = (mouseY - this.listTop) / ROW_H + this.scrollOffset;
        int idx = (int) Math.floor(rel + 0.5); // snap to nearest gap
        return Mth.clamp(idx, 0, this.allRows.size());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        if (!JeiCategoryCatalog.jeiAvailable()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("packai.recipe_cats.no_jei"),
                    this.width / 2, (this.listTop + this.listBottom) / 2, 0xFFAAAA);
            return;
        }

        graphics.fill(this.listLeft - 2, this.listTop - 2,
                this.listLeft + this.listWidth + 2, this.listBottom + 2, 0x66000000);

        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll());
        int y = this.listTop;
        int end = Math.min(this.filteredIdx.size(), this.scrollOffset + visibleRows());
        for (int fi = this.scrollOffset; fi < end; fi++) {
            int allIndex = this.filteredIdx.get(fi);
            JeiCategoryCatalog.Row row = this.allRows.get(allIndex);
            boolean dragging = this.dragFrom == allIndex;
            boolean hover = !dragging
                    && mouseX >= this.listLeft && mouseX <= this.listLeft + this.listWidth
                    && mouseY >= y && mouseY < y + ROW_H;
            if (dragging) {
                graphics.fill(this.listLeft, y, this.listLeft + this.listWidth, y + ROW_H, 0x664488FF);
            } else if (hover) {
                graphics.fill(this.listLeft, y, this.listLeft + this.listWidth, y + ROW_H, 0x33FFFFFF);
            }
            // drop-line: show gap above this row when insert index matches
            if (this.dragFrom >= 0 && this.dragHoverInsert == allIndex) {
                graphics.fill(this.listLeft, y, this.listLeft + this.listWidth, y + 2, 0xFFE0E0E0);
            }
            if (this.dragFrom >= 0 && this.dragHoverInsert == this.allRows.size()
                    && fi == end - 1) {
                graphics.fill(this.listLeft, y + ROW_H - 2, this.listLeft + this.listWidth, y + ROW_H, 0xFFE0E0E0);
            }

            // toggle box
            int tx = this.listLeft + 4;
            int ty = y + 4;
            graphics.fill(tx, ty, tx + 12, ty + 12, row.enabled() ? 0xFF55AA55 : 0xFF555555);
            graphics.fill(tx + 1, ty + 1, tx + 11, ty + 11, row.enabled() ? 0xFF88FF88 : 0xFF333333);
            if (row.enabled()) {
                graphics.drawString(this.font, "✓", tx + 2, ty + 1, 0x003300, false);
            }

            // drag handle
            int hx = this.listLeft + TOGGLE_W + 2;
            graphics.drawString(this.font, "≡", hx, y + 5, 0xAAAAAA, false);

            int textLeft = this.listLeft + TOGGLE_W + HANDLE_W + 4;
            int color = row.enabled() ? 0xE0E0E0 : 0x777777;
            String label = row.title();
            int maxW = this.listWidth - (textLeft - this.listLeft) - 8;
            if (this.font.width(label) > maxW) {
                label = this.font.plainSubstrByWidth(label, maxW - 8) + "…";
            }
            graphics.drawString(this.font, label, textLeft, y + 5, color, false);
            y += ROW_H;
        }

        if (this.allRows.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("packai.recipe_cats.empty"),
                    this.width / 2, (this.listTop + this.listBottom) / 2, 0xAAAAAA);
        } else if (this.filteredIdx.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("packai.recipe_cats.no_match"),
                    this.width / 2, (this.listTop + this.listBottom) / 2, 0xAAAAAA);
        } else {
            graphics.drawString(this.font, Component.translatable("packai.recipe_cats.hint"),
                    this.listLeft, this.listBottom + 6, 0x888888, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && JeiCategoryCatalog.jeiAvailable()
                && mouseX >= this.listLeft && mouseX <= this.listLeft + this.listWidth
                && mouseY >= this.listTop && mouseY < this.listBottom) {
            int row = this.scrollOffset + (int) ((mouseY - this.listTop) / ROW_H);
            if (row >= 0 && row < this.filteredIdx.size()) {
                int allIndex = this.filteredIdx.get(row);
                double localX = mouseX - this.listLeft;
                if (localX < TOGGLE_W) {
                    toggleAt(allIndex);
                    return true;
                }
                // start drag (search filter: only allow drag when unfiltered for stable order)
                if (searchBlank()) {
                    this.dragFrom = allIndex;
                    this.dragHoverInsert = allIndex;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean searchBlank() {
        return this.search == null || this.search.getValue().isBlank();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.dragFrom >= 0 && searchBlank()) {
            if (mouseY < this.listTop + 8 && this.scrollOffset > 0) {
                this.scrollOffset--;
            } else if (mouseY > this.listBottom - 8 && this.scrollOffset < maxScroll()) {
                this.scrollOffset++;
            }
            this.dragHoverInsert = insertIndexAt(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.dragFrom >= 0) {
            int from = this.dragFrom;
            int insertBefore = this.dragHoverInsert >= 0 ? this.dragHoverInsert : insertIndexAt(mouseY);
            this.dragFrom = -1;
            this.dragHoverInsert = -1;
            moveRow(from, insertBefore);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= this.listLeft - 4 && mouseX <= this.listLeft + this.listWidth + 4
                && mouseY >= this.listTop && mouseY <= this.listBottom) {
            this.scrollOffset = Mth.clamp(this.scrollOffset - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

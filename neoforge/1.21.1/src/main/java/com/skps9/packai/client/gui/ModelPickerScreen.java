package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.ModelCatalog;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Searchable model picker — replaces the cycle button for long cloud/Ollama lists.
 */
public class ModelPickerScreen extends Screen {
    private static final int ROW_H = 18;

    private final Screen parent;
    private EditBox search;
    private List<String> allModels = List.of();
    private List<String> filtered = List.of();
    private int scrollOffset;
    private int listLeft;
    private int listWidth;
    private int listTop;
    private int listBottom;
    private boolean autoRefreshScheduled;
    private String status = "";

    public ModelPickerScreen(Screen parent) {
        super(Component.translatable("packai.model_picker.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(360, this.width - 40);
        this.listLeft = (this.width - w) / 2;
        this.listWidth = w;
        this.listTop = 58;
        this.listBottom = this.height - 52;

        this.search = new EditBox(this.font, this.listLeft, 32, w - 88, 20,
                Component.translatable("packai.model_picker.search"));
        this.search.setMaxLength(128);
        this.search.setHint(Component.translatable("packai.model_picker.search_hint"));
        this.search.setTooltip(Tooltip.create(Component.translatable("packai.model_picker.tooltip.search")));
        this.search.setResponder(s -> applyFilter());
        this.addRenderableWidget(this.search);

        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.refresh_models"), b -> refreshModels())
                .bounds(this.listLeft + w - 84, 32, 84, 20)
                .tooltip(Tooltip.create(Component.translatable("packai.model_picker.tooltip.refresh")))
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.listLeft, this.height - 28, w, 20)
                .tooltip(Tooltip.create(Component.translatable("packai.model_picker.tooltip.done")))
                .build());

        this.allModels = new ArrayList<>(ModelCatalog.optionsForUi());
        applyFilter();
        this.setInitialFocus(this.search);

        if (!this.autoRefreshScheduled) {
            this.autoRefreshScheduled = true;
            ModelCatalog.refreshAsync(() -> {
                if (this.minecraft != null && this.minecraft.screen == this) {
                    this.allModels = new ArrayList<>(ModelCatalog.optionsForUi());
                    applyFilter();
                }
            });
        }
    }

    private void refreshModels() {
        this.status = Component.translatable("packai.status.models_refreshing").getString();
        ModelCatalog.invalidate();
        ModelCatalog.refreshAsync(true, () -> {
            if (this.minecraft != null && this.minecraft.screen == this) {
                this.allModels = new ArrayList<>(ModelCatalog.optionsForUi());
                applyFilter();
                this.status = Component.translatable("packai.status.models_refreshed").getString();
            }
        });
    }

    private void applyFilter() {
        String q = this.search == null ? "" : this.search.getValue().trim().toLowerCase(Locale.ROOT);
        List<String> next = new ArrayList<>();
        for (String m : this.allModels) {
            if (q.isEmpty() || m.toLowerCase(Locale.ROOT).contains(q)) {
                next.add(m);
            }
        }
        this.filtered = next;
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll());
    }

    private int visibleRows() {
        return Math.max(1, (this.listBottom - this.listTop) / ROW_H);
    }

    private int maxScroll() {
        return Math.max(0, this.filtered.size() - visibleRows());
    }

    private void select(String model) {
        if (model == null || model.isBlank()) {
            return;
        }
        PackAiConfig.setUiModel(model);
        onClose();
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

        graphics.fill(this.listLeft - 2, this.listTop - 2,
                this.listLeft + this.listWidth + 2, this.listBottom + 2, 0x66000000);

        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll());
        String current = PackAiConfig.uiModel();
        int y = this.listTop;
        int end = Math.min(this.filtered.size(), this.scrollOffset + visibleRows());
        for (int i = this.scrollOffset; i < end; i++) {
            String model = this.filtered.get(i);
            boolean hover = mouseX >= this.listLeft && mouseX <= this.listLeft + this.listWidth
                    && mouseY >= y && mouseY < y + ROW_H;
            boolean selected = model.equals(current);
            if (selected) {
                graphics.fill(this.listLeft, y, this.listLeft + this.listWidth, y + ROW_H, 0x664488FF);
            } else if (hover) {
                graphics.fill(this.listLeft, y, this.listLeft + this.listWidth, y + ROW_H, 0x33FFFFFF);
            }
            int color = selected ? 0xFFE0E0 : 0xE0E0E0;
            String label = model;
            if (this.font.width(label) > this.listWidth - 8) {
                label = this.font.plainSubstrByWidth(label, this.listWidth - 16) + "…";
            }
            graphics.drawString(this.font, label, this.listLeft + 4, y + 5, color, false);
            y += ROW_H;
        }

        if (this.filtered.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("packai.model_picker.empty"),
                    this.width / 2, (this.listTop + this.listBottom) / 2, 0xAAAAAA);
        } else if (maxScroll() > 0) {
            graphics.drawString(this.font, Component.translatable("packai.model_picker.scroll"),
                    this.listLeft, this.listBottom + 6, 0x888888, false);
        }

        if (!this.status.isEmpty()) {
            graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height - 40, 0xA0FFA0);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= this.listLeft && mouseX <= this.listLeft + this.listWidth
                && mouseY >= this.listTop && mouseY < this.listBottom) {
            int row = this.scrollOffset + (int) ((mouseY - this.listTop) / ROW_H);
            if (row >= 0 && row < this.filtered.size()) {
                select(this.filtered.get(row));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

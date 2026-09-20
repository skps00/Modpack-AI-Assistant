package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.ModelCatalog;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Unified model picker: cloud + local sections; writes {@link PackAiConfig#setCloudModel} /
 * {@link PackAiConfig#setOllamaModel} (never mode-driven {@code setUiModel}).
 */
public class ModelPickerScreen extends Screen {
    private static final int ROW_H = 18;

    /** Write target for a selectable model row. */
    public enum Target {
        CLOUD,
        LOCAL
    }

    /** One list row: section header, status line, or selectable model. */
    public static final class Row {
        public enum Kind {
            HEADER,
            STATUS,
            MODEL
        }

        public final Kind kind;
        public final Target target;
        /** Lang key for HEADER/STATUS; model id for MODEL. */
        public final String text;

        private Row(Kind kind, Target target, String text) {
            this.kind = kind;
            this.target = target;
            this.text = text;
        }

        public static Row header(String langKey) {
            return new Row(Kind.HEADER, null, langKey);
        }

        public static Row status(String langKey) {
            return new Row(Kind.STATUS, null, langKey);
        }

        public static Row model(Target target, String modelId) {
            return new Row(Kind.MODEL, target, modelId);
        }

        public boolean selectable() {
            return kind == Kind.MODEL;
        }
    }

    private final Screen parent;
    private EditBox search;
    private List<Row> allRows = List.of();
    private List<Row> filtered = List.of();
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

        this.search = WidgetCompat.editBox(this.listLeft, 32, w - 88, 20,
                Component.translatable("packai.model_picker.search"),
                Component.translatable("packai.model_picker.tooltip.search"));
        this.search.setMaxLength(128);
        this.search.setResponder(s -> applyFilter());
        this.addRenderableWidget(this.search);

        this.addRenderableWidget(WidgetCompat.button(this.listLeft + w - 84, 32, 84, 20,
                Component.translatable("packai.screen.refresh_models"), b -> refreshModels(),
                Component.translatable("packai.model_picker.tooltip.refresh")));

        this.addRenderableWidget(WidgetCompat.button(this.listLeft, this.height - 28, w, 20,
                Component.translatable("gui.done"), b -> onClose(),
                Component.translatable("packai.model_picker.tooltip.done")));

        rebuildRowsFromCache();
        this.setInitialFocus(this.search);

        if (!this.autoRefreshScheduled) {
            this.autoRefreshScheduled = true;
            // Force both backends (D 5A.2) — ignore mode.
            ModelCatalog.refreshAsync(true, () -> {
                if (this.minecraft != null && this.minecraft.screen == this) {
                    rebuildRowsFromCache();
                }
            });
        }
    }

    private void refreshModels() {
        this.status = Component.translatable("packai.status.models_refreshing").getString();
        ModelCatalog.invalidate();
        ModelCatalog.refreshAsync(true, () -> {
            if (this.minecraft != null && this.minecraft.screen == this) {
                rebuildRowsFromCache();
                this.status = Component.translatable("packai.status.models_refreshed").getString();
            }
        });
    }

    private void rebuildRowsFromCache() {
        String cloudCur = safe(PackAiConfig.MODEL.get());
        String localCur = safe(PackAiConfig.OLLAMA_MODEL.get());
        this.allRows = buildRows(
                ModelCatalog.cloudLive(),
                ModelCatalog.ollamaLive(),
                cloudCur,
                localCur,
                ModelCatalog.cloudHasLive(),
                ModelCatalog.ollamaHasLive());
        applyFilter();
    }

    /**
     * Pure row builder (headless-testable). Empty section omitted; configured value always on top
     * of its section; no hard-coded fallback list when live empty.
     */
    public static List<Row> buildRows(
            List<String> cloudLive,
            List<String> ollamaLive,
            String cloudCurrent,
            String ollamaCurrent,
            boolean cloudOk,
            boolean ollamaOk) {
        List<Row> out = new ArrayList<>();
        appendSection(
                out,
                "packai.model_picker.section.cloud",
                Target.CLOUD,
                cloudLive,
                cloudCurrent,
                cloudOk,
                "packai.model_picker.status.cloud_empty");
        appendSection(
                out,
                "packai.model_picker.section.local",
                Target.LOCAL,
                ollamaLive,
                ollamaCurrent,
                ollamaOk,
                "packai.model_picker.status.ollama_empty");
        return List.copyOf(out);
    }

    private static void appendSection(
            List<Row> out,
            String headerKey,
            Target target,
            List<String> live,
            String current,
            boolean liveOk,
            String emptyStatusKey) {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        if (current != null && !current.isBlank()) {
            models.add(current.trim());
        }
        if (live != null) {
            for (String m : live) {
                if (m != null && !m.isBlank()) {
                    models.add(m.trim());
                }
            }
        }
        // Skip empty section with no configured value.
        if (models.isEmpty() && !liveOk) {
            out.add(Row.header(headerKey));
            out.add(Row.status(emptyStatusKey));
            return;
        }
        if (models.isEmpty()) {
            return;
        }
        out.add(Row.header(headerKey));
        if (!liveOk && (live == null || live.isEmpty())) {
            out.add(Row.status(emptyStatusKey));
        }
        for (String m : models) {
            out.add(Row.model(target, m));
        }
    }

    /** Filter MODEL rows by query; keep HEADER/STATUS of sections that still have a MODEL match. */
    public static List<Row> filterRows(List<Row> all, String queryRaw) {
        String query = queryRaw == null ? "" : queryRaw.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return List.copyOf(all);
        }
        List<Row> out = new ArrayList<>();
        List<Row> pendingHeader = new ArrayList<>();
        boolean sectionHasModel = false;
        for (Row r : all) {
            if (r.kind == Row.Kind.HEADER) {
                flushSection(out, pendingHeader, sectionHasModel);
                pendingHeader.clear();
                pendingHeader.add(r);
                sectionHasModel = false;
                continue;
            }
            if (r.kind == Row.Kind.STATUS) {
                pendingHeader.add(r);
                continue;
            }
            if (r.text.toLowerCase(Locale.ROOT).contains(query)) {
                if (!sectionHasModel) {
                    out.addAll(pendingHeader);
                    sectionHasModel = true;
                    pendingHeader.clear();
                }
                out.add(r);
            }
        }
        flushSection(out, pendingHeader, sectionHasModel);
        return List.copyOf(out);
    }

    private static void flushSection(List<Row> out, List<Row> pending, boolean hasModel) {
        // Drop orphan headers when no model matched.
        if (hasModel) {
            out.addAll(pending);
        }
    }

    private void applyFilter() {
        String query = this.search == null ? "" : this.search.getValue();
        this.filtered = filterRows(this.allRows, query);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll());
    }

    private int visibleRows() {
        return Math.max(1, (this.listBottom - this.listTop) / ROW_H);
    }

    private int maxScroll() {
        return Math.max(0, this.filtered.size() - visibleRows());
    }

    private void select(Row row) {
        if (row == null || !row.selectable() || row.text == null || row.text.isBlank()) {
            return;
        }
        if (row.target == Target.LOCAL) {
            PackAiConfig.setOllamaModel(row.text);
        } else {
            PackAiConfig.setCloudModel(row.text);
        }
        onClose();
    }

    private String highlightFor(Row row) {
        if (row == null || row.target == null) {
            return "";
        }
        if (row.target == Target.LOCAL) {
            return safe(PackAiConfig.OLLAMA_MODEL.get());
        }
        return safe(PackAiConfig.MODEL.get());
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    // Draw-order note (C-0): widgets here sit outside the custom list paint, so
    // title-after-tips is OK. If a widget ever lands inside a custom-painted zone,
    // follow SettingsScreenV2: custom chrome → super.render → tips last.
    private void renderScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics.pose());
        GuiShell.nestedShell(graphics, this.width, this.height);
        super.render(graphics.pose(), mouseX, mouseY, partialTick);
        WidgetCompat.renderHoveredTips(this, graphics.pose(), mouseX, mouseY);
        GuiShell.title(graphics, this.font, this.title, this.width / 2, 6);

        GuiShell.panel(graphics, this.listLeft - 2, this.listTop - 2,
                this.listLeft + this.listWidth + 2, this.listBottom + 2,
                GuiShell.FILL_PRIMARY, GuiShell.BORDER_SOFT);

        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll());
        int y = this.listTop;
        int end = Math.min(this.filtered.size(), this.scrollOffset + visibleRows());
        for (int i = this.scrollOffset; i < end; i++) {
            Row row = this.filtered.get(i);
            boolean hover = row.selectable()
                    && mouseX >= this.listLeft
                    && mouseX <= this.listLeft + this.listWidth
                    && mouseY >= y
                    && mouseY < y + ROW_H;
            String current = highlightFor(row);
            boolean selected = row.selectable() && row.text.equals(current);
            if (selected) {
                graphics.fill(this.listLeft, y, this.listLeft + this.listWidth, y + ROW_H, 0x664488FF);
            } else if (hover) {
                graphics.fill(this.listLeft, y, this.listLeft + this.listWidth, y + ROW_H, 0x33FFFFFF);
            }
            int color;
            String label;
            if (row.kind == Row.Kind.HEADER) {
                color = GuiShell.ACCENT;
                label = Component.translatable(row.text).getString();
            } else if (row.kind == Row.Kind.STATUS) {
                color = GuiShell.MUTED;
                label = Component.translatable(row.text).getString();
            } else {
                color = selected ? 0xFFE0E0 : GuiShell.TITLE;
                label = row.text;
            }
            if (this.font.width(label) > this.listWidth - 8) {
                label = this.font.plainSubstrByWidth(label, this.listWidth - 16) + "...";
            }
            int x = this.listLeft + (row.kind == Row.Kind.HEADER ? 4 : 10);
            graphics.drawString(this.font, label, x, y + 5, color, false);
            y += ROW_H;
        }

        if (this.filtered.isEmpty()) {
            GuiShell.mutedCentered(graphics, this.font,
                    Component.translatable("packai.model_picker.empty"),
                    this.width / 2, (this.listTop + this.listBottom) / 2);
        } else if (maxScroll() > 0) {
            graphics.drawString(this.font, Component.translatable("packai.model_picker.scroll"),
                    this.listLeft, this.listBottom + 6, GuiShell.MUTED, false);
        }

        // Footer tag mirrors settings row tag (same source).
        String tag = Component.translatable(PackAiConfig.effectiveModelTagKey()).getString();
        String footer = PackAiConfig.uiModel() + " (" + tag + ")";
        graphics.drawString(this.font, footer, this.listLeft, this.height - 44, GuiShell.MUTED, false);
        GuiShell.statusOk(graphics, this.font, this.status, this.width / 2, this.height - 40);
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderScreen(new GuiGraphics(this.minecraft, this, pose), mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= this.listLeft && mouseX <= this.listLeft + this.listWidth
                && mouseY >= this.listTop && mouseY < this.listBottom) {
            int row = this.scrollOffset + (int) ((mouseY - this.listTop) / ROW_H);
            if (row >= 0 && row < this.filtered.size()) {
                Row r = this.filtered.get(row);
                if (r.selectable()) {
                    select(r);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (mouseX >= this.listLeft - 4 && mouseX <= this.listLeft + this.listWidth + 4
                && mouseY >= this.listTop && mouseY <= this.listBottom) {
            this.scrollOffset = Mth.clamp(this.scrollOffset - (int) Math.signum(scrollDelta), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

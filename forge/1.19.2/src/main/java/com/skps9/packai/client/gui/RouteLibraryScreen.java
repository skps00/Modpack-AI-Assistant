package com.skps9.packai.client.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skps9.packai.client.routes.RouteStore;
import com.skps9.packai.client.routes.RouteStoreCodec.SavedRoute;
import com.skps9.packai.client.routes.RouteStoreCodec.StepDto;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.ObtainPaths;
import com.skps9.packai.logic.RouteMarks;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import org.lwjgl.glfw.GLFW;

/**
 * Saved obtain-route library: list, preview, and crafting-tree entry.
 */
public class RouteLibraryScreen extends Screen {
    private static final int ROW_H = 18;
    private static final int PREVIEW_MAX = 12;
    /** Step lines wrap to at most this many lines; a 3rd line collapses into a trailing ellipsis. */
    private static final int PREVIEW_LINE_CAP = 2;
    /** Bottom breathing room in a preview row; with a 9px font one line stays 18px tall. */
    private static final int PREVIEW_ROW_PAD = 9;
    private static final int PREVIEW_TEXT_INSET = 3;
    private static final int PREVIEW_STATUS_PAD = 6;
    private static final long CLEAR_CONFIRM_MS = 3000L;
    private static final DateTimeFormatter LIST_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private final Screen parent;

    private List<SavedRoute> routes = List.of();
    private int selected = -1;
    private int listScroll;
    private int listLeft;
    private int listWidth;
    private int listTop;
    private int listBottom;
    private int previewLeft;
    private int previewWidth;
    private int previewTop;
    private int previewBottom;

    private List<Component> previewLines = List.of();
    private int previewHidden;
    private RouteMarks.Result marksResult = new RouteMarks.Result(List.of(), 0, false);
    private long clearConfirmUntil;
    private long lastListClickMs;
    private int lastListClickRow = -1;
    private Button btnOpenTree;
    private Button btnDelete;
    private Button btnClear;
    private Button btnClose;

    public RouteLibraryScreen() {
        this(null);
    }

    public RouteLibraryScreen(@Nullable Screen parent) {
        super(Component.translatable("packai.route_library.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int margin = GuiShell.frameInset();
        int innerW = this.width - margin * 2;
        int leftW = (int) (innerW * 0.45);
        int gap = 8;
        int rightW = innerW - leftW - gap;
        this.listLeft = margin;
        this.listWidth = leftW;
        this.previewLeft = margin + leftW + gap;
        this.previewWidth = rightW;
        this.listTop = GuiShell.frameInset() + 16;
        this.previewTop = this.listTop;
        int bottomBar = this.height - 36;
        this.listBottom = bottomBar - 8;
        this.previewBottom = bottomBar - 8;

        this.routes = RouteStore.load();
        if (this.selected < 0 && !this.routes.isEmpty()) {
            this.selected = 0;
        } else if (this.selected >= this.routes.size()) {
            this.selected = this.routes.isEmpty() ? -1 : this.routes.size() - 1;
        }
        refreshPreview();

        int btnY = this.height - 28;
        int btnH = 20;
        int btnGap = 4;
        int totalBtnW = innerW;
        int each = (totalBtnW - btnGap * 3) / 4;

        this.btnOpenTree = WidgetCompat.button(margin, btnY, each, btnH,
                Component.translatable("packai.route_library.button.open_tree"),
                b -> openTree(),
                Component.translatable("packai.route_library.tooltip.open_tree"));
        this.btnDelete = WidgetCompat.button(margin + each + btnGap, btnY, each, btnH,
                Component.translatable("packai.route_library.button.delete"),
                b -> deleteSelected(),
                Component.translatable("packai.route_library.tooltip.delete"));
        this.btnClear = WidgetCompat.button(margin + (each + btnGap) * 2, btnY, each, btnH,
                clearButtonLabel(),
                b -> clearAll(),
                Component.translatable("packai.route_library.tooltip.clear"));
        this.btnClose = WidgetCompat.button(margin + (each + btnGap) * 3, btnY, each, btnH,
                Component.translatable("gui.done"),
                b -> onClose(),
                Component.translatable("packai.route_library.tooltip.done"));

        this.addRenderableWidget(this.btnOpenTree);
        this.addRenderableWidget(this.btnDelete);
        this.addRenderableWidget(this.btnClear);
        this.addRenderableWidget(this.btnClose);
        updateButtonStates();
    }

    private Component clearButtonLabel() {
        if (System.currentTimeMillis() < this.clearConfirmUntil) {
            return Component.translatable("packai.route_library.button.confirm_clear");
        }
        return Component.translatable("packai.route_library.button.clear");
    }

    private void updateButtonStates() {
        boolean feature = PackAiConfig.routeMarks();
        boolean hasSel = selectedRoute() != null;
        this.btnOpenTree.active = hasSel;
        this.btnDelete.active = feature && hasSel;
        this.btnClear.active = feature && !this.routes.isEmpty();
        if (this.btnClear != null) {
            this.btnClear.setMessage(clearButtonLabel());
        }
    }

    @Nullable
    private SavedRoute selectedRoute() {
        if (this.selected < 0 || this.selected >= this.routes.size()) {
            return null;
        }
        return this.routes.get(this.selected);
    }

    private void selectIndex(int idx) {
        if (idx < 0 || idx >= this.routes.size() || idx == this.selected) {
            if (idx >= 0 && idx < this.routes.size() && idx == this.selected) {
                refreshPreview();
            }
            return;
        }
        this.selected = idx;
        refreshPreview();
        updateButtonStates();
    }

    private void refreshPreview() {
        SavedRoute route = selectedRoute();
        if (route == null) {
            this.previewLines = List.of();
            this.previewHidden = 0;
            this.marksResult = new RouteMarks.Result(List.of(), 0, false);
            return;
        }
        ObtainPaths.Path path = RouteBookmarkInject.toPath(route);
        this.marksResult = RouteMarks.build(List.of(path));
        this.previewLines = buildPreviewLines(route);
        // The overflow count depends on wrapped row heights, i.e. on the font and pane size,
        // so it is computed while rendering.
        this.previewHidden = 0;
    }

    private void openTree() {
        SavedRoute route = selectedRoute();
        if (route == null || this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new RouteTreeScreen(route, this));
    }

    private void deleteSelected() {
        SavedRoute route = selectedRoute();
        if (route == null) {
            return;
        }
        RouteStore.delete(route.itemId());
        this.routes = RouteStore.load();
        if (this.routes.isEmpty()) {
            this.selected = -1;
        } else {
            this.selected = Math.min(this.selected, this.routes.size() - 1);
        }
        refreshPreview();
        updateButtonStates();
    }

    private void clearAll() {
        long now = System.currentTimeMillis();
        if (now < this.clearConfirmUntil) {
            RouteStore.clear();
            this.routes = List.of();
            this.selected = -1;
            this.clearConfirmUntil = 0L;
            refreshPreview();
            updateButtonStates();
            return;
        }
        this.clearConfirmUntil = now + CLEAR_CONFIRM_MS;
        if (this.btnClear != null) {
            this.btnClear.setMessage(clearButtonLabel());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.clearConfirmUntil > 0L && System.currentTimeMillis() >= this.clearConfirmUntil) {
            this.clearConfirmUntil = 0L;
            if (this.btnClear != null) {
                this.btnClear.setMessage(clearButtonLabel());
            }
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void renderScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x99000000);
        GuiShell.bodyShell(graphics, this.width, this.height);
        super.render(graphics.pose(), mouseX, mouseY, partialTick);
        WidgetCompat.renderHoveredTips(this, graphics.pose(), mouseX, mouseY);
        GuiShell.titleOnly(graphics, this.font, this.title, this.width);

        GuiShell.panel(graphics, this.listLeft - 2, this.listTop - 2,
                this.listLeft + this.listWidth + 2, this.listBottom + 2,
                GuiShell.FILL_PRIMARY, GuiShell.BORDER_SOFT);
        GuiShell.panel(graphics, this.previewLeft - 2, this.previewTop - 2,
                this.previewLeft + this.previewWidth + 2, this.previewBottom + 2,
                GuiShell.FILL_SECONDARY, GuiShell.BORDER_SOFT);

        graphics.drawString(this.font,
                Component.translatable("packai.route_library.column.routes"),
                this.listLeft, this.listTop - 14, GuiShell.ACCENT, false);
        graphics.drawString(this.font,
                Component.translatable("packai.route_library.column.preview"),
                this.previewLeft, this.previewTop - 14, GuiShell.ACCENT, false);

        if (!PackAiConfig.routeMarks()) {
            GuiShell.mutedCentered(graphics, this.font,
                    Component.translatable("packai.route_library.hint.feature_off"),
                    this.width / 2, this.listTop + 4);
        }

        renderRouteList(graphics, mouseX, mouseY);
        renderPreview(graphics);
        renderPreviewStatus(graphics);
    }

    private int previewContentBottom() {
        return this.previewBottom - PREVIEW_STATUS_PAD - this.font.lineHeight;
    }

    private void renderPreviewStatus(GuiGraphics graphics) {
        Component status = buildStatusLine();
        String text = status.getString();
        if (text.isEmpty()) {
            return;
        }
        int max = this.previewWidth - 8;
        if (this.font.width(text) > max) {
            text = this.font.plainSubstrByWidth(text, max - this.font.width("...")) + "...";
        }
        int y = this.previewBottom - PREVIEW_STATUS_PAD - this.font.lineHeight;
        graphics.drawString(this.font, text, this.previewLeft + 4, y, GuiShell.MUTED, false);
    }

    private Component buildStatusLine() {
        SavedRoute route = selectedRoute();
        if (route == null) {
            return Component.empty();
        }
        net.minecraft.network.chat.MutableComponent out = route.complete()
                ? Component.translatable("packai.route_library.status.complete")
                : Component.translatable("packai.route_library.status.incomplete");
        if (this.marksResult.truncatedCount() > 0) {
            out.append(Component.literal(" · "))
                    .append(Component.translatable(
                            "packai.route_library.status.truncated",
                            this.marksResult.truncatedCount()));
        }
        return out;
    }

    private void renderRouteList(GuiGraphics graphics, int mouseX, int mouseY) {
        this.listScroll = Mth.clamp(this.listScroll, 0, maxListScroll());
        int y = this.listTop;
        int end = Math.min(this.routes.size(), this.listScroll + visibleListRows());
        for (int i = this.listScroll; i < end; i++) {
            SavedRoute r = this.routes.get(i);
            boolean hover = mouseX >= this.listLeft
                    && mouseX <= this.listLeft + this.listWidth
                    && mouseY >= y
                    && mouseY < y + ROW_H;
            boolean sel = i == this.selected;
            if (sel) {
                graphics.fill(this.listLeft, y, this.listLeft + this.listWidth, y + ROW_H, 0x664488FF);
            } else if (hover) {
                graphics.fill(this.listLeft, y, this.listLeft + this.listWidth, y + ROW_H, 0x33FFFFFF);
            }
            String name = routeListTitle(r);
            String time = formatCreatedAt(r.createdAt());
            int steps = r.steps() == null ? 0 : r.steps().size();
            // Widest label first; on overflow the timestamp goes, then the step count, so the
            // item name always survives (with an ellipsis if even the name is too wide).
            List<String> labels = new ArrayList<>(3);
            labels.add(Component.translatable(
                    "packai.route_library.list.row", name, time, steps).getString());
            labels.add(Component.translatable(
                    "packai.route_library.list.row_no_time", name, steps).getString());
            labels.add(Component.translatable(
                    "packai.route_library.list.row_name", name).getString());
            String text = RouteTextWrap.pickListLabel(
                    labels, this.listWidth - 8, this.font::width);
            graphics.drawString(this.font, text, this.listLeft + 4, y + 5, GuiShell.TITLE, false);
            y += ROW_H;
        }
        if (this.routes.isEmpty()) {
            GuiShell.mutedCentered(graphics, this.font,
                    Component.translatable("packai.route_library.list.empty"),
                    this.listLeft + this.listWidth / 2, (this.listTop + this.listBottom) / 2);
        }
    }

    private int previewMaxTextWidth() {
        return Math.max(0, this.previewWidth - 8);
    }

    /** Wrap every previewable step row once per frame; layout and drawing share the same result. */
    private List<List<String>> layoutPreviewRows() {
        int maxTextW = previewMaxTextWidth();
        int end = Math.min(this.previewLines.size(), PREVIEW_MAX);
        List<List<String>> rows = new ArrayList<>(end);
        for (int i = 0; i < end; i++) {
            rows.add(RouteTextWrap.wrapLines(
                    this.previewLines.get(i).getString(), maxTextW, this.font::width,
                    PREVIEW_LINE_CAP));
        }
        return rows;
    }

    private int previewRowHeight(List<String> row) {
        return RouteTextWrap.rowHeight(row.size(), this.font.lineHeight, PREVIEW_ROW_PAD);
    }

    /**
     * Count the rows that fit in {@code availPx}. {@code reserveHint} keeps room for the
     * "N more line(s)" footer so it can never be drawn over the last step.
     */
    private int fittingPreviewRows(List<List<String>> rows, int availPx, boolean reserveHint) {
        int limit = availPx - (reserveHint ? PREVIEW_TEXT_INSET + this.font.lineHeight : 0);
        int used = 0;
        int count = 0;
        for (List<String> row : rows) {
            int h = previewRowHeight(row);
            if (row.isEmpty() || used + h > limit) {
                break;
            }
            used += h;
            count++;
        }
        return count;
    }

    private void renderPreview(GuiGraphics graphics) {
        if (this.previewLines.isEmpty()) {
            GuiShell.mutedCentered(graphics, this.font,
                    Component.translatable("packai.route_library.preview.empty"),
                    this.previewLeft + this.previewWidth / 2, (this.previewTop + this.previewBottom) / 2);
            return;
        }
        List<List<String>> rows = layoutPreviewRows();
        int avail = previewContentBottom() - this.previewTop;
        int drawn = fittingPreviewRows(rows, avail, false);
        if (this.previewLines.size() > drawn) {
            drawn = fittingPreviewRows(rows, avail, true);
        }
        int y = this.previewTop;
        for (int i = 0; i < drawn; i++) {
            List<String> lines = rows.get(i);
            for (int ln = 0; ln < lines.size(); ln++) {
                graphics.drawString(this.font, lines.get(ln), this.previewLeft + 4,
                        y + PREVIEW_TEXT_INSET + ln * this.font.lineHeight, GuiShell.TITLE, false);
            }
            y += previewRowHeight(lines);
        }
        this.previewHidden = 0;
        for (int i = drawn; i < rows.size(); i++) {
            // Count drawn lines, not rows: the footer says "N more line(s)".
            this.previewHidden += rows.get(i).size();
        }
        this.previewHidden += Math.max(0, this.previewLines.size() - rows.size());
        if (this.previewHidden > 0) {
            graphics.drawString(
                    this.font,
                    Component.translatable("packai.route_library.preview.more_lines", this.previewHidden),
                    this.previewLeft + 4,
                    y + PREVIEW_TEXT_INSET,
                    GuiShell.MUTED,
                    false);
        }
    }

    private List<Component> buildPreviewLines(SavedRoute route) {
        List<StepDto> steps = route.steps();
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<Component> out = new ArrayList<>();
        Set<String> seenItems = new HashSet<>();
        int n = steps.size();
        for (int rev = 0; rev < n; rev++) {
            int stepNo = rev + 1;
            StepDto step = steps.get(n - 1 - rev);
            if (step == null) {
                continue;
            }
            Item product = RouteBookmarkInject.itemFromId(step.output());
            String productName = itemDisplay(product);
            String machine = RouteMaterials.displayMachineLabel(step.actionLabel(), step.typeId());
            if (machine == null) {
                machine = "";
            }
            List<String> matNames = new ArrayList<>();
            boolean repeat = false;
            if (step.inputs() != null) {
                for (String in : step.inputs()) {
                    Item it = RouteBookmarkInject.itemFromId(in);
                    String dn = itemDisplay(it);
                    matNames.add(dn);
                    String uid = in == null ? "" : in;
                    if (!uid.isEmpty() && seenItems.contains(uid)) {
                        repeat = true;
                    }
                    if (!uid.isEmpty()) {
                        seenItems.add(uid);
                    }
                }
            }
            if (product != null && product != Items.AIR) {
                ResourceLocation pid = ForgeRegistries.ITEMS.getKey(product);
                if (pid != null) {
                    String uid = pid.toString();
                    if (seenItems.contains(uid)) {
                        repeat = true;
                    }
                    seenItems.add(uid);
                }
            }
            String mats = String.join(", ", matNames);
            Component base = Component.translatable(
                    "packai.route_library.preview.step",
                    stepNo,
                    machine,
                    productName,
                    mats);
            if (repeat) {
                base = Component.translatable(
                        "packai.route_library.preview.step_repeat",
                        stepNo,
                        machine,
                        productName,
                        mats,
                        Component.translatable("packai.route_library.preview.repeat_badge"));
            }
            out.add(base);
        }
        return List.copyOf(out);
    }

    private static String routeListTitle(SavedRoute route) {
        String name = displayName(route.itemId());
        if (route.truncated()) {
            name = name + Component.translatable("packai.routes.truncated_suffix").getString();
        }
        return name;
    }

    private static String displayName(String itemId) {
        Item item = RouteBookmarkInject.itemFromId(itemId);
        return itemDisplay(item == null ? Items.AIR : item);
    }

    private static String itemDisplay(Item item) {
        if (item == null || item == Items.AIR) {
            return Component.translatable("packai.route_library.item.unknown").getString();
        }
        return new ItemStack(item).getHoverName().getString();
    }

    private static String formatCreatedAt(String iso) {
        if (iso == null || iso.isBlank()) {
            return Component.translatable("packai.route_library.item.unknown").getString();
        }
        try {
            Instant inst = Instant.parse(iso);
            return LIST_TIME.format(inst.atZone(ZoneId.systemDefault()));
        } catch (DateTimeParseException e) {
            return iso.length() > 16 ? iso.substring(0, 16) : iso;
        }
    }

    private int visibleListRows() {
        return Math.max(1, (this.listBottom - this.listTop) / ROW_H);
    }

    private int maxListScroll() {
        return Math.max(0, this.routes.size() - visibleListRows());
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderScreen(new GuiGraphics(this.minecraft, this, pose), mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0
                && mouseX >= this.listLeft
                && mouseX <= this.listLeft + this.listWidth
                && mouseY >= this.listTop
                && mouseY < this.listBottom) {
            int row = this.listScroll + (int) ((mouseY - this.listTop) / ROW_H);
            if (row >= 0 && row < this.routes.size()) {
                long now = System.currentTimeMillis();
                if (row == this.lastListClickRow && now - this.lastListClickMs < 400L) {
                    selectIndex(row);
                    openTree();
                    this.lastListClickRow = -1;
                    return true;
                }
                this.lastListClickRow = row;
                this.lastListClickMs = now;
                selectIndex(row);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        int delta = (int) Math.signum(scrollDelta);
        if (mouseX >= this.listLeft - 4
                && mouseX <= this.listLeft + this.listWidth + 4
                && mouseY >= this.listTop
                && mouseY <= this.listBottom) {
            this.listScroll = Mth.clamp(this.listScroll - delta, 0, maxListScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (!this.routes.isEmpty()) {
                selectIndex(Math.max(0, this.selected < 0 ? 0 : this.selected - 1));
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (!this.routes.isEmpty()) {
                int start = this.selected < 0 ? 0 : this.selected + 1;
                selectIndex(Math.min(this.routes.size() - 1, start));
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

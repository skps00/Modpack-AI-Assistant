package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.emi.EmiFavoriteBridge;
import com.skps9.packai.client.jei.JeiBookmarkBridge;
import com.skps9.packai.client.jei.SuggestIcons;
import com.skps9.packai.client.routes.ItemManagerBridge;
import com.skps9.packai.client.routes.RouteStoreCodec.SavedRoute;
import com.skps9.packai.client.routes.RouteStoreCodec.StepDto;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.ObtainGraph;
import com.skps9.packai.logic.ObtainGraphRuntime;
import com.skps9.packai.logic.ReplyLang;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.TooltipAccessor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Crafting-tree view for a single saved obtain route.
 */
public class RouteTreeScreen extends Screen {
    private static final int NODE = 18;
    private static final int H_GAP_MAX = 10;
    private static final int H_GAP_MIN = 6;
    private static final int V_LAYER = 26;
    private static final int CONTENT_PAD = 8;
    private static final int TITLE_BAND = 22;
    private static final int VIEW_BAND = 38;
    private static final int VIEW_SEGMENT_H = 18;
    private static final int CARD_PAD = 12;
    private static final int CARD_ROW_H = 14;
    private static final int TABLE_ROW = 16;
    private static final int TABLE_HEADER = 10;
    private static final int TABLE_SECTION = 12;
    private static final int CHECKLIST_ROW = 22;
    private static final int CHECKLIST_MAT_LINE = 10;
    private static final int MISSING_WARN = 0xFFE0A54A;
    private static final int BOTTOM_BAND = 32;
    private final SavedRoute route;
    private final Screen parent;
    private final JeiBookmarkBridge jeiBridge = new JeiBookmarkBridge();
    private final EmiFavoriteBridge emiBridge = new EmiFavoriteBridge();

    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;
    private int contentLeft;
    private int contentTop;
    private int contentRight;
    private int contentBottom;

    private double scrollX;
    private double scrollY;
    private int layoutHGap = H_GAP_MAX;
    private int treeMinX;
    private int treeWidth;
    private int treeHeight;
    @Nullable
    private TreeNode root;
    @Nullable
    private TreeNode hovered;

    private enum ViewMode {
        TREE,
        CARDS,
        TABLE,
        CHECKLIST
    }

    private ViewMode viewMode = ViewMode.TREE;
    private String desiredViewName;
    private int listContentHeight;
    private int cardIndex;
    @Nullable
    private int[] cardReqRows;
    private List<CardData> cachedCards = List.of();
    private boolean metricsFromGraph;

    private Component injectStatus = Component.empty();
    private Button btnBack;
    @Nullable
    private Button btnJei;
    @Nullable
    private Button btnEmi;
    private Button btnClose;

    public RouteTreeScreen(SavedRoute route, Screen parent) {
        super(Component.translatable("packai.route_tree.title"));
        this.route = route;
        this.parent = parent;
        try {
            this.desiredViewName = PackAiConfig.routeTreeViewMode();
        } catch (RuntimeException ex) {
            this.desiredViewName = "tree";
        }
        if (this.desiredViewName == null || this.desiredViewName.isBlank()) {
            this.desiredViewName = "tree";
        }
    }

    /** Autotest / API: {@code tree}, {@code cards}, {@code table}, or {@code checklist} (case-insensitive). */
    public void setViewByName(String name) {
        setViewByName(name, false);
    }

    public void setViewByName(String name, boolean persist) {
        if (name == null) {
            return;
        }
        ViewMode mode = viewModeFromName(name);
        if (mode == null) {
            return;
        }
        this.desiredViewName = mode.name().toLowerCase(Locale.ROOT);
        setViewMode(mode, persist);
    }

    @Nullable
    private static ViewMode viewModeFromName(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "tree":
                return ViewMode.TREE;
            case "cards":
                return ViewMode.CARDS;
            case "table":
                return ViewMode.TABLE;
            case "checklist":
                return ViewMode.CHECKLIST;
            default:
                return null;
        }
    }

    @Override
    protected void init() {
        int pw = Math.min(this.width - 40, 420);
        int ph = this.height - 40;
        this.panelLeft = (this.width - pw) / 2;
        this.panelTop = 20;
        this.panelRight = this.panelLeft + pw;
        this.panelBottom = this.panelTop + ph;

        this.contentLeft = this.panelLeft + CONTENT_PAD;
        this.contentTop = this.panelTop + TITLE_BAND + VIEW_BAND;
        this.contentRight = this.panelRight - CONTENT_PAD;
        this.contentBottom = this.panelBottom - BOTTOM_BAND;

        ViewMode initMode = viewModeFromName(this.desiredViewName);
        if (initMode != null) {
            this.viewMode = initMode;
        }

        rebuildTreeLayout();
        rebuildListMetrics();

        int backX = this.panelRight - 24;
        int backY = this.panelTop + 6;
        this.btnBack = new RouteBackButton(backX, backY, 18, 18,
                Component.literal("\u2190"),
                b -> goBack(),
                Component.translatable("packai.route_tree.back"));
        this.addRenderableWidget(this.btnBack);

        int btnH = 20;
        int btnGap = 8;
        int innerW = this.panelRight - this.panelLeft - CONTENT_PAD * 2;
        int each = (innerW - btnGap * 2) / 3;
        boolean showJei = this.jeiBridge.capability() != ItemManagerBridge.Capability.NONE;
        boolean showEmi = this.emiBridge.capability() != ItemManagerBridge.Capability.NONE;
        int btnCount = (showJei ? 1 : 0) + (showEmi ? 1 : 0) + 1;
        int rowW = each * btnCount + btnGap * (btnCount - 1);
        int btnX = this.panelLeft + (this.panelRight - this.panelLeft - rowW) / 2;
        int btnY = this.panelBottom + 8;
        if (btnY + btnH > this.height - 4) {
            btnY = this.panelBottom - btnH - 8;
        }

        int slot = 0;
        this.btnJei = null;
        this.btnEmi = null;
        if (showJei) {
            int x = btnX + slot * (each + btnGap);
            this.btnJei = WidgetCompat.button(x, btnY, each, btnH,
                    Component.translatable("packai.route_library.button.add_jei"),
                    b -> injectJei(),
                    Component.translatable("packai.route_library.tooltip.add_jei"));
            this.addRenderableWidget(this.btnJei);
            slot++;
        }
        if (showEmi) {
            int x = btnX + slot * (each + btnGap);
            this.btnEmi = WidgetCompat.button(x, btnY, each, btnH,
                    Component.translatable("packai.route_library.button.add_emi"),
                    b -> injectEmi(),
                    Component.translatable("packai.route_library.tooltip.add_emi"));
            this.addRenderableWidget(this.btnEmi);
            slot++;
        }
        this.btnClose = WidgetCompat.button(btnX + slot * (each + btnGap), btnY, each, btnH,
                Component.translatable("gui.done"),
                b -> goBack(),
                Component.translatable("packai.route_library.tooltip.done"));
        this.addRenderableWidget(this.btnClose);
        updateButtonStates();
    }

    private void rebuildTreeLayout() {
        this.root = buildTree(this.route);
        this.layoutHGap = H_GAP_MAX;
        if (this.root != null) {
            layoutTree(this.root, this.layoutHGap);
            int innerW = this.contentRight - this.contentLeft;
            while (this.treeWidth > innerW && this.layoutHGap > H_GAP_MIN) {
                this.layoutHGap--;
                layoutTree(this.root, this.layoutHGap);
            }
        } else {
            this.treeMinX = 0;
            this.treeWidth = 0;
            this.treeHeight = 0;
        }
        clampScroll();
    }

    private void layoutTree(TreeNode node, int hGap) {
        assignPositions(node, 0, 0, hGap);
        Bounds bounds = treeBounds(node);
        this.treeMinX = bounds.minX;
        this.treeWidth = bounds.width;
        this.treeHeight = bounds.height;
    }

    private static final class Bounds {
        final int minX;
        final int width;
        final int height;

        Bounds(int minX, int width, int height) {
            this.minX = minX;
            this.width = width;
            this.height = height;
        }
    }

    private Bounds treeBounds(TreeNode node) {
        int minX = Integer.MAX_VALUE;
        int maxX = 0;
        int maxY = 0;
        for (TreeNode n : allNodes(node)) {
            minX = Math.min(minX, n.x);
            maxX = Math.max(maxX, n.x + NODE);
            maxY = Math.max(maxY, n.y + NODE);
        }
        if (minX == Integer.MAX_VALUE) {
            minX = 0;
        }
        return new Bounds(minX, maxX - minX, maxY);
    }

    private List<TreeNode> allNodes(TreeNode node) {
        List<TreeNode> out = new ArrayList<>();
        collectNodes(node, out);
        return out;
    }

    private void collectNodes(TreeNode node, List<TreeNode> out) {
        out.add(node);
        for (TreeNode c : node.children) {
            collectNodes(c, out);
        }
    }

    private int assignPositions(TreeNode node, int depth, int xCursor, int hGap) {
        node.depth = depth;
        node.y = depth * V_LAYER;
        if (node.children.isEmpty()) {
            node.x = xCursor;
            return xCursor + NODE + hGap;
        }
        int cx = xCursor;
        for (TreeNode child : node.children) {
            cx = assignPositions(child, depth + 1, cx, hGap);
        }
        TreeNode lastChild = node.children.get(node.children.size() - 1);
        int left = node.children.get(0).x;
        int right = lastChild.x + NODE;
        node.x = (left + right - NODE) / 2;
        return cx;
    }

    @Nullable
    private TreeNode buildTree(SavedRoute saved) {
        if (saved == null) {
            return null;
        }
        List<StepDto> steps = saved.steps();
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        int n = steps.size();
        StepDto firstLine = steps.get(n - 1);
        String rootId = firstLine != null && firstLine.output() != null && !firstLine.output().isBlank()
                ? firstLine.output()
                : saved.itemId();
        if (rootId == null || rootId.isBlank()) {
            return null;
        }
        return buildNode(rootId, 0, steps, new HashSet<>());
    }

    private TreeNode buildNode(String id, int depth, List<StepDto> steps, Set<String> onPath) {
        TreeNode node = new TreeNode(id, 1, depth);
        if (onPath.contains(id)) {
            return node;
        }
        int stepIdx = findStepForOutput(steps, id);
        if (stepIdx < 0) {
            return node;
        }
        node.stepIndex = stepIdx;
        StepDto step = steps.get(stepIdx);
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (step.inputs() != null) {
            for (String in : step.inputs()) {
                if (in != null && !in.isBlank()) {
                    counts.merge(in, 1, Integer::sum);
                }
            }
        }
        Set<String> nextPath = new HashSet<>(onPath);
        nextPath.add(id);
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            TreeNode child = buildNode(e.getKey(), depth + 1, steps, nextPath);
            child.count = e.getValue();
            node.children.add(child);
        }
        return node;
    }

    private static int findStepForOutput(List<StepDto> steps, String outputId) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            StepDto s = steps.get(i);
            if (s != null && outputId.equals(s.output())) {
                return i;
            }
        }
        return -1;
    }

    private void updateButtonStates() {
        boolean feature = PackAiConfig.routeMarks();
        if (this.btnJei != null) {
            this.btnJei.active = feature && PackAiConfig.routeMarksJei();
        }
        if (this.btnEmi != null) {
            this.btnEmi.active = feature && PackAiConfig.routeMarksEmi();
        }
    }

    private void injectJei() {
        this.injectStatus = RouteBookmarkInject.inject(this.route, this.jeiBridge, true);
    }

    private void injectEmi() {
        this.injectStatus = RouteBookmarkInject.inject(this.route, this.emiBridge, false);
    }

    private void goBack() {
        try {
            this.jeiBridge.clear();
            this.emiBridge.clear();
        } catch (RuntimeException ex) {
            PackAiMod.LOGGER.warn("Route tree bookmark clear on back failed", ex);
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        goBack();
    }

    private void rebuildListMetrics() {
        if (this.viewMode == ViewMode.TABLE) {
            List<RouteMaterials.Row> rows = RouteMaterials.compute(this.route, RouteMaterials.playerInventoryCounts());
            List<RouteMaterials.FluidRow> fluids = RouteMaterials.computeFluids(this.route);
            int h = TABLE_HEADER + TABLE_SECTION + rows.size() * TABLE_ROW;
            if (!fluids.isEmpty()) {
                h += TABLE_SECTION + fluids.size() * TABLE_ROW;
            }
            this.listContentHeight = h;
        } else if (this.viewMode == ViewMode.CHECKLIST) {
            int steps = this.route.steps() != null ? this.route.steps().size() : 0;
            this.listContentHeight = steps * (CHECKLIST_ROW + CHECKLIST_MAT_LINE);
        } else if (this.viewMode == ViewMode.CARDS) {
            rebuildCardCache();
            int n = this.cardReqRows != null ? this.cardReqRows.length : 0;
            this.listContentHeight = CardListLayout.contentHeight(n, this.cardReqRows);
        } else {
            this.listContentHeight = 0;
        }
    }

    private void clampScroll() {
        int innerW = this.contentRight - this.contentLeft;
        int innerH = this.contentBottom - this.contentTop;
        if (this.viewMode == ViewMode.TREE) {
            int maxScrollX = Math.max(0, this.treeWidth - innerW);
            int maxScrollY = Math.max(0, this.treeHeight - innerH);
            this.scrollX = Mth.clamp(this.scrollX, 0, maxScrollX);
            this.scrollY = Mth.clamp(this.scrollY, 0, maxScrollY);
        } else {
            this.scrollX = 0;
            if (this.viewMode == ViewMode.CARDS && this.cardReqRows != null && this.cardReqRows.length > 0) {
                int n = this.cardReqRows.length;
                int maxScrollY = CardListLayout.maxScroll(n, this.cardReqRows, innerH);
                int last = n - 1;
                this.cardIndex = Mth.clamp(this.cardIndex, 0, last);
                List<CardListLayout.Card> snapCards = CardListLayout.layout(n, this.cardReqRows, 0);
                int maxIdx = CardListLayout.maxIndexForScroll(snapCards, maxScrollY);
                if (this.cardIndex > maxIdx) {
                    this.cardIndex = maxIdx;
                }
                this.scrollY = CardListLayout.snapTop(snapCards, this.cardIndex);
                if (this.scrollY > maxScrollY) {
                    this.scrollY = maxScrollY;
                }
            } else {
                int maxScrollY = Math.max(0, this.listContentHeight - innerH);
                this.scrollY = Mth.clamp(this.scrollY, 0, maxScrollY);
            }
        }
    }

    private List<StepDto> orderedSteps() {
        List<StepDto> steps = this.route.steps();
        return steps != null ? steps : List.of();
    }

    private void rebuildCardCache() {
        List<StepDto> steps = orderedSteps();
        int n = steps.size();
        this.cardReqRows = new int[n];
        List<CardData> list = new ArrayList<>(n);
        String lang = ReplyLang.current();
        for (int i = 0; i < n; i++) {
            StepDto step = steps.get(i);
            ObtainGraph.Edge edge = findEdgeForStep(step);
            int reqRows = 0;
            List<CardReqLine> reqLines = new ArrayList<>();
            if (edge != null && edge.requirements() != null) {
                for (ObtainGraph.Requirement req : edge.requirements()) {
                    if (req == null || req.amount() <= 0) {
                        continue;
                    }
                    String line;
                    int lineColor;
                    if ("energy".equalsIgnoreCase(req.kind())) {
                        line = ReplyLang.tr(lang, "packai.obtain.req.energy", Integer.valueOf(req.amount()));
                        lineColor = GuiShell.ACCENT;
                    } else {
                        String id = req.id() == null || req.id().isBlank() ? req.kind() : req.id();
                        line = ReplyLang.tr(
                                lang,
                                "packai.obtain.req.extra",
                                Integer.valueOf(req.amount()),
                                id,
                                req.kind());
                        lineColor = GuiShell.MUTED;
                    }
                    if (line != null && !line.isEmpty()) {
                        reqLines.add(new CardReqLine(line, lineColor));
                        reqRows++;
                    }
                }
            }
            this.cardReqRows[i] = reqRows;
            String machineLabel = step != null
                    ? RouteMaterials.displayMachineLabel(step.actionLabel(), step.typeId())
                    : null;
            ResourceLocation typeRl = step != null && step.typeId() != null && !step.typeId().isBlank()
                    ? ResourceLocation.tryParse(step.typeId())
                    : null;
            ItemStack machine = MachineIcons.machineFor(typeRl);
            ItemStack product = step != null ? stackFor(step.output()) : ItemStack.EMPTY;
            int outputCount = edge != null && edge.outputCount() > 0 ? edge.outputCount() : 0;
            list.add(new CardData(step, edge, reqLines, machineLabel, machine, product, outputCount));
        }
        this.cachedCards = list;
    }

    private void maybeRefreshCardMetrics() {
        if (this.viewMode != ViewMode.CARDS) {
            return;
        }
        List<StepDto> steps = orderedSteps();
        if (steps.isEmpty()) {
            return;
        }
        ObtainGraph graph = ObtainGraphRuntime.INSTANCE.graph();
        boolean graphReady = graph != null;
        boolean need = this.cardReqRows == null;
        if (!need && graphReady && !this.metricsFromGraph) {
            need = true;
        }
        if (!need && graphReady && this.cardReqRows != null) {
            for (int i = 0; i < steps.size(); i++) {
                StepDto step = steps.get(i);
                int live = step != null ? countRequirementRows(step) : 0;
                if (i >= this.cardReqRows.length || live != this.cardReqRows[i]) {
                    need = true;
                    break;
                }
            }
        }
        if (need) {
            rebuildListMetrics();
            if (graphReady) {
                this.metricsFromGraph = true;
            }
            clampScroll();
        }
    }

    private int viewSwitcherBandWidth() {
        return this.contentRight - this.contentLeft;
    }

    private int viewSegmentW() {
        return Math.max(1, (viewSwitcherBandWidth() - 2) / 4);
    }

    private int viewSwitcherLeft() {
        return this.contentLeft;
    }

    private int viewSwitcherTop() {
        return this.panelTop + TITLE_BAND + 4;
    }

    @Nullable
    private ViewMode viewModeAt(double mouseX, double mouseY) {
        int x0 = viewSwitcherLeft();
        int y0 = viewSwitcherTop();
        int x1 = this.contentRight;
        if (mouseX < x0 || mouseX >= x1
                || mouseY < y0 || mouseY >= y0 + VIEW_SEGMENT_H) {
            return null;
        }
        int segW = Math.max(1, (this.contentRight - this.contentLeft - 2) / 4);
        int rel = (int) mouseX - x0 - 1;
        int idx = Mth.clamp(rel / segW, 0, 3);
        return ViewMode.values()[idx];
    }

    private void setViewMode(ViewMode mode, boolean persist) {
        if (this.viewMode == mode) {
            return;
        }
        this.viewMode = mode;
        this.desiredViewName = mode.name().toLowerCase(Locale.ROOT);
        this.scrollY = 0;
        this.scrollX = 0;
        this.cardIndex = 0;
        this.metricsFromGraph = false;
        rebuildListMetrics();
        clampScroll();
        if (persist) {
            PackAiConfig.setRouteTreeViewMode(mode.name().toLowerCase(Locale.ROOT));
        }
    }

    private void renderViewSwitcher(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = viewSwitcherLeft();
        int y0 = viewSwitcherTop();
        int x1 = this.contentRight;
        int y1 = y0 + VIEW_SEGMENT_H;
        int segW = viewSegmentW();
        g.fill(x0, y0, x1, y1, GuiShell.FILL_SECONDARY);
        g.fill(x0, y0, x1, y0 + 1, GuiShell.BORDER);
        g.fill(x0, y1 - 1, x1, y1, GuiShell.BORDER);
        g.fill(x0, y0, x0 + 1, y1, GuiShell.BORDER);
        g.fill(x1 - 1, y0, x1, y1, GuiShell.BORDER);
        Component[] labels = {
                Component.translatable("packai.route_view.tree"),
                Component.translatable("packai.route_view.cards"),
                Component.translatable("packai.route_view.table"),
                Component.translatable("packai.route_view.checklist")
        };
        for (int i = 0; i < 4; i++) {
            int sx = x0 + 1 + i * segW;
            int ex = i == 3 ? x1 - 1 : sx + segW;
            boolean sel = ViewMode.values()[i] == this.viewMode;
            g.fill(sx, y0 + 1, ex, y1 - 1, sel ? GuiShell.ACCENT_DIM : GuiShell.FILL_PRIMARY);
            if (i > 0) {
                g.fill(sx, y0 + 1, sx + 1, y1 - 1, GuiShell.HAIRLINE);
            }
            int textColor = sel ? GuiShell.TITLE : GuiShell.MUTED;
            int ty = y0 + (VIEW_SEGMENT_H - this.font.lineHeight) / 2;
            drawFitted(g, labels[i].getString(), sx + 4, ty, ex - 4, textColor);
        }
    }

    private int treeOriginX() {
        int innerW = this.contentRight - this.contentLeft;
        int ox = this.contentLeft + Math.max(0, (innerW - this.treeWidth) / 2) - this.treeMinX;
        return ox - (int) this.scrollX;
    }

    private int treeOriginY() {
        return this.contentTop - (int) this.scrollY;
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        GuiGraphics graphics = new GuiGraphics(this.minecraft, this, pose);
        graphics.fill(0, 0, this.width, this.height, 0x99000000);
        GuiShell.panel(graphics, this.panelLeft, this.panelTop, this.panelRight, this.panelBottom,
                GuiShell.FILL_BODY, GuiShell.BORDER);

        String title = displayName(this.route.itemId());
        graphics.drawString(this.font, title, this.panelLeft + CONTENT_PAD, this.panelTop + 7, GuiShell.TITLE, false);
        renderViewSwitcher(graphics, mouseX, mouseY);

        if (!this.injectStatus.getString().isEmpty()) {
            String st = this.injectStatus.getString();
            int max = this.panelRight - this.panelLeft - 80;
            if (this.font.width(st) > max) {
                st = this.font.plainSubstrByWidth(st, max - this.font.width("...")) + "...";
            }
            graphics.drawString(this.font, st, this.panelLeft + CONTENT_PAD, this.panelBottom - 18, GuiShell.MUTED, false);
        }

        this.hovered = null;
        boolean empty = this.route.steps() == null || this.route.steps().isEmpty();
        if (empty) {
            GuiShell.mutedCentered(graphics, this.font,
                    Component.translatable("packai.route_tree.empty"),
                    (this.panelLeft + this.panelRight) / 2,
                    (this.contentTop + this.contentBottom) / 2);
        } else {
            maybeRefreshCardMetrics();
            graphics.enableScissor(this.contentLeft, this.contentTop, this.contentRight, this.contentBottom);
            switch (this.viewMode) {
                case TREE -> {
                    if (this.root != null) {
                        int ox = treeOriginX();
                        int oy = treeOriginY();
                        drawLinks(graphics, this.root, ox, oy);
                        drawNodes(graphics, this.root, ox, oy, mouseX, mouseY);
                    }
                }
                case CARDS -> renderCards(graphics);
                case TABLE -> renderMaterialsTable(graphics, mouseX, mouseY);
                case CHECKLIST -> renderChecklist(graphics, mouseX, mouseY);
                default -> {}
            }
            graphics.disableScissor();
            if (this.hovered != null) {
                renderNodeTooltip(graphics, this.hovered, mouseX, mouseY);
            }
        }

        super.render(pose, mouseX, mouseY, partialTick);
        WidgetCompat.renderHoveredTips(this, pose, mouseX, mouseY);
    }

    private void drawLinks(GuiGraphics g, TreeNode node, int ox, int oy) {
        int px = ox + node.x + NODE / 2;
        int py = oy + node.y + NODE;
        for (TreeNode child : node.children) {
            int cx = ox + child.x + NODE / 2;
            int cy = oy + child.y;
            int midY = py + 8;
            vLine(g, px, py, midY);
            if (px != cx) {
                int left = Math.min(px, cx);
                int right = Math.max(px, cx);
                hLine(g, left, right, midY);
            }
            vLine(g, cx, midY, cy);
            drawLinks(g, child, ox, oy);
        }
    }

    private static void vLine(GuiGraphics g, int x, int y0, int y1) {
        if (y1 < y0) {
            int t = y0;
            y0 = y1;
            y1 = t;
        }
        g.fill(x, y0, x + 1, y1, GuiShell.BORDER);
    }

    private static void hLine(GuiGraphics g, int x0, int x1, int y) {
        g.fill(x0, y, x1 + 1, y + 1, GuiShell.BORDER);
    }

    private void drawNodes(GuiGraphics g, TreeNode node, int ox, int oy, int mouseX, int mouseY) {
        int x = ox + node.x;
        int y = oy + node.y;
        boolean hover = mouseX >= x && mouseX < x + NODE && mouseY >= y && mouseY < y + NODE;
        if (hover) {
            this.hovered = node;
        }
        int border = hover ? GuiShell.ACCENT : GuiShell.BORDER_SOFT;
        g.fill(x, y, x + NODE, y + NODE, GuiShell.FILL_PRIMARY);
        g.fill(x, y, x + NODE, y + 1, border);
        g.fill(x, y + NODE - 1, x + NODE, y + NODE, border);
        g.fill(x, y, x + 1, y + NODE, border);
        g.fill(x + NODE - 1, y, x + NODE, y + NODE, border);

        ItemStack stack = stackFor(node.id);
        if (!stack.isEmpty()) {
            g.renderItem(stack, x + 1, y + 1);
        }
        if (node.count > 1) {
            String count = String.valueOf(node.count);
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.scale(0.5f, 0.5f, 1f);
            int tx = (x + NODE - 3) * 2;
            int ty = (y + NODE - 5) * 2;
            g.drawString(this.font, count, tx + 1, ty + 1, 0xFF000000, false);
            g.drawString(this.font, count, tx, ty, GuiShell.TITLE, false);
            pose.popPose();
        }
        for (TreeNode child : node.children) {
            drawNodes(g, child, ox, oy, mouseX, mouseY);
        }
    }

    private void renderNodeTooltip(GuiGraphics g, TreeNode node, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(displayName(node.id)));
        if (node.stepIndex >= 0 && this.route.steps() != null) {
            int n = this.route.steps().size();
            int stepNo = n - node.stepIndex;
            lines.add(Component.translatable("packai.route_tree.step", stepNo));
            StepDto step = this.route.steps().get(node.stepIndex);
            if (step != null) {
                String machine = RouteMaterials.displayMachineLabel(step.actionLabel(), step.typeId());
                if (machine != null) {
                    lines.add(Component.literal(machine));
                }
                if (step.fluids() != null) {
                    for (var f : step.fluids()) {
                        if (f == null || f.fluidId() == null || f.fluidId().isBlank() || f.amountMb() <= 0) {
                            continue;
                        }
                        String fname = RouteMaterials.displayFluidName(f.fluidId());
                        lines.add(Component.translatable("packai.route_tree.fluid_line", fname, f.amountMb()));
                    }
                }
            }
        }
        if (node.count > 1) {
            lines.add(Component.translatable("packai.route_tree.count", node.count));
        }
        g.renderTooltip(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }

    private static ItemStack stackFor(String id) {
        Item item = RouteBookmarkInject.itemFromId(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return SuggestIcons.resolveRef(id);
    }

    private static String displayName(String itemId) {
        ItemStack stack = stackFor(itemId);
        return SuggestIcons.labelFor(itemId, stack);
    }

    private void renderMaterialsTable(GuiGraphics g, int mouseX, int mouseY) {
        List<RouteMaterials.Row> rows = RouteMaterials.compute(this.route, RouteMaterials.playerInventoryCounts());
        List<RouteMaterials.FluidRow> fluidRows = RouteMaterials.computeFluids(this.route);
        int y = this.contentTop - (int) this.scrollY;
        int right = this.contentRight;
        int colMiss = right - 4;
        int colHave = colMiss - 28;
        int colNeed = colHave - 28;
        int nameRight = colNeed - 6;
        int colFluidMb = colMiss;

        drawTableHeader(g, y, colNeed, colHave, colMiss);
        y += TABLE_HEADER;

        y = drawTableSectionLabel(g, y, Component.translatable("packai.route_table.section.items"));

        for (int i = 0; i < rows.size(); i++) {
            RouteMaterials.Row row = rows.get(i);
            y = drawItemTableRow(g, mouseX, mouseY, y, row, nameRight, colNeed, colHave, colMiss);
        }

        if (!fluidRows.isEmpty()) {
            y = drawTableSectionLabel(g, y, Component.translatable("packai.route_table.section.fluids"));
            for (RouteMaterials.FluidRow row : fluidRows) {
                int rowY0 = y;
                int rowY1 = y + TABLE_ROW;
                boolean hover = mouseX >= this.contentLeft && mouseX < this.contentRight
                        && mouseY >= rowY0 && mouseY < rowY1
                        && rowY1 > this.contentTop && rowY0 < this.contentBottom;
                if (hover) {
                    g.fill(this.contentLeft, Math.max(rowY0, this.contentTop), this.contentRight,
                            Math.min(rowY1, this.contentBottom), GuiShell.FILL_PRIMARY);
                }
                ItemStack bucket = RouteMaterials.bucketStackForFluidId(row.fluidId());
                if (!bucket.isEmpty()) {
                    g.renderItem(bucket, this.contentLeft, rowY0);
                }
                String name = RouteMaterials.displayFluidName(row.fluidId());
                int maxName = nameRight - this.contentLeft - 18;
                if (maxName > 8 && this.font.width(name) > maxName) {
                    name = this.font.plainSubstrByWidth(name, maxName - this.font.width("...")) + "...";
                }
                g.drawString(this.font, name, this.contentLeft + 18, rowY0 + 4, GuiShell.TITLE, false);
                String mb = row.totalMb() + " mB";
                drawRightNumber(g, mb, colFluidMb, rowY0 + 4, GuiShell.MUTED);
                g.fill(this.contentLeft, rowY1 - 1, this.contentRight, rowY1, GuiShell.HAIRLINE);
                y = rowY1;
            }
        }
    }

    private int drawTableSectionLabel(GuiGraphics g, int y, Component label) {
        g.drawString(this.font, label, this.contentLeft + 2, y + 2, GuiShell.MUTED, false);
        int y1 = y + TABLE_SECTION;
        g.fill(this.contentLeft, y1 - 1, this.contentRight, y1, GuiShell.HAIRLINE);
        return y1;
    }

    private int drawItemTableRow(
            GuiGraphics g,
            int mouseX,
            int mouseY,
            int y,
            RouteMaterials.Row row,
            int nameRight,
            int colNeed,
            int colHave,
            int colMiss) {
        int rowY0 = y;
        int rowY1 = y + TABLE_ROW;
        boolean hover = mouseX >= this.contentLeft && mouseX < this.contentRight
                && mouseY >= rowY0 && mouseY < rowY1
                && rowY1 > this.contentTop && rowY0 < this.contentBottom;
        if (hover) {
            g.fill(this.contentLeft, Math.max(rowY0, this.contentTop), this.contentRight,
                    Math.min(rowY1, this.contentBottom), GuiShell.FILL_PRIMARY);
        }
        ItemStack stack = stackFor(row.id());
        if (!stack.isEmpty()) {
            g.renderItem(stack, this.contentLeft, rowY0);
        }
        String name = displayName(row.id());
        int maxName = nameRight - this.contentLeft - 18;
        if (maxName > 8 && this.font.width(name) > maxName) {
            name = this.font.plainSubstrByWidth(name, maxName - this.font.width("...")) + "...";
        }
        g.drawString(this.font, name, this.contentLeft + 18, rowY0 + 4, GuiShell.TITLE, false);

        int missColor = row.missing() > 0 ? MISSING_WARN : GuiShell.MUTED;
        drawRightNumber(g, String.valueOf(row.total()), colNeed, rowY0 + 4, GuiShell.MUTED);
        drawRightNumber(g, String.valueOf(row.have()), colHave, rowY0 + 4, GuiShell.MUTED);
        drawRightNumber(g, String.valueOf(row.missing()), colMiss, rowY0 + 4, missColor);

        g.fill(this.contentLeft, rowY1 - 1, this.contentRight, rowY1, GuiShell.HAIRLINE);
        return rowY1;
    }

    private void drawTableHeader(GuiGraphics g, int y, int colNeed, int colHave, int colMiss) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.scale(0.5f, 0.5f, 1f);
        int hy = (y + 1) * 2;
        drawHeaderLabel(g, Component.translatable("packai.route_view.col_need"), colNeed, hy);
        drawHeaderLabel(g, Component.translatable("packai.route_view.col_have"), colHave, hy);
        drawHeaderLabel(g, Component.translatable("packai.route_view.col_miss"), colMiss, hy);
        pose.popPose();
        g.fill(this.contentLeft, y + TABLE_HEADER - 1, this.contentRight, y + TABLE_HEADER, GuiShell.HAIRLINE);
    }

    private void drawHeaderLabel(GuiGraphics g, Component text, int colRight, int hy) {
        String s = text.getString();
        int tw = this.font.width(s);
        g.drawString(this.font, s, colRight * 2 - tw, hy, GuiShell.MUTED, false);
    }

    private void drawRightNumber(GuiGraphics g, String num, int colRight, int y, int color) {
        int tw = this.font.width(num);
        g.drawString(this.font, num, colRight - tw, y, color, false);
    }

    private void renderChecklist(GuiGraphics g, int mouseX, int mouseY) {
        List<StepDto> steps = this.route.steps();
        if (steps == null) {
            return;
        }
        boolean routeDone = this.route.complete();
        int n = steps.size();
        int y = this.contentTop - (int) this.scrollY;
        for (int stepNo = 1; stepNo <= n; stepNo++) {
            StepDto step = steps.get(n - stepNo);
            if (step == null) {
                continue;
            }
            int blockH = CHECKLIST_ROW + CHECKLIST_MAT_LINE;
            int rowY0 = y;
            int rowY1 = y + blockH;
            boolean hover = mouseX >= this.contentLeft && mouseX < this.contentRight
                    && mouseY >= rowY0 && mouseY < rowY1
                    && rowY1 > this.contentTop && rowY0 < this.contentBottom;
            if (hover) {
                g.fill(this.contentLeft, Math.max(rowY0, this.contentTop), this.contentRight,
                        Math.min(rowY1, this.contentBottom), GuiShell.FILL_PRIMARY);
            }
            int textColor = routeDone ? GuiShell.MUTED : GuiShell.TITLE;
            String machine = RouteMaterials.displayMachineLabel(step.actionLabel(), step.typeId());
            String product = displayName(step.output());
            String head = machine != null
                    ? "#" + stepNo + "  [" + machine + "]  " + product + " ×1"
                    : "#" + stepNo + "  " + product + " ×1";
            int maxW = this.contentRight - this.contentLeft - 4;
            if (this.font.width(head) > maxW) {
                head = this.font.plainSubstrByWidth(head, maxW - this.font.width("...")) + "...";
            }
            g.drawString(this.font, head, this.contentLeft + 2, rowY0 + 6, textColor, false);

            String mats = formatStepMaterials(step);
            int matColor = textColor;
            int matX = this.contentLeft + 12;
            int matMax = this.contentRight - matX - 2;
            if (matMax > 8 && this.font.width(mats) > matMax) {
                mats = this.font.plainSubstrByWidth(mats, matMax - this.font.width("...")) + "...";
            }
            g.drawString(this.font, mats, matX, rowY0 + CHECKLIST_ROW, matColor, false);

            g.fill(this.contentLeft, rowY1 - 1, this.contentRight, rowY1, GuiShell.HAIRLINE);
            y = rowY1;
        }
    }

    private int drawFitted(GuiGraphics g, String text, int x, int y, int limit, int color) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int maxW = limit - x;
        if (maxW <= 0) {
            return 0;
        }
        if (this.font.width(text) <= maxW) {
            g.drawString(this.font, text, x, y, color, false);
            return this.font.width(text);
        }
        String ellipsis = "...";
        int ellW = this.font.width(ellipsis);
        String base = text;
        while (base.length() > 0 && this.font.width(base) + ellW > maxW) {
            base = base.substring(0, base.length() - 1);
        }
        String shown = base.isEmpty() ? ellipsis : base + ellipsis;
        g.drawString(this.font, shown, x, y, color, false);
        return this.font.width(shown);
    }

    private int cardWidth() {
        int panelInner = this.contentRight - this.contentLeft;
        int w = Math.max(240, Math.min(panelInner - 8, 604));
        int maxW = this.contentRight - cardX0() - 4;
        return Math.max(120, Math.min(w, maxW));
    }

    private int cardX0() {
        return this.contentLeft + 4;
    }

    @Nullable
    private ObtainGraph.Edge findEdgeForStep(StepDto step) {
        if (step == null) {
            return null;
        }
        Item out = RouteBookmarkInject.itemFromId(step.output());
        if (out == null || out == Items.AIR) {
            return null;
        }
        ObtainGraph graph = ObtainGraphRuntime.INSTANCE.graph();
        if (graph == null) {
            return null;
        }
        List<ObtainGraph.Edge> edges = graph.edgesFor(out);
        if (edges.isEmpty()) {
            return null;
        }
        ResourceLocation rid = step.recipeId() != null && !step.recipeId().isBlank()
                ? ResourceLocation.tryParse(step.recipeId())
                : null;
        if (rid != null) {
            for (ObtainGraph.Edge edge : edges) {
                if (edge.recipeId() != null && edge.recipeId().equals(rid)) {
                    return edge;
                }
            }
            return null;
        }
        if (edges.size() == 1) {
            return edges.get(0);
        }
        return null;
    }

    private int countRequirementRows(StepDto step) {
        ObtainGraph.Edge edge = findEdgeForStep(step);
        if (edge == null || edge.requirements() == null) {
            return 0;
        }
        int n = 0;
        for (ObtainGraph.Requirement req : edge.requirements()) {
            if (req != null && req.amount() > 0) {
                n++;
            }
        }
        return n;
    }

    private void renderCards(GuiGraphics g) {
        if (this.cachedCards.isEmpty() || this.cardReqRows == null) {
            return;
        }
        int cardW = cardWidth();
        int cardX0 = cardX0();
        int cardX1 = cardX0 + cardW;
        int iconX = cardX0 + CARD_PAD;
        int leftX = iconX + 24;
        int rightX1 = Math.min(cardX1 - CARD_PAD, this.contentRight - 4);
        int scrollY = (int) this.scrollY;
        List<CardListLayout.Card> layout = CardListLayout.layout(this.cardReqRows.length, this.cardReqRows, scrollY);
        List<ItemStack> deferredStacks = new ArrayList<>();
        List<int[]> deferredPos = new ArrayList<>();
        for (int i = 0; i < layout.size() && i < this.cachedCards.size(); i++) {
            CardListLayout.Card slot = layout.get(i);
            CardData data = this.cachedCards.get(i);
            StepDto step = data.step;
            if (step == null) {
                continue;
            }
            int y = this.contentTop + slot.top();
            int cardH = slot.height();
            int cardY0 = y;
            int cardY1 = y + cardH;
            g.fill(cardX0, cardY0, cardX1, cardY1, GuiShell.FILL_PRIMARY);
            g.fill(cardX0, cardY0, cardX1, cardY0 + 1, GuiShell.BORDER_SOFT);
            g.fill(cardX0, cardY1 - 1, cardX1, cardY1, GuiShell.BORDER_SOFT);
            g.fill(cardX0, cardY0, cardX0 + 1, cardY1, GuiShell.BORDER_SOFT);
            g.fill(cardX1 - 1, cardY0, cardX1, cardY1, GuiShell.BORDER_SOFT);

            if (!data.productStack.isEmpty()) {
                deferredStacks.add(data.productStack);
                deferredPos.add(new int[] {iconX, y + 12});
            }
            drawFitted(g, displayName(step.output()), leftX, y + 10, rightX1, GuiShell.TITLE);
            if (data.outputCount > 0) {
                drawFitted(g, "\u00d7" + data.outputCount, leftX, y + 24, rightX1, GuiShell.MUTED);
            }

            if (data.machineStack != null && !data.machineStack.isEmpty()) {
                deferredStacks.add(data.machineStack);
                deferredPos.add(new int[] {iconX, y + 38});
            }
            if (data.machineLabel != null) {
                drawFitted(g, data.machineLabel, leftX, y + 38, rightX1, GuiShell.MUTED);
            }

            int row = 0;
            for (CardReqLine reqLine : data.requirementLines) {
                drawFitted(g, reqLine.text, leftX, y + 56 + CARD_ROW_H * row, rightX1, reqLine.color);
                row++;
            }
        }

        for (int i = 0; i < deferredStacks.size(); i++) {
            ItemStack one = deferredStacks.get(i).copy();
            one.setCount(1);
            int[] pos = deferredPos.get(i);
            g.renderItem(one, pos[0], pos[1]);
        }
        RenderSystem.disableDepthTest();
    }

    private static String formatStepMaterials(StepDto step) {
        if (step == null) {
            return "";
        }
        boolean hasFluids = step.fluids() != null && !step.fluids().isEmpty();
        if ((step.inputs() == null || step.inputs().isEmpty()) && !hasFluids) {
            return "";
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (step.inputs() != null) {
        for (String in : step.inputs()) {
            if (in != null && !in.isBlank()) {
                counts.merge(in, 1, Integer::sum);
            }
        }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String name = displayName(e.getKey());
            sb.append(" · ").append(name).append(" ×").append(e.getValue());
        }
        if (step.fluids() != null) {
            for (var f : step.fluids()) {
                if (f == null || f.fluidId() == null || f.fluidId().isBlank() || f.amountMb() <= 0) {
                    continue;
                }
                sb.append(" · ")
                        .append(RouteMaterials.displayFluidName(f.fluidId()))
                        .append(" ")
                        .append(f.amountMb())
                        .append(" mB");
            }
        }
        return sb.toString().trim();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ViewMode hit = viewModeAt(mouseX, mouseY);
        if (hit != null && button == 0) {
            setViewMode(hit, true);
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (mouseX >= this.contentLeft
                && mouseX <= this.contentRight
                && mouseY >= this.contentTop
                && mouseY <= this.contentBottom) {
            int innerW = this.contentRight - this.contentLeft;
            int innerH = this.contentBottom - this.contentTop;
            if (this.viewMode == ViewMode.TREE) {
                if (Screen.hasShiftDown() && this.treeWidth > innerW) {
                    this.scrollX = Mth.clamp(this.scrollX - scrollDelta * 16, 0,
                            Math.max(0, this.treeWidth - innerW));
                } else {
                    this.scrollY = Mth.clamp(this.scrollY - scrollDelta * 16, 0,
                            Math.max(0, this.treeHeight - innerH));
                }
            } else if (this.viewMode == ViewMode.CARDS && this.cardReqRows != null && this.cardReqRows.length > 0) {
                int dir = scrollDelta < 0 ? 1 : (scrollDelta > 0 ? -1 : 0);
                int n = this.cardReqRows.length;
                this.cardIndex = CardListLayout.nextIndex(n, this.cardIndex, dir);
                List<CardListLayout.Card> snapCards = CardListLayout.layout(n, this.cardReqRows, 0);
                int maxScrollY = CardListLayout.maxScroll(n, this.cardReqRows, innerH);
                int maxIdx = CardListLayout.maxIndexForScroll(snapCards, maxScrollY);
                if (this.cardIndex > maxIdx) {
                    this.cardIndex = maxIdx;
                }
                this.scrollY = CardListLayout.snapTop(snapCards, this.cardIndex);
                if (this.scrollY > maxScrollY) {
                    this.scrollY = maxScrollY;
                }
            } else {
                rebuildListMetrics();
                int maxScrollY = Math.max(0, this.listContentHeight - innerH);
                this.scrollY = Mth.clamp(this.scrollY - scrollDelta * 16, 0, maxScrollY);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class RouteBackButton extends Button implements TooltipAccessor {
        private final Component tip;

        RouteBackButton(int x, int y, int w, int h, Component label, OnPress onPress, Component tip) {
            super(x, y, w, h, label, onPress);
            this.tip = tip;
        }

        @Override
        public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTick) {
            boolean hovered = this.isHoveredOrFocused();
            int border = hovered ? GuiShell.ACCENT : GuiShell.BORDER;
            net.minecraft.client.gui.GuiComponent.fill(pose, this.x, this.y, this.x + this.width, this.y + this.height,
                    GuiShell.FILL_SECONDARY);
            net.minecraft.client.gui.GuiComponent.fill(pose, this.x, this.y, this.x + this.width, this.y + 1, border);
            net.minecraft.client.gui.GuiComponent.fill(pose, this.x, this.y + this.height - 1, this.x + this.width,
                    this.y + this.height, border);
            net.minecraft.client.gui.GuiComponent.fill(pose, this.x, this.y, this.x + 1, this.y + this.height, border);
            net.minecraft.client.gui.GuiComponent.fill(pose, this.x + this.width - 1, this.y, this.x + this.width,
                    this.y + this.height, border);
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            int ty = this.y + (this.height - font.lineHeight) / 2;
            net.minecraft.client.gui.GuiComponent.drawCenteredString(
                    pose, font, this.getMessage(), this.x + this.width / 2, ty, GuiShell.TITLE);
        }

        @Override
        public List<FormattedCharSequence> getTooltip() {
            return WidgetCompat.tipLines(this.tip);
        }
    }

    private static final class CardReqLine {
        final String text;
        final int color;

        CardReqLine(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    private static final class CardData {
        @Nullable
        final StepDto step;
        @Nullable
        final ObtainGraph.Edge edge;
        final List<CardReqLine> requirementLines;
        @Nullable
        final String machineLabel;
        @Nullable
        final ItemStack machineStack;
        final ItemStack productStack;
        final int outputCount;

        CardData(
                @Nullable StepDto step,
                @Nullable ObtainGraph.Edge edge,
                List<CardReqLine> requirementLines,
                @Nullable String machineLabel,
                @Nullable ItemStack machineStack,
                ItemStack productStack,
                int outputCount) {
            this.step = step;
            this.edge = edge;
            this.requirementLines = requirementLines;
            this.machineLabel = machineLabel;
            this.machineStack = machineStack;
            this.productStack = productStack;
            this.outputCount = outputCount;
        }
    }

    private static final class TreeNode {
        final String id;
        int count;
        int depth;
        int x;
        int y;
        int stepIndex = -1;
        final List<TreeNode> children = new ArrayList<>();

        TreeNode(String id, int count, int depth) {
            this.id = id;
            this.count = count;
            this.depth = depth;
        }
    }
}

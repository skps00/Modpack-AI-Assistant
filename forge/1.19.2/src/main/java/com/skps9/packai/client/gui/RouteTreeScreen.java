package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import com.skps9.packai.logic.RouteSummary;
import com.skps9.packai.logic.RouteTreeBuilder;
import com.skps9.packai.logic.RouteTreeData;
import com.skps9.packai.client.routes.RouteStoreCodec.FluidDto;
import com.skps9.packai.client.routes.RouteStoreCodec.FluidSourceDto;
import com.skps9.packai.client.routes.RouteStoreCodec.InputDto;

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

    private double scrollY;
    private List<TreeCardData> treeCards = List.of();
    private List<TreeCardLayout.Rect> treeRects = List.of();
    private int treeContentHeight;
    private int treeMaxScrollY;
    private int pendingAutotestScrollY = -1;
    /** Autotest only: pretend the tree was capped so the limit banner can be screenshotted. */
    private static boolean autotestForceTruncated;
    private long lastGraphGen;
    private boolean lastGraphReady;
    private boolean treeDirty = true;

    private enum ViewMode {
        TREE,
        CARDS,
        TABLE,
        CHECKLIST,
        SUMMARY
    }

    private ViewMode viewMode = ViewMode.TREE;
    private RouteSummary.Data cachedSummary =
            new RouteSummary.Data(List.of(), List.of(), new RouteSummary.Totals(0, 0, 0), true);
    private boolean summaryDirty = true;
    private double summaryScrollY;
    private int summaryMaxScrollY;
    private boolean summaryDrawerOpen = true;
    private boolean summaryBarExpanded;
    private int summaryDrawerBtnX;
    private int summaryDrawerBtnY;
    private int summaryDrawerBtnW;
    private int summaryDrawerBtnH;
    private int summaryPanelX;
    private int summaryPanelY;
    private int summaryPanelW;
    private int summaryPanelH;
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
            case "summary":
                return ViewMode.SUMMARY;
            default:
                return null;
        }
    }

    private static String routeSummaryLayout() {
        try {
            String m = PackAiConfig.routeSummaryMode();
            if (m != null && !m.isBlank()) {
                return m.toLowerCase(Locale.ROOT);
            }
        } catch (RuntimeException ignored) {
        }
        return "tab";
    }

    private boolean summaryTabVisible() {
        return "tab".equals(routeSummaryLayout());
    }

    private int viewModeCount() {
        return summaryTabVisible() ? 5 : 4;
    }

    private void markSummaryDirty() {
        this.summaryDirty = true;
    }

    private void ensureSummaryData() {
        if (!this.summaryDirty) {
            return;
        }
        this.cachedSummary = RouteSummary.of(orderedSteps());
        this.summaryDirty = false;
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

        markTreeDirty();
        markSummaryDirty();
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

    private void markTreeDirty() {
        this.treeDirty = true;
    }

    private void maybeRefreshTreeCards() {
        if (this.viewMode != ViewMode.TREE) {
            return;
        }
        ObtainGraphRuntime runtime = ObtainGraphRuntime.INSTANCE;
        boolean ready = runtime.ready();
        long gen = runtime.generation();
        boolean need = this.treeDirty;
        if (!need && ready && (!this.lastGraphReady || gen != this.lastGraphGen)) {
            need = true;
        }
        if (need) {
            rebuildTreeCards();
        }
    }

    private void rebuildTreeCards() {
        int innerW = this.contentRight - this.contentLeft;
        int innerH = this.contentBottom - this.contentTop;
        RouteTreeData.Tree tree = RouteTreeBuilder.build(this.route.itemId(), this.route.steps());
        if (autotestForceTruncated && !tree.truncated()) {
            // Autotest only: keep the root so the limit banner lands on the first screen.
            List<RouteTreeData.Node> rootOnly = tree.nodes().isEmpty()
                    ? tree.nodes()
                    : List.of(tree.nodes().get(0));
            tree = new RouteTreeData.Tree(rootOnly, true, 1);
        }
        List<StepDto> steps = orderedSteps();
        List<TreeCardLayout.Card> layoutCards = new ArrayList<>();
        List<TreeCardData> data = new ArrayList<>();
        for (int i = 0; i < tree.nodes().size(); i++) {
            RouteTreeData.Node node = tree.nodes().get(i);
            StepDto step = node.stepIndex() >= 0 && node.stepIndex() < steps.size()
                    ? steps.get(node.stepIndex())
                    : null;
            List<CardReqLine> reqLines = step != null ? requirementLinesForStep(step) : List.of();
            List<String> fluidLines = step != null ? fluidLinesForStep(step) : List.of();
            int statusRows = node.status() != RouteTreeData.Status.OK ? 1 : 0;
            int rows = reqLines.size() + fluidLines.size() + statusRows;
            boolean hasMachine = false;
            ItemStack machineStack = ItemStack.EMPTY;
            String machineLabel = null;
            if (node.status() == RouteTreeData.Status.OK && step != null) {
                ResourceLocation typeRl = step.typeId() != null && !step.typeId().isBlank()
                        ? ResourceLocation.tryParse(step.typeId())
                        : null;
                ItemStack machine = MachineIcons.machineFor(typeRl);
                machineStack = machine != null ? machine : ItemStack.EMPTY;
                hasMachine = !machineStack.isEmpty();
                machineLabel = RouteMaterials.displayMachineLabel(step.actionLabel(), step.typeId());
            }
            String statusLine = null;
            if (node.status() == RouteTreeData.Status.CYCLE) {
                statusLine = Component.translatable("packai.route_tree.cycle").getString();
            } else if (node.status() == RouteTreeData.Status.NOT_IN_ROUTE) {
                statusLine = Component.translatable("packai.route_tree.not_in_route").getString();
            } else if (node.status() == RouteTreeData.Status.TRUNCATED) {
                // tree.omitted() is not a reliable omitted-material count — do not show a number in UI
                statusLine = Component.translatable("packai.route_tree.truncated").getString();
            }
            layoutCards.add(new TreeCardLayout.Card(
                    node.parentIndex(), node.depth(), hasMachine, rows));
            data.add(new TreeCardData(
                    node, reqLines, fluidLines, statusLine, hasMachine, machineLabel, machineStack));
        }
        if (tree.truncated()) {
            // tree.omitted() is not a reliable omitted-material count — do not show a number in UI
            String truncLine = Component.translatable("packai.route_tree.truncated").getString();
            RouteTreeData.Node truncNode = new RouteTreeData.Node(
                    "", 0, 0, 1, RouteTreeData.Status.TRUNCATED, -1);
            layoutCards.add(new TreeCardLayout.Card(0, 0, false, 1));
            data.add(new TreeCardData(
                    truncNode, List.of(), List.of(), truncLine, false, null, ItemStack.EMPTY));
        }
        TreeCardLayout.Result laid = TreeCardLayout.layout(layoutCards, innerW, innerH);
        this.treeCards = data;
        this.treeRects = laid.rects();
        this.treeContentHeight = laid.contentHeight();
        this.treeMaxScrollY = laid.maxScrollY();
        this.treeDirty = false;
        ObtainGraphRuntime runtime = ObtainGraphRuntime.INSTANCE;
        if (runtime.ready()) {
            this.lastGraphGen = runtime.generation();
            this.lastGraphReady = true;
        }
        clampScroll();
        if (this.pendingAutotestScrollY >= 0) {
            this.scrollY = Mth.clamp(this.pendingAutotestScrollY, 0, this.treeMaxScrollY);
            this.pendingAutotestScrollY = -1;
        }
    }

    private List<String> fluidLinesForStep(StepDto step) {
        if (step.fluids() == null || step.fluids().isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        List<FluidSourceDto> sources = step.fluidSources() != null ? step.fluidSources() : List.of();
        for (FluidDto f : step.fluids()) {
            if (f == null || f.fluidId() == null || f.fluidId().isBlank() || f.amountMb() <= 0) {
                continue;
            }
            String fname = RouteMaterials.displayFluidName(f.fluidId());
            FluidSourceDto src = findFluidSource(sources, f.fluidId());
            if (src != null) {
                String machine = RouteMaterials.displayMachineLabel(src.actionLabel(), src.typeId());
                if (machine == null) {
                    machine = "";
                }
                out.add(Component.translatable("packai.route_tree.fluid_line_src", fname, String.valueOf(f.amountMb()), machine).getString());
            } else {
                out.add(Component.translatable("packai.route_tree.fluid_line_nosrc", fname, String.valueOf(f.amountMb())).getString());
            }
        }
        return out;
    }

    @Nullable
    private static FluidSourceDto findFluidSource(List<FluidSourceDto> sources, String fluidId) {
        if (sources == null || fluidId == null) {
            return null;
        }
        for (FluidSourceDto fs : sources) {
            if (fs != null && fluidId.equals(fs.fluidId())) {
                return fs;
            }
        }
        return null;
    }

    private List<CardReqLine> requirementLinesForStep(StepDto step) {
        ObtainGraph.Edge edge = findEdgeForStep(step);
        if (edge == null || edge.requirements() == null) {
            return List.of();
        }
        List<CardReqLine> reqLines = new ArrayList<>();
        for (ObtainGraph.Requirement req : edge.requirements()) {
            if (req == null || req.amount() <= 0) {
                continue;
            }
            String line;
            int lineColor;
            if ("energy".equalsIgnoreCase(req.kind())) {
                line = Component.translatable("packai.obtain.req.energy", req.amount()).getString();
                lineColor = GuiShell.ACCENT;
            } else {
                String id = req.id() == null || req.id().isBlank() ? req.kind() : req.id();
                line = Component.translatable(
                        "packai.obtain.req.extra", req.amount(), id, req.kind()).getString();
                lineColor = GuiShell.MUTED;
            }
            if (line != null && !line.isEmpty()) {
                reqLines.add(new CardReqLine(line, lineColor));
            }
        }
        return reqLines;
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
        } else if (this.viewMode == ViewMode.TREE) {
            rebuildTreeCards();
        } else if (this.viewMode == ViewMode.SUMMARY) {
            ensureSummaryData();
            RouteSummary.Data data = this.cachedSummary;
            int innerW = Math.max(0, this.contentRight - this.contentLeft);
            boolean stacked = innerW < RouteSummaryView.MIN_W * 2 + 8;
            this.listContentHeight = stacked
                    ? stackedSummaryContentHeight(data)
                    : RouteSummaryView.contentHeight(data.materials().size(), data.machines().size());
        } else {
            this.listContentHeight = 0;
        }
    }

    public static void autotestSetForceTruncated(boolean on) {
        autotestForceTruncated = on;
    }

    /** Autotest only: jump the tree view to a scroll offset before the screenshot. */
    public void autotestScrollTo(int y) {
        if (this.treeRects.isEmpty() || this.treeMaxScrollY <= 0) {
            this.pendingAutotestScrollY = Math.max(0, y);
            return;
        }
        this.scrollY = Mth.clamp(y, 0, this.treeMaxScrollY);
    }

    private void clampScroll() {
        int innerW = this.contentRight - this.contentLeft;
        int innerH = this.contentBottom - this.contentTop;
        if (this.viewMode == ViewMode.TREE) {
            this.scrollY = Mth.clamp(this.scrollY, 0, this.treeMaxScrollY);
        } else if (this.viewMode == ViewMode.SUMMARY) {
            int maxScrollY = Math.max(0, this.listContentHeight - innerH);
            this.summaryScrollY = Mth.clamp(this.summaryScrollY, 0, maxScrollY);
            this.summaryMaxScrollY = maxScrollY;
        } else {
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
        for (int i = 0; i < n; i++) {
            StepDto step = steps.get(i);
            ObtainGraph.Edge edge = findEdgeForStep(step);
            List<CardReqLine> reqLines = requirementLinesForStep(step);
            this.cardReqRows[i] = reqLines.size();
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
        return Math.max(1, (viewSwitcherBandWidth() - 2) / viewModeCount());
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
        int n = viewModeCount();
        int segW = Math.max(1, (this.contentRight - this.contentLeft - 2) / n);
        int rel = (int) mouseX - x0 - 1;
        int idx = Mth.clamp(rel / segW, 0, n - 1);
        return ViewMode.values()[idx];
    }

    private void setViewMode(ViewMode mode, boolean persist) {
        if (this.viewMode == mode) {
            return;
        }
        this.viewMode = mode;
        this.desiredViewName = mode.name().toLowerCase(Locale.ROOT);
        this.scrollY = 0;
        this.cardIndex = 0;
        this.metricsFromGraph = false;
        markTreeDirty();
        if (mode == ViewMode.SUMMARY) {
            markSummaryDirty();
        }
        rebuildListMetrics();
        clampScroll();
        if (persist && mode != ViewMode.SUMMARY) {
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
        int n = viewModeCount();
        Component[] labels = new Component[n];
        labels[0] = Component.translatable("packai.route_view.tree");
        labels[1] = Component.translatable("packai.route_view.cards");
        labels[2] = Component.translatable("packai.route_view.table");
        labels[3] = Component.translatable("packai.route_view.checklist");
        if (n > 4) {
            labels[4] = Component.translatable("packai.route_summary.drawer");
        }
        for (int i = 0; i < n; i++) {
            int sx = x0 + 1 + i * segW;
            int ex = i == n - 1 ? x1 - 1 : sx + segW;
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

        boolean empty = this.route.steps() == null || this.route.steps().isEmpty();
        if (empty) {
            GuiShell.mutedCentered(graphics, this.font,
                    Component.translatable("packai.route_tree.empty"),
                    (this.panelLeft + this.panelRight) / 2,
                    (this.contentTop + this.contentBottom) / 2);
        } else {
            maybeRefreshTreeCards();
            maybeRefreshCardMetrics();
            int scissorBottom = this.contentBottom;
            if (this.viewMode == ViewMode.TREE && "bar".equals(routeSummaryLayout())) {
                scissorBottom -= RouteSummaryView.barH(this.summaryBarExpanded);
            }
            graphics.enableScissor(this.contentLeft, this.contentTop, this.contentRight, scissorBottom);
            switch (this.viewMode) {
                case TREE -> renderTreeCards(graphics);
                case CARDS -> renderCards(graphics);
                case TABLE -> renderMaterialsTable(graphics, mouseX, mouseY);
                case CHECKLIST -> renderChecklist(graphics, mouseX, mouseY);
                case SUMMARY -> renderSummaryTab(graphics, mouseX, mouseY);
                default -> {}
            }
            graphics.disableScissor();
            if (this.viewMode == ViewMode.TREE) {
                String layout = routeSummaryLayout();
                if ("drawer".equals(layout)) {
                    if (this.summaryDrawerOpen) {
                        renderSummaryDrawer(graphics, mouseX, mouseY);
                    }
                    renderSummaryDrawerToggle(graphics, mouseX, mouseY);
                } else if ("bar".equals(layout)) {
                    renderSummaryBar(graphics, mouseX, mouseY);
                }
            }
        }

        super.render(pose, mouseX, mouseY, partialTick);
        WidgetCompat.renderHoveredTips(this, pose, mouseX, mouseY);
    }

    private void renderTreeCards(GuiGraphics g) {
        if (this.treeCards.isEmpty()) {
            g.drawString(this.font, Component.translatable("packai.route_tree.empty").getString(),
                    this.contentLeft, this.contentTop, GuiShell.MUTED, false);
            return;
        }
        if (this.treeRects.size() != this.treeCards.size()) {
            return;
        }
        int scroll = (int) this.scrollY;
        int baseX = this.contentLeft;
        int baseY = this.contentTop - scroll;
        drawTreeIndentLines(g, baseX, baseY);
        List<ItemStack> deferredStacks = new ArrayList<>();
        List<int[]> deferredPos = new ArrayList<>();
        for (int i = 0; i < this.treeCards.size(); i++) {
            TreeCardData data = this.treeCards.get(i);
            TreeCardLayout.Rect rect = this.treeRects.get(i);
            int rx = baseX + rect.x();
            int ry = baseY + rect.y();
            int rw = rect.w();
            int rh = rect.h();
            g.fill(rx, ry, rx + rw, ry + rh, GuiShell.FILL_PRIMARY);
            g.fill(rx, ry, rx + rw, ry + 1, GuiShell.BORDER_SOFT);
            g.fill(rx, ry + rh - 1, rx + rw, ry + rh, GuiShell.BORDER_SOFT);
            g.fill(rx, ry, rx + 1, ry + rh, GuiShell.BORDER_SOFT);
            g.fill(rx + rw - 1, ry, rx + rw, ry + rh, GuiShell.BORDER_SOFT);

            RouteTreeData.Node node = data.node;
            ItemStack product = stackFor(node.itemId());
            if (!product.isEmpty()) {
                deferredStacks.add(product);
                deferredPos.add(new int[] {rx + 3, ry + 2});
            }
            String countSuffix = node.count() > 1 ? "\u00d7" + node.count() : "";
            int countW = countSuffix.isEmpty() ? 0 : this.font.width(countSuffix);
            int nameAvail = TreeCardLayout.nameAvail(rw, countSuffix.isEmpty() ? 0 : countW);
            String name = Fit.fit(displayName(node.itemId()), nameAvail, this.font::width);
            g.drawString(this.font, name, rx + 22, ry + TreeCardLayout.nameY(), GuiShell.TITLE, false);
            if (!countSuffix.isEmpty()) {
                drawRightNumber(g, countSuffix, rx + rw - TreeCardLayout.PAD, ry + TreeCardLayout.nameY(), GuiShell.MUTED);
            }

            if (data.hasMachine) {
                if (!data.machineStack.isEmpty()) {
                    deferredStacks.add(data.machineStack);
                    deferredPos.add(new int[] {rx + 3, ry + 20});
                }
                if (data.machineLabel != null) {
                    String ml = Fit.fit(data.machineLabel, rw - 26, this.font::width);
                    g.drawString(this.font, ml, rx + 22, ry + TreeCardLayout.machineY(), GuiShell.MUTED, false);
                }
            }

            int row = 0;
            for (CardReqLine reqLine : data.requirementLines) {
                String line = Fit.fit(reqLine.text, rw - 8, this.font::width);
                g.drawString(this.font, line, rx + 4, ry + TreeCardLayout.rowY(data.hasMachine, row), reqLine.color, false);
                row++;
            }
            for (String fluidLine : data.fluidLines) {
                String line = Fit.fit(fluidLine, rw - 8, this.font::width);
                g.drawString(this.font, line, rx + 4, ry + TreeCardLayout.rowY(data.hasMachine, row), GuiShell.MUTED, false);
                row++;
            }
            if (data.statusLine != null) {
                String line = Fit.fit(data.statusLine, rw - 8, this.font::width);
                g.drawString(this.font, line, rx + 4, ry + TreeCardLayout.rowY(data.hasMachine, row), MISSING_WARN, false);
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

    private void drawTreeIndentLines(GuiGraphics g, int baseX, int baseY) {
        for (int i = 0; i < this.treeCards.size(); i++) {
            TreeCardData data = this.treeCards.get(i);
            if (data.node.parentIndex() < 0) {
                continue;
            }
            int parent = data.node.parentIndex();
            if (parent >= this.treeRects.size()) {
                continue;
            }
            TreeCardLayout.Rect child = this.treeRects.get(i);
            TreeCardLayout.Rect parentRect = this.treeRects.get(parent);
            int lineX = baseX + child.x() - 6;
            int childTop = baseY + child.y();
            int parentBottomY = baseY + parentRect.y() + parentRect.h();
            int parentCenterX = baseX + parentRect.x() + parentRect.w() / 2;
            int y0 = Math.min(childTop, parentBottomY);
            int y1 = Math.max(childTop, parentBottomY);
            g.fill(lineX - 1, y0, lineX + 1, y1, GuiShell.BORDER);
            if (lineX != parentCenterX) {
                int left = Math.min(lineX, parentCenterX);
                int right = Math.max(lineX, parentCenterX);
                g.fill(left, parentBottomY - 1, right + 1, parentBottomY + 1, GuiShell.BORDER);
            }
        }
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

    private static final int SUMMARY_SECTION_GAP = 6;

    private void refreshSummaryScrollBounds(int viewportH, boolean stackedSections) {
        ensureSummaryData();
        RouteSummary.Data data = this.cachedSummary;
        int contentH = stackedSections
                ? stackedSummaryContentHeight(data)
                : RouteSummaryView.contentHeight(data.materials().size(), data.machines().size());
        this.summaryMaxScrollY = Math.max(0, contentH - Math.max(0, viewportH));
        this.summaryScrollY = Mth.clamp(this.summaryScrollY, 0, this.summaryMaxScrollY);
    }

    private static int stackedSummaryContentHeight(RouteSummary.Data data) {
        int m = data.materials().size();
        int mc = data.machines().size();
        if (data.empty() || (m == 0 && mc == 0)) {
            return RouteSummaryView.HEADER_H + RouteSummaryView.HEADER_H;
        }
        int h = RouteSummaryView.HEADER_H;
        if (m > 0) {
            h += RouteSummaryView.HEADER_H + m * RouteSummaryView.ROW_H;
        }
        if (mc > 0) {
            if (m > 0) {
                h += SUMMARY_SECTION_GAP;
            }
            h += RouteSummaryView.HEADER_H + mc * RouteSummaryView.ROW_H;
        }
        return h;
    }

    private void renderSummaryTab(GuiGraphics g, int mouseX, int mouseY) {
        int innerW = this.contentRight - this.contentLeft;
        int innerH = this.contentBottom - this.contentTop;
        boolean stacked = innerW < RouteSummaryView.MIN_W * 2 + 8;
        refreshSummaryScrollBounds(innerH, stacked);
        renderSummaryPanel(
                g,
                this.contentLeft,
                this.contentTop,
                innerW,
                innerH,
                (int) this.summaryScrollY,
                mouseX,
                mouseY,
                stacked);
    }

    private void renderSummaryDrawerToggle(GuiGraphics g, int mouseX, int mouseY) {
        String label = Component.translatable("packai.route_summary.drawer").getString();
        int btnW = Math.min(this.contentRight - this.contentLeft - 8, this.font.width(label) + 12);
        int btnH = VIEW_SEGMENT_H;
        int btnX = this.contentRight - btnW - 2;
        if (this.summaryDrawerOpen) {
            int innerW = this.contentRight - this.contentLeft;
            int drawerLeft = this.contentRight - RouteSummaryView.drawerW(innerW);
            btnX = drawerLeft - btnW - 4;
        }
        if (btnX < this.contentLeft) {
            btnX = this.contentLeft;
        }
        int btnY = this.contentTop + 2;
        this.summaryDrawerBtnX = btnX;
        this.summaryDrawerBtnY = btnY;
        this.summaryDrawerBtnW = btnW;
        this.summaryDrawerBtnH = btnH;
        boolean hover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(btnX, btnY, btnX + btnW, btnY + btnH, hover ? GuiShell.FILL_PRIMARY : GuiShell.FILL_SECONDARY);
        g.fill(btnX, btnY, btnX + btnW, btnY + 1, GuiShell.BORDER);
        g.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, GuiShell.BORDER);
        g.fill(btnX, btnY, btnX + 1, btnY + btnH, GuiShell.BORDER);
        g.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, GuiShell.BORDER);
        int ty = btnY + (btnH - this.font.lineHeight) / 2;
        drawFitted(g, label, btnX + 6, ty, btnX + btnW - 4, this.summaryDrawerOpen ? GuiShell.TITLE : GuiShell.MUTED);
    }

    private void renderSummaryDrawer(GuiGraphics g, int mouseX, int mouseY) {
        int innerW = this.contentRight - this.contentLeft;
        int dw = RouteSummaryView.drawerW(innerW);
        int dh = this.contentBottom - this.contentTop;
        int dx = this.contentRight - dw;
        int dy = this.contentTop;
        refreshSummaryScrollBounds(dh, true);
        this.summaryPanelX = dx;
        this.summaryPanelY = dy;
        this.summaryPanelW = dw;
        this.summaryPanelH = dh;
        renderSummaryPanel(g, dx, dy, dw, dh, (int) this.summaryScrollY, mouseX, mouseY, true);
    }

    private void renderSummaryBar(GuiGraphics g, int mouseX, int mouseY) {
        int barH = RouteSummaryView.barH(this.summaryBarExpanded);
        int x0 = this.contentLeft;
        int y0 = this.contentBottom - barH;
        int w = this.contentRight - this.contentLeft;
        g.fill(x0, y0, this.contentRight, this.contentBottom, GuiShell.FILL_SECONDARY);
        g.fill(x0, y0, this.contentRight, y0 + 1, GuiShell.BORDER);
        if (!this.summaryBarExpanded) {
            ensureSummaryData();
            RouteSummary.Totals totals = this.cachedSummary.totals();
            int ty = y0 + (barH - this.font.lineHeight) / 2;
            String totalsLine = summaryTotalsLine(totals, this.contentRight - 6 - (x0 + 6));
            g.drawString(this.font, totalsLine, x0 + 6, ty, GuiShell.MUTED, false);
            String expand = Component.translatable("packai.route_summary.expand").getString();
            drawRightNumber(g, expand, this.contentRight - 6, ty, GuiShell.ACCENT);
            this.summaryPanelX = x0;
            this.summaryPanelY = y0;
            this.summaryPanelW = w;
            this.summaryPanelH = barH;
            return;
        }
        refreshSummaryScrollBounds(barH - RouteSummaryView.HEADER_H, true);
        this.summaryPanelX = x0;
        this.summaryPanelY = y0;
        this.summaryPanelW = w;
        this.summaryPanelH = barH;
        renderSummaryPanel(g, x0, y0, w, barH, (int) this.summaryScrollY, mouseX, mouseY, true);
    }

    private String summaryTotalsLine(RouteSummary.Totals totals, int maxWidth) {
        String dm = String.valueOf(totals.distinctMaterials());
        String ti = String.valueOf(totals.totalItems());
        String mc = String.valueOf(totals.machines());
        String key = totals.machines() == 1 ? "packai.route_summary.totals_one" : "packai.route_summary.totals";
        String full = Component.translatable(key, dm, ti, mc).getString();
        if (maxWidth > 0 && this.font.width(full) > maxWidth) {
            full = Component.translatable("packai.route_summary.totals_short", dm, ti, mc).getString();
        }
        return Fit.fit(full, Math.max(0, maxWidth), this.font::width);
    }

    private String summaryMachineDisplay(RouteSummary.Machine machine) {
        String label = RouteMaterials.displayMachineLabel(machine.label(), machine.typeId());
        if (label == null || label.isBlank()) {
            label = machine.typeId();
        } else if (label.indexOf(':') >= 0 && machine.typeId() != null && !machine.typeId().isBlank()) {
            ResourceLocation rl = ResourceLocation.tryParse(machine.typeId());
            if (rl != null) {
                ItemStack stack = MachineIcons.machineFor(rl);
                if (stack != null && !stack.isEmpty()) {
                    String hover = stack.getHoverName().getString();
                    if (hover != null && !hover.isBlank()) {
                        label = hover;
                    }
                }
            }
        }
        return label != null ? label : "";
    }

    private void renderSummaryPanel(
            GuiGraphics g,
            int x,
            int y,
            int w,
            int h,
            int scroll,
            int mouseX,
            int mouseY,
            boolean stackedSections) {
        if (w <= 0 || h <= 0) {
            return;
        }
        ensureSummaryData();
        RouteSummary.Data data = this.cachedSummary;
        g.fill(x, y, x + w, y + h, GuiShell.FILL_SECONDARY);
        g.fill(x, y, x + w, y + 1, GuiShell.BORDER);
        g.fill(x, y + h - 1, x + w, y + h, GuiShell.BORDER);
        g.fill(x, y, x + 1, y + h, GuiShell.BORDER);
        g.fill(x + w - 1, y, x + w, y + h, GuiShell.BORDER);

        RouteSummary.Totals totals = data.totals();
        int ty = y + 2;
        String totalsLine = summaryTotalsLine(totals, w - 8);
        g.drawString(this.font, totalsLine, x + 4, ty, GuiShell.MUTED, false);

        if (data.empty() || (data.materials().isEmpty() && data.machines().isEmpty())) {
            String empty = Component.translatable("packai.route_summary.empty").getString();
            int ey = y + RouteSummaryView.HEADER_H + (h - RouteSummaryView.HEADER_H) / 2;
            drawFitted(g, empty, x + 4, ey, x + w - 4, GuiShell.MUTED);
            return;
        }

        int innerX = x + 4;
        int innerW = w - 8;
        int bodyY = y + RouteSummaryView.HEADER_H - scroll;

        if (stackedSections) {
            int rowY = bodyY;
            if (!data.materials().isEmpty()) {
                String matTitle = Component.translatable("packai.route_summary.title").getString();
                if (rowY + RouteSummaryView.HEADER_H >= y && rowY <= y + h) {
                    drawFitted(g, matTitle, innerX, rowY, innerX + innerW, GuiShell.MUTED);
                }
                rowY += RouteSummaryView.HEADER_H;
                for (RouteSummary.Material mat : data.materials()) {
                    if (rowY + RouteSummaryView.ROW_H >= y && rowY <= y + h) {
                        String line = Fit.fit(
                                displayName(mat.itemId()) + " \u00d7" + mat.count(),
                                innerW,
                                this.font::width);
                        g.drawString(this.font, line, innerX, rowY, GuiShell.MUTED, false);
                    }
                    rowY += RouteSummaryView.ROW_H;
                }
            }
            if (!data.machines().isEmpty()) {
                if (!data.materials().isEmpty()) {
                    rowY += SUMMARY_SECTION_GAP;
                }
                String macTitle = Component.translatable("packai.route_summary.machines").getString();
                if (rowY + RouteSummaryView.HEADER_H >= y && rowY <= y + h) {
                    drawFitted(g, macTitle, innerX, rowY, innerX + innerW, GuiShell.MUTED);
                }
                rowY += RouteSummaryView.HEADER_H;
                for (RouteSummary.Machine machine : data.machines()) {
                    if (rowY + RouteSummaryView.ROW_H >= y && rowY <= y + h) {
                        String line = Fit.fit(
                                summaryMachineDisplay(machine) + " \u00d7" + machine.steps(),
                                innerW,
                                this.font::width);
                        g.drawString(this.font, line, innerX, rowY, GuiShell.MUTED, false);
                    }
                    rowY += RouteSummaryView.ROW_H;
                }
            }
            return;
        }

        RouteSummaryView.Columns cols = RouteSummaryView.columns(innerX, innerW);
        int headerY = bodyY;
        drawFitted(
                g,
                Component.translatable("packai.route_summary.title").getString(),
                cols.leftX(),
                headerY,
                cols.leftX() + cols.leftW(),
                GuiShell.TITLE);
        if (cols.rightW() > 0) {
            drawFitted(
                    g,
                    Component.translatable("packai.route_summary.machines").getString(),
                    cols.rightX(),
                    headerY,
                    cols.rightX() + cols.rightW(),
                    GuiShell.TITLE);
        }
        int rowY = headerY + RouteSummaryView.HEADER_H;
        int rowCount = Math.max(data.materials().size(), data.machines().size());
        for (int i = 0; i < rowCount; i++) {
            int lineY = rowY + i * RouteSummaryView.ROW_H;
            if (lineY + RouteSummaryView.ROW_H < y || lineY > y + h) {
                continue;
            }
            if (i < data.materials().size()) {
                RouteSummary.Material mat = data.materials().get(i);
                String line = Fit.fit(
                        displayName(mat.itemId()) + " \u00d7" + mat.count(),
                        cols.leftW(),
                        this.font::width);
                g.drawString(this.font, line, cols.leftX(), lineY, GuiShell.MUTED, false);
            }
            if (cols.rightW() > 0 && i < data.machines().size()) {
                RouteSummary.Machine machine = data.machines().get(i);
                String line = Fit.fit(
                        summaryMachineDisplay(machine) + " \u00d7" + machine.steps(),
                        cols.rightW(),
                        this.font::width);
                g.drawString(this.font, line, cols.rightX(), lineY, GuiShell.MUTED, false);
            }
        }
    }

    private boolean mouseInSummaryPanel(double mouseX, double mouseY) {
        return mouseX >= this.summaryPanelX
                && mouseX < this.summaryPanelX + this.summaryPanelW
                && mouseY >= this.summaryPanelY
                && mouseY < this.summaryPanelY + this.summaryPanelH;
    }

    private void scrollSummary(double scrollDelta) {
        this.summaryScrollY = Mth.clamp(this.summaryScrollY - scrollDelta * 16, 0, this.summaryMaxScrollY);
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
        // 同 Tree 用同一份數量來源（inputItems），舊存檔 fallback 去 slot 數
        if (step.inputItems() != null && !step.inputItems().isEmpty()) {
            for (InputDto in : step.inputItems()) {
                if (in == null || in.id() == null || in.id().isBlank() || in.count() <= 0) {
                    continue;
                }
                counts.merge(in.id(), in.count(), Integer::sum);
            }
        } else if (step.inputs() != null) {
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
        if (button == 0 && this.viewMode == ViewMode.TREE && "drawer".equals(routeSummaryLayout())) {
            if (mouseX >= this.summaryDrawerBtnX
                    && mouseX < this.summaryDrawerBtnX + this.summaryDrawerBtnW
                    && mouseY >= this.summaryDrawerBtnY
                    && mouseY < this.summaryDrawerBtnY + this.summaryDrawerBtnH) {
                this.summaryDrawerOpen = !this.summaryDrawerOpen;
                this.summaryScrollY = 0;
                if (this.minecraft != null) {
                    this.minecraft.getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                return true;
            }
        }
        if (button == 0 && this.viewMode == ViewMode.TREE && "bar".equals(routeSummaryLayout())) {
            int barH = RouteSummaryView.barH(this.summaryBarExpanded);
            int y0 = this.contentBottom - barH;
            if (mouseX >= this.contentLeft
                    && mouseX < this.contentRight
                    && mouseY >= y0
                    && mouseY < this.contentBottom) {
                this.summaryBarExpanded = !this.summaryBarExpanded;
                this.summaryScrollY = 0;
                if (this.minecraft != null) {
                    this.minecraft.getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                return true;
            }
        }
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
        if (this.viewMode == ViewMode.SUMMARY
                && mouseX >= this.contentLeft
                && mouseX <= this.contentRight
                && mouseY >= this.contentTop
                && mouseY <= this.contentBottom) {
            int innerW = this.contentRight - this.contentLeft;
            int innerH = this.contentBottom - this.contentTop;
            boolean stacked = innerW < RouteSummaryView.MIN_W * 2 + 8;
            refreshSummaryScrollBounds(innerH, stacked);
            scrollSummary(scrollDelta);
            return true;
        }
        if (mouseInSummaryPanel(mouseX, mouseY) && this.summaryMaxScrollY > 0) {
            scrollSummary(scrollDelta);
            return true;
        }
        if (mouseX >= this.contentLeft
                && mouseX <= this.contentRight
                && mouseY >= this.contentTop
                && mouseY <= this.contentBottom) {
            int innerW = this.contentRight - this.contentLeft;
            int innerH = this.contentBottom - this.contentTop;
            if (this.viewMode == ViewMode.TREE) {
                this.scrollY = Mth.clamp(this.scrollY - scrollDelta * 16, 0, this.treeMaxScrollY);
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

    private static final class TreeCardData {
        final RouteTreeData.Node node;
        final List<CardReqLine> requirementLines;
        final List<String> fluidLines;
        @Nullable
        final String statusLine;
        final boolean hasMachine;
        @Nullable
        final String machineLabel;
        final ItemStack machineStack;

        TreeCardData(
                RouteTreeData.Node node,
                List<CardReqLine> requirementLines,
                List<String> fluidLines,
                @Nullable String statusLine,
                boolean hasMachine,
                @Nullable String machineLabel,
                ItemStack machineStack) {
            this.node = node;
            this.requirementLines = requirementLines;
            this.fluidLines = fluidLines;
            this.statusLine = statusLine;
            this.hasMachine = hasMachine;
            this.machineLabel = machineLabel;
            this.machineStack = machineStack != null ? machineStack : ItemStack.EMPTY;
        }
    }
}

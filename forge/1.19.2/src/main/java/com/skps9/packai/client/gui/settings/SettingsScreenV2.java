package com.skps9.packai.client.gui.settings;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.gui.AiAssistantScreen;
import com.skps9.packai.client.gui.GuiGraphics;
import com.skps9.packai.client.gui.GuiShell;
import com.skps9.packai.client.gui.ModelPickerScreen;
import com.skps9.packai.client.gui.ShiftTipHost;
import com.skps9.packai.client.gui.WidgetCompat;
import com.skps9.packai.client.gui.settings.SettingsLayout.Layout;
import com.skps9.packai.client.gui.settings.SettingsLayout.Rect;
import com.skps9.packai.client.gui.settings.SettingsRegistry.ControlType;
import com.skps9.packai.client.gui.settings.SettingsRegistry.Entry;
import com.skps9.packai.client.gui.settings.SettingsRegistry.UiCategory;
import com.skps9.packai.client.jei.JeiCategoryCatalog;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.AskEngine;
import com.skps9.packai.logic.CostWindow;
import com.skps9.packai.logic.DailyTokenUsage;
import com.skps9.packai.logic.KnowledgeRemote;
import com.skps9.packai.logic.KnowledgeStore;
import com.skps9.packai.logic.LlmClient;
import com.skps9.packai.logic.ModelCatalog;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Settings V2 (Batch B2): registry-driven controls + JEI category editor.
 * Geometry from {@link SettingsLayout} only; search filter in responder (not render).
 */
public class SettingsScreenV2 extends Screen implements ShiftTipHost {
    private static final Set<String> SECRET_PATHS = Set.of(
            "llm.apiKey", "web.tavilyApiKey", "web.serperApiKey");
    private static final Set<String> FREE_TEXT_LIST = Set.of(
            "ui.recipeCardMirrorCategories",
            "ui.ingredientNbtSkipPatterns",
            "ui.ingredientNbtKeepPatterns");
    /** Unified picker opens only from llm.model; ollamaModel stays in registry (hiddenInUi). */
    private static final Set<String> MODEL_PICKER_PATHS = Set.of("llm.model");
    /** Keep string for check_settings_setters UI_KEYS (ollamaModel must appear in this file). */
    @SuppressWarnings("unused")
    private static final String OLLAMA_MODEL_KEY_ANCHOR = "ollamaModel";
    private static final String JEI_CATS_PATH = "ui.recipeCategoryOrder";
    private static final int DESC_BODY_MAX_LINES = 3;

    private static final List<Integer> JEI_CHARS = List.of(2000, 4000, 8000, 12000);
    private static final List<Integer> HISTORY_TURNS = List.of(0, 2, 4, 8, 12, 16);
    private static final List<Integer> MAX_FACTS = List.of(4, 8, 12, 16, 24, 32);
    private static final List<Integer> CLIP_RADII = List.of(10, 20, 30, 40, 50);
    private static final List<Integer> TRACE_KEEP = List.of(1, 10, 25, 50, 100, 200, 500);
    private static final List<Integer> TRACE_DAYS = List.of(0, 1, 3, 7, 14, 30, 90, 365);
    private static final List<Integer> TOOL_ROUNDS = List.of(1, 2, 3, 4, 5, 6, 7, 8);
    private static final List<Integer> DAILY_TOKENS =
            List.of(0, 10_000, 50_000, 100_000, 250_000, 500_000, 1_000_000);
    private static final List<Integer> CACHE_MB = List.of(1, 4, 8, 16, 32, 64, 128, 256, 512);
    private static final List<Integer> RECIPE_CARDS = List.of(1, 2, 3, 4, 5, 6, 8);

    private static final int TOGGLE_W = 22;
    private static final int HANDLE_W = 14;
    /** Screen-local memo for daily usage row (1s); do not push into logic layer. */
    private static final long USAGE_SUMMARY_CACHE_MS = 1000L;
    /** Plan A S9: one-shot layout diag per JVM session. */
    private static boolean settingsLayoutLogged;

    private final Screen parent;
    private EditBox search;
    private EditBox valueBox;
    private Layout layout;
    private UiCategory category = UiCategory.CONNECTION;
    private List<Entry> filtered = List.of();
    private int scrollOffset;
    private int highlightIndex;
    private String searchQuery = "";
    private String editingPath;
    private String status = "";

    /** JEI category editor (inlined from deleted RecipeCategoryScreen; Neo still keeps old screens while paused). */
    private boolean jeiCatsMode;
    private List<JeiCategoryCatalog.Row> jeiAllRows = List.of();
    private List<Integer> jeiFilteredIdx = List.of();
    private int dragFrom = -1;
    private int dragHoverInsert = -1;

    private boolean knowledgeTestBusy;
    private String draftApiKey;
    private String draftBaseUrl;
    /** Uncommitted valueBox text while {@code editingPath != null} (survives off-screen scroll). */
    private String editDraft;
    private int editCaret = -1;
    private String usageSummaryCache;
    private long usageSummaryCacheAtMs;

    public SettingsScreenV2(Screen parent) {
        super(Component.translatable("packai.settings.title"));
        this.parent = parent;
    }

    /** Headless alias — same as {@link SettingsLayout#rowRects(int, int, int)}. */
    public static List<Rect> rowRects(int w, int h, int rows) {
        return SettingsLayout.rowRects(w, h, rows);
    }

    /**
     * Guard anchor: every {@link ControlType} is constructed / handled here (TOGGLE / NUMBER /
     * TEXT / LIST). {@code tests/check_settings_registry.py} asserts these tokens exist.
     */
    @SuppressWarnings("unused")
    private static ControlType[] controlTypesImplemented() {
        return new ControlType[] {
            ControlType.TOGGLE, ControlType.NUMBER, ControlType.TEXT, ControlType.LIST
        };
    }

    @Override
    protected void init() {
        this.layout = SettingsLayout.compute(this.width, this.height);
        this.search = WidgetCompat.editBox(
                this.layout.searchBox.x,
                this.layout.searchBox.y,
                this.layout.searchBox.w,
                this.layout.searchBox.h,
                Component.translatable(
                        this.jeiCatsMode ? "packai.recipe_cats.search" : "packai.settings.v2.search"),
                Component.translatable(
                        this.jeiCatsMode
                                ? "packai.recipe_cats.tooltip.search"
                                : "packai.settings.v2.tooltip.search"));
        this.search.setMaxLength(128);
        this.search.setValue(this.searchQuery);
        this.search.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.search);

        int btnY = this.height - SettingsLayout.BOTTOM_CHROME;
        int btnH = 20;
        int gap = 4;
        int doneW = 72;
        int resetW = 72;
        int allW = 72;
        int x = this.width - 8 - doneW;
        this.addRenderableWidget(WidgetCompat.button(
                x,
                btnY,
                doneW,
                btnH,
                Component.translatable("gui.done"),
                b -> onDonePressed(),
                Component.translatable("packai.settings.tooltip.done")));
        x -= gap + allW;
        this.addRenderableWidget(WidgetCompat.button(
                x,
                btnY,
                allW,
                btnH,
                Component.translatable("packai.settings.v2.reset_all"),
                b -> resetAll(),
                Component.translatable("packai.settings.v2.tooltip.reset_all")));
        x -= gap + resetW;
        this.addRenderableWidget(WidgetCompat.button(
                x,
                btnY,
                resetW,
                btnH,
                Component.translatable("packai.settings.v2.reset_page"),
                b -> resetPage(),
                Component.translatable("packai.settings.v2.tooltip.reset_page")));

        if (!this.jeiCatsMode && this.category == UiCategory.ADVANCED) {
            int testW = 88;
            int clearW = 88;
            int lx = 8;
            this.addRenderableWidget(WidgetCompat.button(
                    lx,
                    btnY,
                    testW,
                    btnH,
                    Component.translatable("packai.settings.knowledge_test"),
                    b -> testKnowledgeConnection(),
                    Component.translatable("packai.settings.tooltip.knowledge_test")));
            lx += testW + gap;
            this.addRenderableWidget(WidgetCompat.button(
                    lx,
                    btnY,
                    clearW,
                    btnH,
                    Component.translatable("packai.settings.knowledge_clear_cache"),
                    b -> clearKnowledgeCache(),
                    Component.translatable("packai.settings.tooltip.knowledge_clear_cache")));
        }

        if (this.jeiCatsMode) {
            reloadJeiRows();
        } else {
            applyFilter();
            if (this.category == UiCategory.CONNECTION && this.draftBaseUrl == null) {
                String b = PackAiConfig.API_BASE_URL.get();
                this.draftBaseUrl = b == null ? "" : b;
            }
            attachValueBoxIfNeeded();
        }
        this.setInitialFocus(this.search);
    }

    private void onDonePressed() {
        if (this.jeiCatsMode) {
            exitJeiCatsMode();
            return;
        }
        onClose();
    }

    private void exitJeiCatsMode() {
        this.jeiCatsMode = false;
        this.dragFrom = -1;
        this.dragHoverInsert = -1;
        this.searchQuery = "";
        this.scrollOffset = 0;
        rebuildUi();
    }

    private void enterJeiCatsMode() {
        this.jeiCatsMode = true;
        this.editingPath = null;
        this.valueBox = null;
        clearEditDraft();
        this.searchQuery = "";
        this.scrollOffset = 0;
        this.dragFrom = -1;
        rebuildUi();
    }

    private void rebuildUi() {
        this.clearWidgets();
        this.init();
    }

    /**
     * Rebuild while keeping uncommitted valueBox text (scroll / search must not evaporate draft).
     * Do <b>not</b> use from {@link #startEdit} — that path commits then seeds from config.
     */
    private void rebuildUiPreservingDraft() {
        captureEditDraftFromBox();
        rebuildUi();
    }

    private void captureEditDraftFromBox() {
        if (this.valueBox != null && this.editingPath != null) {
            this.editDraft = this.valueBox.getValue();
            this.editCaret = this.valueBox.getCursorPosition();
        }
    }

    private void clearEditDraft() {
        this.editDraft = null;
        this.editCaret = -1;
    }

    /** Search filter runs in the EditBox responder — never per render frame. */
    private void onSearchChanged(String raw) {
        this.searchQuery = raw == null ? "" : raw;
        if (this.jeiCatsMode) {
            applyJeiFilter();
        } else {
            applyFilter();
            if (this.editingPath != null) {
                rebuildUiPreservingDraft();
            }
        }
    }

    private void applyFilter() {
        String q = this.searchQuery.trim().toLowerCase(Locale.ROOT);
        List<Entry> next = new ArrayList<>();
        for (Entry e : SettingsRegistry.byCategory(this.category)) {
            if (e.hiddenInUi) {
                continue;
            }
            if (q.isEmpty() || matchesSearch(e, q)) {
                next.add(e);
            }
        }
        this.filtered = next;
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll());
        if (this.highlightIndex >= this.filtered.size()) {
            this.highlightIndex = Math.max(0, this.filtered.size() - 1);
        }
    }

    private boolean matchesSearch(Entry e, String qLower) {
        if (e.path.toLowerCase(Locale.ROOT).contains(qLower)) {
            return true;
        }
        String label = Component.translatable(e.labelKey).getString();
        String tip = Component.translatable(e.tooltipKey).getString();
        return label.toLowerCase(Locale.ROOT).contains(qLower)
                || tip.toLowerCase(Locale.ROOT).contains(qLower);
    }

    private int visibleRows() {
        if (this.layout == null) {
            return 1;
        }
        return Math.max(1, this.layout.maxVisibleEntryRows);
    }

    private int maxScroll() {
        if (this.jeiCatsMode) {
            return Math.max(0, this.jeiFilteredIdx.size() - visibleRows());
        }
        return Math.max(0, this.filtered.size() - visibleRows());
    }

    private void selectCategory(UiCategory cat) {
        if (cat == null || cat == this.category || this.jeiCatsMode) {
            return;
        }
        commitValueBox();
        this.category = cat;
        this.scrollOffset = 0;
        this.highlightIndex = 0;
        this.editingPath = null;
        clearEditDraft();
        rebuildUi();
    }

    private void attachValueBoxIfNeeded() {
        if (this.editingPath == null || this.layout == null || this.layout.fallback) {
            return;
        }
        int idx = indexOfPath(this.editingPath);
        if (idx < 0 || idx < this.scrollOffset || idx >= this.scrollOffset + visibleRows()) {
            return;
        }
        Entry e = this.filtered.get(idx);
        List<Rect> rows = SettingsLayout.entryRowRects(this.layout, visibleRows());
        Rect r = rows.get(idx - this.scrollOffset);
        String label = Component.translatable(e.labelKey).getString();
        int labelCap = Math.max(24, (int) (r.w * 0.55));
        int labelW = Math.min(this.font.width(label), labelCap);
        int boxX = r.x + labelW + 4;
        int boxW = Math.max(40, r.right() - 4 - boxX);
        this.valueBox = WidgetCompat.editBoxNoTip(
                boxX,
                r.y,
                boxW,
                Math.min(SettingsLayout.ROW_H - 1, 18),
                Component.translatable(e.labelKey));
        this.valueBox.setMaxLength(512);
        String raw = this.editDraft != null ? this.editDraft : displayValue(e);
        this.valueBox.setValue(raw);
        if (this.editDraft != null && this.editCaret >= 0) {
            int caret = Mth.clamp(this.editCaret, 0, raw.length());
            this.valueBox.setCursorPosition(caret);
            this.valueBox.setHighlightPos(caret);
        }
        if (SECRET_PATHS.contains(e.path)) {
            this.valueBox.setFormatter((text, first) -> FormattedCharSequence.forward(
                    "*".repeat(Math.min(text.length(), 128)), Style.EMPTY));
            this.valueBox.setResponder(v -> {
                if (SettingsRegistry.isPlaceholderValue(v)) {
                    return;
                }
                this.editDraft = v;
                this.editCaret = this.valueBox.getCursorPosition();
                // Draft only — persist on focus loss / Done / onClose (never keystroke SPEC.save).
                if ("llm.apiKey".equals(e.path)) {
                    this.draftApiKey = v;
                }
            });
        } else {
            this.valueBox.setResponder(v -> {
                if (SettingsRegistry.isPlaceholderValue(v)) {
                    return;
                }
                this.editDraft = v;
                this.editCaret = this.valueBox.getCursorPosition();
                if ("llm.apiBaseUrl".equals(e.path)) {
                    this.draftBaseUrl = v;
                }
                // Other TEXT: value lives in EditBox until commitValueBox / onClose.
            });
        }
        this.addRenderableWidget(this.valueBox);
        this.setInitialFocus(this.valueBox);
    }

    private int indexOfPath(String path) {
        for (int i = 0; i < this.filtered.size(); i++) {
            if (this.filtered.get(i).path.equals(path)) {
                return i;
            }
        }
        return -1;
    }

    private void commitValueBox() {
        if (this.valueBox == null || this.editingPath == null) {
            return;
        }
        Entry e = SettingsRegistry.byPath(this.editingPath);
        if (e == null) {
            return;
        }
        String v = this.valueBox.getValue();
        if (SettingsRegistry.isPlaceholderValue(v)) {
            return;
        }
        writeEntry(e, v);
        if ("llm.apiKey".equals(e.path)) {
            this.draftApiKey = v;
        }
        if ("llm.apiBaseUrl".equals(e.path)) {
            this.draftBaseUrl = v;
        }
    }

    private void writeEntry(Entry e, String v) {
        if (e == null || v == null) {
            return;
        }
        if (SettingsRegistry.isPlaceholderValue(v)) {
            return;
        }
        if ("llm.apiBaseUrl".equals(e.path) || "llm.ollamaBaseUrl".equals(e.path)) {
            v = LlmClient.normalizeApiBaseUrl(v);
        }
        e.setter.accept(v);
        if ("ui.showHiddenQuests".equals(e.path)) {
            AskEngine.INSTANCE.invalidateIndexes();
        }
        if ("llm.mode".equals(e.path)) {
            ModelCatalog.invalidate();
            ModelCatalog.refreshAsync(true, () -> {
            });
        }
    }

    private String displayValue(Entry e) {
        if (SECRET_PATHS.contains(e.path)) {
            String live = safeGet(e);
            if (live != null && !live.isBlank()) {
                return "•••";
            }
            return "";
        }
        return safeGet(e);
    }

    private static String safeGet(Entry e) {
        try {
            String v = e.getter.get();
            return v == null ? "" : v;
        } catch (Exception ex) {
            return "";
        }
    }

    private String valueSummary(Entry e) {
        if (JEI_CATS_PATH.equals(e.path)) {
            return Component.translatable("packai.settings.v2.edit_list").getString();
        }
        if ("llm.model".equals(e.path)) {
            String m = PackAiConfig.uiModel();
            String tag = Component.translatable(PackAiConfig.effectiveModelTagKey()).getString();
            return m + " (" + tag + ")";
        }
        if ("llm.dailyTokenLimit".equals(e.path)) {
            return dailyTokenUsageSummaryCached();
        }
        String v = displayValue(e);
        if (e.type == ControlType.TOGGLE) {
            boolean on = parseBool(v);
            return Component.translatable(
                            on ? "packai.settings.v2.on" : "packai.settings.v2.off")
                    .getString();
        }
        return v;
    }

    private String dailyTokenUsageSummaryCached() {
        long now = System.currentTimeMillis();
        if (usageSummaryCache != null && now - usageSummaryCacheAtMs < USAGE_SUMMARY_CACHE_MS) {
            return usageSummaryCache;
        }
        String built = buildDailyTokenUsageSummary();
        usageSummaryCache = built;
        usageSummaryCacheAtMs = now;
        return built;
    }

    private String buildDailyTokenUsageSummary() {
        var mc = this.minecraft;
        Path gameDir = mc != null && mc.gameDirectory != null ? mc.gameDirectory.toPath() : null;
        if (CostWindow.isUsageUnreadable(gameDir)) {
            return Component.translatable("packai.settings.usage.unreadable").getString();
        }
        int used = DailyTokenUsage.todayUsed(gameDir);
        int limit = PackAiConfig.dailyTokenLimit();
        String counts = CostWindow.usageSummary(used, limit);
        long epoch = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        String line = Component.translatable(
                        "packai.settings.usage.summary",
                        counts,
                        CostWindow.nextResetUtcLabel(),
                        CostWindow.nextResetLocal(epoch, zone))
                .getString();
        String apiBase = PackAiConfig.API_BASE_URL.get();
        if (CostWindow.isDeepSeekPeak(Instant.now(), apiBase, PackAiConfig.uiUsesOllamaModel())) {
            line = line + " " + Component.translatable("packai.settings.usage.peak").getString();
        }
        return line;
    }

    /** Truncate {@code text} to fit {@code maxPx} (font width); empty if maxPx too small. */
    private String fitWidth(String text, int maxPx) {
        if (text == null || text.isEmpty() || maxPx <= 0) {
            return "";
        }
        if (this.font.width(text) <= maxPx) {
            return text;
        }
        if (maxPx < 12) {
            return "";
        }
        return this.font.plainSubstrByWidth(text, maxPx - 8) + "...";
    }

    // --- ControlType handlers (TOGGLE / NUMBER / TEXT / LIST) ---

    private void activateRow(Entry e) {
        this.highlightIndex = Math.max(0, indexOfPath(e.path));
        if (e.type == ControlType.TOGGLE) {
            flipToggle(e);
            return;
        }
        if (e.type == ControlType.NUMBER) {
            startEdit(e);
            return;
        }
        if (e.type == ControlType.LIST) {
            if (JEI_CATS_PATH.equals(e.path)) {
                enterJeiCatsMode();
                return;
            }
            if (FREE_TEXT_LIST.contains(e.path)) {
                startEdit(e);
                return;
            }
            cycleList(e);
            return;
        }
        if (e.type == ControlType.TEXT) {
            if (MODEL_PICKER_PATHS.contains(e.path)) {
                openModelPicker();
                return;
            }
            startEdit(e);
        }
    }

    private void flipToggle(Entry e) {
        boolean cur = parseBool(safeGet(e));
        writeEntry(e, Boolean.toString(!cur));
    }

    private void cycleList(Entry e) {
        List<String> opts = listOptions(e.path);
        if (opts.isEmpty()) {
            startEdit(e);
            return;
        }
        String cur = safeGet(e);
        int i = opts.indexOf(cur);
        String next = opts.get((i + 1) % opts.size());
        writeEntry(e, next);
    }

    /** {@code dir} +1 next preset, -1 previous (Ctrl+wheel). */
    private void cycleNumber(Entry e, int dir) {
        List<Integer> opts = numberOptions(e.path);
        int cur;
        try {
            cur = Integer.parseInt(safeGet(e).trim());
        } catch (NumberFormatException ex) {
            cur = opts.get(0);
        }
        int nearest = nearest(opts, cur);
        int i = opts.indexOf(nearest);
        int step = dir >= 0 ? 1 : -1;
        int next = opts.get(Math.floorMod(i + step, opts.size()));
        writeEntry(e, Integer.toString(next));
    }

    private void startEdit(Entry e) {
        commitValueBox();
        clearEditDraft();
        this.editingPath = e.path;
        rebuildUi();
    }

    private void openModelPicker() {
        commitValueBox();
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ModelPickerScreen(this));
        }
    }

    private static List<String> listOptions(String path) {
        return switch (path) {
            case "llm.mode" -> List.of("auto", "cloud", "ollama", "offline");
            case "llm.askNativeTools" -> List.of("auto", "force", "off");
            case "ui.askPurposeOrder" -> List.of("purpose_first", "ingredient_first");
            case "ui.guidebookScope" -> List.of("same_mod", "any_mod");
            case "ui.preferObtain" -> List.of("craft", "quest", "loot", "balanced");
            case "ui.recipeCardsMode" -> List.of("keywords", "ai", "always", "never");
            case "ui.recipeBackend" -> List.of("auto", "jei", "emi");
            case "ui.sidebarSide" -> List.of("right", "left");
            case "ui.ingredientNbtPolicy" -> List.of("auto", "always", "never");
            default -> List.of();
        };
    }

    private static List<Integer> numberOptions(String path) {
        return switch (path) {
            case "token.maxJeiChars" -> JEI_CHARS;
            case "token.historyTurns" -> HISTORY_TURNS;
            case "token.maxFacts" -> MAX_FACTS;
            case "ui.knowledgeCacheMaxMb" -> CACHE_MB;
            case "llm.askTraceKeepFiles" -> TRACE_KEEP;
            case "llm.traceKeepDays" -> TRACE_DAYS;
            case "llm.askMaxToolRounds" -> TOOL_ROUNDS;
            case "llm.dailyTokenLimit" -> DAILY_TOKENS;
            case "ui.packIndexClipRadius" -> CLIP_RADII;
            case "ui.recipeCardsPerItem", "ui.recipeCardsPerItemUse" -> RECIPE_CARDS;
            default -> List.of(0, 1, 2, 4, 8, 16, 32, 64);
        };
    }

    private static int nearest(List<Integer> options, int current) {
        int best = options.get(0);
        int bestDist = Integer.MAX_VALUE;
        for (int option : options) {
            int dist = Math.abs(option - current);
            if (dist < bestDist) {
                bestDist = dist;
                best = option;
            }
        }
        return best;
    }

    private static boolean parseBool(String v) {
        if (v == null) {
            return false;
        }
        String t = v.trim().toLowerCase(Locale.ROOT);
        return "true".equals(t) || "1".equals(t) || "on".equals(t) || "yes".equals(t);
    }

    // --- Reset (never clears api keys / tavily / serper) ---

    private void resetPage() {
        if (this.jeiCatsMode) {
            PackAiConfig.resetRecipeCategoryPrefs();
            this.dragFrom = -1;
            reloadJeiRows();
            ModelCatalog.invalidate();
            return;
        }
        for (Entry e : SettingsRegistry.byCategory(this.category)) {
            resetEntry(e);
        }
        ModelCatalog.invalidate();
        this.editingPath = null;
        clearEditDraft();
        rebuildUi();
    }

    private void resetAll() {
        for (Entry e : SettingsRegistry.all()) {
            resetEntry(e);
        }
        PackAiConfig.resetRecipeCategoryPrefs();
        ModelCatalog.invalidate();
        this.editingPath = null;
        clearEditDraft();
        this.jeiCatsMode = false;
        rebuildUi();
    }

    private void resetEntry(Entry e) {
        if (e == null || SECRET_PATHS.contains(e.path)) {
            return;
        }
        if (JEI_CATS_PATH.equals(e.path)) {
            PackAiConfig.resetRecipeCategoryPrefs();
            return;
        }
        String def = defaultFor(e);
        if (def != null) {
            e.setter.accept(def);
        }
    }

    /** Defaults from Forge ConfigValue — secrets never reset here. */
    private static String defaultFor(Entry e) {
        return switch (e.path) {
            case "llm.mode" -> str(PackAiConfig.MODE.getDefault());
            case "llm.apiBaseUrl" -> str(PackAiConfig.API_BASE_URL.getDefault());
            case "llm.model" -> str(PackAiConfig.MODEL.getDefault());
            case "llm.ollamaBaseUrl" -> str(PackAiConfig.OLLAMA_BASE_URL.getDefault());
            case "llm.ollamaModel" -> str(PackAiConfig.OLLAMA_MODEL.getDefault());
            case "web.allowWebSearch" -> Boolean.toString(PackAiConfig.ALLOW_WEB_SEARCH.getDefault());
            case "token.maxJeiChars" -> Integer.toString(PackAiConfig.MAX_JEI_CHARS.getDefault());
            case "token.historyTurns" -> Integer.toString(PackAiConfig.HISTORY_TURNS.getDefault());
            case "token.maxFacts" -> Integer.toString(PackAiConfig.MAX_FACTS.getDefault());
            case "llm.askNativeTools" -> str(PackAiConfig.ASK_NATIVE_TOOLS.getDefault());
            case "ui.askPurposeOrder" -> str(PackAiConfig.ASK_PURPOSE_ORDER.getDefault());
            case "ui.guidebookScope" -> str(PackAiConfig.GUIDEBOOK_SCOPE.getDefault());
            case "ui.guidebookRelatedHop" ->
                    Boolean.toString(PackAiConfig.GUIDEBOOK_RELATED_HOP.getDefault());
            case "ui.preferObtain" -> str(PackAiConfig.PREFER_OBTAIN.getDefault());
            case "ui.recipeCardsPerItem" ->
                    Integer.toString(PackAiConfig.RECIPE_CARDS_PER_ITEM.getDefault());
            case "ui.recipeCardsPerItemUse" ->
                    Integer.toString(PackAiConfig.RECIPE_CARDS_PER_ITEM_USE.getDefault());
            case "ui.recipeCardsMode" -> str(PackAiConfig.RECIPE_CARDS_MODE.getDefault());
            case "ui.recipeCardMirrorCategories" ->
                    str(PackAiConfig.RECIPE_CARD_MIRROR_CATEGORIES.getDefault());
            case "ui.modularToolSingleItem" ->
                    Boolean.toString(PackAiConfig.MODULAR_TOOL_SINGLE_ITEM.getDefault());
            case "ui.recipeBackend" -> str(PackAiConfig.RECIPE_BACKEND.getDefault());
            case "ui.showHiddenQuests" ->
                    Boolean.toString(PackAiConfig.SHOW_HIDDEN_QUESTS.getDefault());
            case "ui.attachRelatedQuests" ->
                    Boolean.toString(PackAiConfig.ATTACH_RELATED_QUESTS.getDefault());
            case "ui.questMatchHotbar" ->
                    Boolean.toString(PackAiConfig.QUEST_MATCH_HOTBAR.getDefault());
            case "ui.sidebarSide" -> str(PackAiConfig.SIDEBAR_SIDE.getDefault());
            case "llm.showTokenUsage" ->
                    Boolean.toString(PackAiConfig.SHOW_TOKEN_USAGE.getDefault());
            case "ui.ingredientNbtPolicy" -> str(PackAiConfig.INGREDIENT_NBT_POLICY.getDefault());
            case "ui.ingredientTooltipAsReq" ->
                    Boolean.toString(PackAiConfig.INGREDIENT_TOOLTIP_AS_REQ.getDefault());
            case "ui.knowledgeEnabled" ->
                    Boolean.toString(PackAiConfig.KNOWLEDGE_ENABLED.getDefault());
            case "ui.knowledgeRemote" ->
                    Boolean.toString(PackAiConfig.KNOWLEDGE_REMOTE.getDefault());
            case "ui.knowledgeCacheMaxMb" ->
                    Integer.toString(PackAiConfig.KNOWLEDGE_CACHE_MAX_MB.getDefault());
            case "ui.knowledgeUrl" -> str(PackAiConfig.KNOWLEDGE_URL.getDefault());
            case "ui.ingredientNbtSkipPatterns" ->
                    str(PackAiConfig.INGREDIENT_NBT_SKIP_PATTERNS.getDefault());
            case "ui.ingredientNbtKeepPatterns" ->
                    str(PackAiConfig.INGREDIENT_NBT_KEEP_PATTERNS.getDefault());
            case "llm.logFullPrompt" -> Boolean.toString(PackAiConfig.LOG_FULL_PROMPT.getDefault());
            case "llm.askTraceJsonl" -> Boolean.toString(PackAiConfig.ASK_TRACE_JSONL.getDefault());
            case "llm.askTraceKeepFiles" ->
                    Integer.toString(PackAiConfig.ASK_TRACE_KEEP_FILES.getDefault());
            case "llm.traceKeepDays" ->
                    Integer.toString(PackAiConfig.ASK_TRACE_KEEP_DAYS.getDefault());
            case "llm.askMaxToolRounds" ->
                    Integer.toString(PackAiConfig.ASK_MAX_TOOL_ROUNDS.getDefault());
            case "llm.dailyTokenLimit" ->
                    Integer.toString(PackAiConfig.DAILY_TOKEN_LIMIT.getDefault());
            case "ui.scanModJars" -> Boolean.toString(PackAiConfig.SCAN_MOD_JARS.getDefault());
            case "ui.unpackStoredItems" ->
                    Boolean.toString(PackAiConfig.UNPACK_STORED_ITEMS.getDefault());
            case "ui.packIndexClipRadius" ->
                    Integer.toString(PackAiConfig.PACK_INDEX_CLIP_RADIUS.getDefault());
            default -> null;
        };
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    // --- JEI category editor ---

    private void reloadJeiRows() {
        this.jeiAllRows = new ArrayList<>(JeiCategoryCatalog.rowsForUi());
        applyJeiFilter();
    }

    private void applyJeiFilter() {
        String query = this.searchQuery.trim().toLowerCase(Locale.ROOT);
        List<Integer> next = new ArrayList<>();
        for (int i = 0; i < this.jeiAllRows.size(); i++) {
            JeiCategoryCatalog.Row row = this.jeiAllRows.get(i);
            if (query.isEmpty()
                    || row.title().toLowerCase(Locale.ROOT).contains(query)
                    || row.uid().toLowerCase(Locale.ROOT).contains(query)) {
                next.add(i);
            }
        }
        this.jeiFilteredIdx = next;
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll());
    }

    private void persistJei() {
        JeiCategoryCatalog.saveRows(this.jeiAllRows);
    }

    private void toggleJeiAt(int allIndex) {
        if (allIndex < 0 || allIndex >= this.jeiAllRows.size()) {
            return;
        }
        JeiCategoryCatalog.Row row = this.jeiAllRows.get(allIndex);
        this.jeiAllRows.set(allIndex, row.withEnabled(!row.enabled()));
        persistJei();
    }

    private void moveJeiRow(int from, int insertBefore) {
        if (from < 0 || from >= this.jeiAllRows.size()) {
            return;
        }
        int to = Mth.clamp(insertBefore, 0, this.jeiAllRows.size());
        if (from == to || from + 1 == to) {
            return;
        }
        JeiCategoryCatalog.Row row = this.jeiAllRows.remove(from);
        if (from < to) {
            to--;
        }
        to = Mth.clamp(to, 0, this.jeiAllRows.size());
        this.jeiAllRows.add(to, row);
        persistJei();
        applyJeiFilter();
    }

    private int jeiInsertIndexAt(double mouseY) {
        double rel = (mouseY - this.layout.entryList.y) / (double) SettingsLayout.ROW_H
                + this.scrollOffset;
        int idx = (int) Math.floor(rel + 0.5D);
        return Mth.clamp(idx, 0, this.jeiAllRows.size());
    }

    private boolean jeiSearchBlank() {
        return this.searchQuery == null || this.searchQuery.isBlank();
    }

    // --- Knowledge (KB-2) off render thread ---

    private void testKnowledgeConnection() {
        if (this.knowledgeTestBusy) {
            return;
        }
        commitValueBox();
        this.knowledgeTestBusy = true;
        this.status = Component.translatable("packai.settings.knowledge_test.busy").getString();
        String base = PackAiConfig.knowledgeUrl();
        boolean enabled = PackAiConfig.knowledgeEnabled();
        boolean remote = PackAiConfig.knowledgeRemote();
        CompletableFuture.supplyAsync(() -> KnowledgeRemote.testConnection(enabled, remote, base))
                .whenComplete((result, err) -> {
                    if (this.minecraft == null) {
                        return;
                    }
                    this.minecraft.execute(() -> {
                        this.knowledgeTestBusy = false;
                        if (err != null || result == null) {
                            this.status = Component.translatable(
                                            "packai.settings.knowledge_test.no_network")
                                    .getString();
                        } else {
                            String key = switch (result) {
                                case OK -> "packai.settings.knowledge_test.ok";
                                case DISABLED -> "packai.settings.knowledge_test.disabled";
                                case NO_NETWORK -> "packai.settings.knowledge_test.no_network";
                            };
                            this.status = Component.translatable(key).getString();
                        }
                    });
                });
    }

    private void clearKnowledgeCache() {
        if (this.knowledgeTestBusy) {
            return;
        }
        var mc = this.minecraft;
        if (mc == null || mc.gameDirectory == null) {
            this.status = Component.translatable("packai.settings.knowledge_clear_cache.fail")
                    .getString();
            return;
        }
        this.knowledgeTestBusy = true;
        this.status = Component.translatable("packai.settings.knowledge_test.busy").getString();
        Path cache = KnowledgeStore.cacheDir(mc.gameDirectory.toPath());
        CompletableFuture.supplyAsync(() -> {
            try {
                if (Files.isDirectory(cache)) {
                    try (DirectoryStream<Path> ds = Files.newDirectoryStream(cache)) {
                        for (Path p : ds) {
                            Files.deleteIfExists(p);
                        }
                    }
                }
                KnowledgeStore.invalidate();
                return Boolean.TRUE;
            } catch (Exception e) {
                return Boolean.FALSE;
            }
        }).whenComplete((ok, err) -> {
            if (this.minecraft == null) {
                return;
            }
            this.minecraft.execute(() -> {
                this.knowledgeTestBusy = false;
                boolean pass = err == null && Boolean.TRUE.equals(ok);
                this.status = Component.translatable(
                                pass
                                        ? "packai.settings.knowledge_clear_cache.ok"
                                        : "packai.settings.knowledge_clear_cache.fail")
                        .getString();
            });
        });
    }

    @Override
    public void onClose() {
        // Persist TEXT/secret drafts; never write placeholder back.
        commitValueBox();
        // §4: auto-save apiKey / baseUrl drafts when box already torn down.
        String key = this.draftApiKey;
        if (key != null && !SettingsRegistry.isPlaceholderValue(key)) {
            PackAiConfig.setApiKey(key);
        }
        String baseRaw = this.draftBaseUrl;
        if (baseRaw != null && !SettingsRegistry.isPlaceholderValue(baseRaw)) {
            String base = LlmClient.normalizeApiBaseUrl(baseRaw);
            if (!base.isEmpty()) {
                PackAiConfig.setApiBaseUrl(base);
            }
        }
        if (this.parent instanceof AiAssistantScreen ai) {
            ai.reloadLayout();
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    /**
     * Draw order contract (C-0): custom chrome under widgets; tips last.
     * Fallback keeps widgets under the too_small message (layout is full-screen).
     * When widgets sit inside a custom-painted zone, never paint that zone after
     * {@code super.render} / tips — see plan {@code 2026-09-15_settings-c-batch.md} §1.
     */
    private void renderScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics.pose());
        if (this.layout == null) {
            this.layout = SettingsLayout.compute(this.width, this.height);
        }
        if (!settingsLayoutLogged && this.layout != null && !this.layout.fallback) {
            settingsLayoutLogged = true;
            PackAiMod.LOGGER.info(
                    "Pack AI settingsLayout screenH={} descAsOverlay={} entryListH={} descY={} maxRows={}",
                    this.height,
                    this.layout.descAsOverlay,
                    this.layout.entryList.h,
                    this.layout.descPanel.y,
                    this.layout.maxVisibleEntryRows);
        }
        // Scroll changes offset without mouseMoved — recompute hover each frame (Plan A D3).
        updateHoverHighlight(mouseX, mouseY);
        GuiShell.nestedShell(graphics, this.width, this.height);

        if (this.layout.fallback) {
            super.render(graphics.pose(), mouseX, mouseY, partialTick);
            GuiShell.mutedCentered(
                    graphics,
                    this.font,
                    Component.translatable("packai.settings.v2.too_small"),
                    this.width / 2,
                    this.height / 2);
            WidgetCompat.renderHoveredTips(this, graphics.pose(), mouseX, mouseY);
            return;
        }

        GuiShell.title(
                graphics,
                this.font,
                this.jeiCatsMode
                        ? Component.translatable("packai.recipe_cats.title")
                        : this.title,
                this.width / 2,
                6);

        if (!this.jeiCatsMode) {
            renderCategoryCol(graphics, mouseX, mouseY);
        }

        GuiShell.panel(
                graphics,
                this.layout.entryList.x - 2,
                this.layout.entryList.y - 2,
                this.layout.entryList.right() + 2,
                this.layout.entryList.bottom() + 2,
                GuiShell.FILL_PRIMARY,
                GuiShell.BORDER_SOFT);

        if (this.jeiCatsMode) {
            renderJeiList(graphics, mouseX, mouseY);
        } else {
            renderEntryList(graphics, mouseX, mouseY);
            renderDesc(graphics, mouseX, mouseY);
        }

        if (this.status != null && !this.status.isBlank()) {
            int statusY = this.layout.descAsOverlay
                    ? this.height - 40
                    : Math.max(this.layout.entryList.bottom() + 2, this.layout.descPanel.y - 12);
            GuiShell.statusOk(graphics, this.font, this.status, this.width / 2, statusY);
        }

        super.render(graphics.pose(), mouseX, mouseY, partialTick);
        WidgetCompat.renderHoveredTips(this, graphics.pose(), mouseX, mouseY);
    }

    private void renderCategoryCol(GuiGraphics graphics, int mouseX, int mouseY) {
        GuiShell.panel(
                graphics,
                this.layout.categoryCol.x - 2,
                this.layout.categoryCol.y - 2,
                this.layout.categoryCol.right() + 2,
                this.layout.categoryCol.bottom() + 2,
                GuiShell.FILL_SECONDARY,
                GuiShell.BORDER_SOFT);
        UiCategory[] cats = UiCategory.values();
        for (int i = 0; i < this.layout.categoryRows.size() && i < cats.length; i++) {
            Rect r = this.layout.categoryRows.get(i);
            UiCategory cat = cats[i];
            boolean sel = cat == this.category;
            boolean hover = mouseX >= r.x && mouseX < r.right() && mouseY >= r.y && mouseY < r.bottom();
            if (sel) {
                graphics.fill(r.x, r.y, r.right(), r.bottom(), 0x664488FF);
            } else if (hover) {
                graphics.fill(r.x, r.y, r.right(), r.bottom(), 0x33FFFFFF);
            }
            graphics.drawString(
                    this.font,
                    Component.translatable(cat.labelKey),
                    r.x + 4,
                    r.y + 5,
                    sel ? GuiShell.TITLE : GuiShell.MUTED,
                    false);
        }
    }

    private void renderEntryList(GuiGraphics graphics, int mouseX, int mouseY) {
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll());
        List<Rect> rows = SettingsLayout.entryRowRects(this.layout, visibleRows());
        int end = Math.min(this.filtered.size(), this.scrollOffset + rows.size());
        for (int i = this.scrollOffset; i < end; i++) {
            Rect r = rows.get(i - this.scrollOffset);
            Entry e = this.filtered.get(i);
            boolean hi = i == this.highlightIndex;
            boolean hover = mouseX >= r.x && mouseX < r.right() && mouseY >= r.y && mouseY < r.bottom();
            if (hi) {
                graphics.fill(r.x, r.y, r.right(), r.bottom(), 0x664488FF);
            } else if (hover) {
                graphics.fill(r.x, r.y, r.right(), r.bottom(), 0x33FFFFFF);
            }
            String label = Component.translatable(e.labelKey).getString();
            int labelCap = Math.max(24, (int) (r.w * 0.55));
            int labelW = Math.min(this.font.width(label), labelCap);
            String drawLabel = fitWidth(label, labelCap);
            graphics.drawString(this.font, drawLabel, r.x + 4, r.y + 5, GuiShell.TITLE, false);

            if (e.type == ControlType.TOGGLE) {
                boolean on = parseBool(safeGet(e));
                int tx = r.right() - 16;
                int ty = r.y + 3;
                graphics.fill(tx, ty, tx + 12, ty + 12, on ? 0xFF55AA55 : 0xFF555555);
                graphics.fill(tx + 1, ty + 1, tx + 11, ty + 11, on ? 0xFF88FF88 : 0xFF333333);
                if (on) {
                    graphics.drawString(this.font, "X", tx + 3, ty + 1, 0x003300, false);
                }
            } else if (!(e.path.equals(this.editingPath) && this.valueBox != null)) {
                String summary = valueSummary(e);
                int valueMax = Math.max(0, r.w - labelW - 12);
                summary = fitWidth(summary, valueMax);
                if (!summary.isEmpty()) {
                    int sw = this.font.width(summary);
                    graphics.drawString(
                            this.font, summary, r.right() - sw - 4, r.y + 5, GuiShell.MUTED, false);
                }
            }
        }
        if (this.filtered.isEmpty()) {
            GuiShell.mutedCentered(
                    graphics,
                    this.font,
                    Component.translatable("packai.settings.v2.empty"),
                    this.layout.entryList.x + this.layout.entryList.w / 2,
                    this.layout.entryList.y + this.layout.entryList.h / 2);
        }
    }

    private void renderJeiList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!JeiCategoryCatalog.jeiAvailable()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.translatable("packai.recipe_cats.no_jei"),
                    this.layout.entryList.x + this.layout.entryList.w / 2,
                    this.layout.entryList.y + this.layout.entryList.h / 2,
                    0xFFAAAA);
            return;
        }
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll());
        List<Rect> rows = SettingsLayout.entryRowRects(this.layout, visibleRows());
        int end = Math.min(this.jeiFilteredIdx.size(), this.scrollOffset + rows.size());
        for (int fi = this.scrollOffset; fi < end; fi++) {
            Rect r = rows.get(fi - this.scrollOffset);
            int allIndex = this.jeiFilteredIdx.get(fi);
            JeiCategoryCatalog.Row row = this.jeiAllRows.get(allIndex);
            boolean dragging = this.dragFrom == allIndex;
            boolean hover = !dragging
                    && mouseX >= r.x
                    && mouseX < r.right()
                    && mouseY >= r.y
                    && mouseY < r.bottom();
            if (dragging) {
                graphics.fill(r.x, r.y, r.right(), r.bottom(), 0x664488FF);
            } else if (hover) {
                graphics.fill(r.x, r.y, r.right(), r.bottom(), 0x33FFFFFF);
            }
            if (this.dragFrom >= 0 && this.dragHoverInsert == allIndex) {
                graphics.fill(r.x, r.y, r.right(), r.y + 2, 0xFFE0E0E0);
            }
            if (this.dragFrom >= 0
                    && this.dragHoverInsert == this.jeiAllRows.size()
                    && fi == end - 1) {
                graphics.fill(r.x, r.bottom() - 2, r.right(), r.bottom(), 0xFFE0E0E0);
            }

            int tx = r.x + 4;
            int ty = r.y + 3;
            graphics.fill(tx, ty, tx + 12, ty + 12, row.enabled() ? 0xFF55AA55 : 0xFF555555);
            graphics.fill(tx + 1, ty + 1, tx + 11, ty + 11, row.enabled() ? 0xFF88FF88 : 0xFF333333);
            if (row.enabled()) {
                graphics.drawString(this.font, "X", tx + 3, ty + 1, 0x003300, false);
            }
            graphics.drawString(this.font, "::", r.x + TOGGLE_W + 2, r.y + 5, 0xAAAAAA, false);

            int textLeft = r.x + TOGGLE_W + HANDLE_W + 4;
            int color = row.enabled() ? 0xE0E0E0 : 0x777777;
            String label = row.title();
            int maxW = r.right() - textLeft - 8;
            if (this.font.width(label) > maxW) {
                label = this.font.plainSubstrByWidth(label, maxW - 8) + "...";
            }
            graphics.drawString(this.font, label, textLeft, r.y + 5, color, false);
        }
        if (this.jeiAllRows.isEmpty()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.translatable("packai.recipe_cats.empty"),
                    this.layout.entryList.x + this.layout.entryList.w / 2,
                    this.layout.entryList.y + this.layout.entryList.h / 2,
                    0xAAAAAA);
        } else if (this.jeiFilteredIdx.isEmpty()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.translatable("packai.recipe_cats.no_match"),
                    this.layout.entryList.x + this.layout.entryList.w / 2,
                    this.layout.entryList.y + this.layout.entryList.h / 2,
                    0xAAAAAA);
        }
    }

    private void renderDesc(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean showDesc = !this.layout.descAsOverlay
                || (mouseX >= this.layout.entryList.x
                        && mouseX < this.layout.entryList.right()
                        && mouseY >= this.layout.entryList.y
                        && mouseY < this.layout.entryList.bottom());
        if (showDesc
                && !this.filtered.isEmpty()
                && this.highlightIndex >= 0
                && this.highlightIndex < this.filtered.size()) {
            Entry hi = this.filtered.get(this.highlightIndex);
            Rect d = this.layout.descPanel;
            GuiShell.panel(
                    graphics,
                    d.x - 2,
                    d.y - 2,
                    d.right() + 2,
                    d.bottom() + 2,
                    GuiShell.FILL_BODY,
                    GuiShell.BORDER_SOFT);
            String title = Component.translatable(hi.labelKey).getString();
            graphics.drawString(
                    this.font, fitWidth(title, d.w - 8), d.x + 4, d.y + 3, GuiShell.ACCENT, false);
            List<FormattedCharSequence> body = wrapTooltipBody(hi.tooltipKey, d.w - 8);
            int lineH = this.font.lineHeight;
            int y = d.y + 3 + lineH + 2;
            int shown = Math.min(DESC_BODY_MAX_LINES, body.size());
            for (int i = 0; i < shown; i++) {
                graphics.drawString(this.font, body.get(i), d.x + 4, y, GuiShell.MUTED, false);
                y += lineH;
            }
            if (body.size() > DESC_BODY_MAX_LINES) {
                String hint = Component.translatable("packai.settings.desc_shift_hint").getString();
                graphics.drawString(
                        this.font, fitWidth(hint, d.w - 8), d.x + 4, y, GuiShell.MUTED, false);
            }
        }
    }

    /** Split tooltip by paragraphs then wrap; preserves {@code \n}. */
    private List<FormattedCharSequence> wrapTooltipBody(String tipKey, int wrapWidth) {
        String tip = Component.translatable(tipKey).getString();
        if (tip == null || tip.isBlank()) {
            return List.of();
        }
        List<FormattedCharSequence> out = new ArrayList<>();
        int w = Math.max(40, wrapWidth);
        for (String para : tip.split("\n", -1)) {
            if (para.isEmpty()) {
                out.add(FormattedCharSequence.EMPTY);
                continue;
            }
            out.addAll(this.font.split(Component.literal(para), w));
        }
        return out;
    }

    @Override
    public List<FormattedCharSequence> shiftTipLines(int mouseX, int mouseY) {
        if (this.layout == null
                || this.layout.fallback
                || this.filtered.isEmpty()
                || this.highlightIndex < 0
                || this.highlightIndex >= this.filtered.size()) {
            return List.of();
        }
        Entry hi = this.filtered.get(this.highlightIndex);
        int wrap = Math.max(200, this.width / 2);
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable(hi.labelKey).getVisualOrderText());
        lines.addAll(wrapTooltipBody(hi.tooltipKey, wrap));
        return lines;
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderScreen(new GuiGraphics(this.minecraft, this, pose), mouseX, mouseY, partialTick);
    }

    /** Hover highlight only — never writes config (Plan A S6). */
    private void updateHoverHighlight(double mouseX, double mouseY) {
        if (this.layout == null || this.layout.fallback || this.jeiCatsMode || this.filtered.isEmpty()) {
            return;
        }
        if (mouseX < this.layout.entryList.x
                || mouseX >= this.layout.entryList.right()
                || mouseY < this.layout.entryList.y
                || mouseY >= this.layout.entryList.bottom()) {
            return;
        }
        int row = this.scrollOffset
                + (int) ((mouseY - this.layout.entryList.y) / SettingsLayout.ROW_H);
        if (row >= 0 && row < this.filtered.size() && row < this.scrollOffset + visibleRows()) {
            this.highlightIndex = row;
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        updateHoverHighlight(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Widgets first (valueBox / search / Done): vanilla focus + caret. Editing-row right
        // half no longer goes through activateRow→commit+rebuild (A-4); draft still commits
        // onClose / next startEdit / category change.
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && this.layout != null && !this.layout.fallback) {
            if (!this.jeiCatsMode) {
                for (int i = 0; i < this.layout.categoryRows.size(); i++) {
                    Rect r = this.layout.categoryRows.get(i);
                    if (mouseX >= r.x && mouseX < r.right() && mouseY >= r.y && mouseY < r.bottom()) {
                        selectCategory(UiCategory.values()[i]);
                        return true;
                    }
                }
            }
            if (mouseX >= this.layout.entryList.x
                    && mouseX < this.layout.entryList.right()
                    && mouseY >= this.layout.entryList.y
                    && mouseY < this.layout.entryList.bottom()) {
                if (this.jeiCatsMode) {
                    return mouseClickedJei(mouseX, mouseY);
                }
                int row = this.scrollOffset
                        + (int) ((mouseY - this.layout.entryList.y) / SettingsLayout.ROW_H);
                if (row >= 0 && row < this.filtered.size()) {
                    this.highlightIndex = row;
                    activateRow(this.filtered.get(row));
                    return true;
                }
            }
        }
        return false;
    }

    private boolean mouseClickedJei(double mouseX, double mouseY) {
        if (!JeiCategoryCatalog.jeiAvailable()) {
            return true;
        }
        int row = this.scrollOffset
                + (int) ((mouseY - this.layout.entryList.y) / SettingsLayout.ROW_H);
        if (row >= 0 && row < this.jeiFilteredIdx.size()) {
            int allIndex = this.jeiFilteredIdx.get(row);
            double localX = mouseX - this.layout.entryList.x;
            if (localX < TOGGLE_W) {
                toggleJeiAt(allIndex);
                return true;
            }
            if (jeiSearchBlank()) {
                this.dragFrom = allIndex;
                this.dragHoverInsert = allIndex;
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.jeiCatsMode && this.dragFrom >= 0 && jeiSearchBlank() && this.layout != null) {
            if (mouseY < this.layout.entryList.y + 8 && this.scrollOffset > 0) {
                this.scrollOffset--;
            } else if (mouseY > this.layout.entryList.bottom() - 8 && this.scrollOffset < maxScroll()) {
                this.scrollOffset++;
            }
            this.dragHoverInsert = jeiInsertIndexAt(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.jeiCatsMode && this.dragFrom >= 0) {
            int from = this.dragFrom;
            int insertBefore =
                    this.dragHoverInsert >= 0 ? this.dragHoverInsert : jeiInsertIndexAt(mouseY);
            this.dragFrom = -1;
            this.dragHoverInsert = -1;
            moveJeiRow(from, insertBefore);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (this.layout != null
                && mouseX >= this.layout.entryList.x - 4
                && mouseX <= this.layout.entryList.right() + 4
                && mouseY >= this.layout.entryList.y
                && mouseY <= this.layout.entryList.bottom()) {
            // Ctrl+wheel: cycle NUMBER presets; plain wheel only scrolls (Plan A S7).
            if (!this.jeiCatsMode && Screen.hasControlDown()) {
                int row = this.scrollOffset
                        + (int) ((mouseY - this.layout.entryList.y) / SettingsLayout.ROW_H);
                if (row >= 0 && row < this.filtered.size()) {
                    Entry e = this.filtered.get(row);
                    if (e.type == ControlType.NUMBER) {
                        this.highlightIndex = row;
                        cycleNumber(e, scrollDelta > 0 ? -1 : 1);
                        return true;
                    }
                }
            }
            this.scrollOffset = Mth.clamp(
                    this.scrollOffset - (int) Math.signum(scrollDelta), 0, maxScroll());
            if (this.editingPath != null && !this.jeiCatsMode) {
                rebuildUiPreservingDraft();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

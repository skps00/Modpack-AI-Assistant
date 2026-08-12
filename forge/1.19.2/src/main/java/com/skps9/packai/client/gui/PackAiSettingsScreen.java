package com.skps9.packai.client.gui;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.client.service.AskService;
import com.skps9.packai.logic.AskEngine;
import com.skps9.packai.logic.JarLightIndex;
import com.skps9.packai.logic.LlmClient;
import com.skps9.packai.logic.ModelCatalog;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Mods -> Pack AI settings. Four Sodium-style tabs so 480p fits without stacking all rows.
 */
public class PackAiSettingsScreen extends Screen {
    private enum Tab {
        CONNECTION, ASK, RECIPES, QUESTS
    }

    private static final List<String> MODES = List.of("auto", "cloud", "ollama", "offline");
    private static final List<String> SIDEBARS = List.of("right", "left");
    private static final List<String> PREFER_OBTAINS = List.of("craft", "quest", "loot", "balanced");
    private static final List<String> RECIPE_CARDS_MODES = List.of("keywords", "ai", "always", "never");
    private static final List<String> INGREDIENT_NBT_POLICIES = List.of("auto", "always", "never");
    private static final List<Integer> JEI_CHARS = List.of(2000, 4000, 8000, 12000);
    private static final List<Integer> HISTORY_TURNS = List.of(0, 2, 4, 8, 12, 16);
    private static final List<Integer> MAX_FACTS = List.of(4, 8, 12, 16, 24, 32);
    private static final List<Integer> CLIP_RADII = List.of(10, 20, 30, 40, 50);

    private final Screen parent;
    private Tab tab = Tab.CONNECTION;
    private EditBox apiKeyBox;
    private EditBox baseUrlBox;
    /** Survive tab rebuilds when Connection widgets are torn down. */
    private String draftApiKey;
    private String draftBaseUrl;
    private String status = "";
    private boolean autoRefreshScheduled;
    private int shellLeft;
    private int shellTop;
    private int shellRight;
    private int shellBottom;
    private int activeTabX;
    private int activeTabW;
    private int tabBarY;

    public PackAiSettingsScreen(Screen parent) {
        super(Component.translatable("packai.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(420, this.width - 40);
        int left = (this.width - w) / 2;
        int y = 28;

        this.shellLeft = left - 8;
        this.shellRight = left + w + 8;
        this.shellTop = 22;
        this.shellBottom = this.height - 32;
        this.tabBarY = y;

        int tabW = (w - 12) / 4;
        addTabButton(left, y, tabW, Tab.CONNECTION, "packai.settings.tab.connection");
        addTabButton(left + tabW + 4, y, tabW, Tab.ASK, "packai.settings.tab.ask");
        addTabButton(left + 2 * (tabW + 4), y, tabW, Tab.RECIPES, "packai.settings.tab.recipes");
        addTabButton(left + 3 * (tabW + 4), y, tabW, Tab.QUESTS, "packai.settings.tab.quests");

        y += 28;
        int half = w / 2 - 4;

        switch (this.tab) {
            case CONNECTION -> initConnection(left, y, w, half);
            case ASK -> initAsk(left, y, w, half);
            case RECIPES -> initRecipes(left, y, w, half);
            case QUESTS -> initQuests(left, y, w, half);
        }

        int doneY = this.height - 28;
        this.addRenderableWidget(WidgetCompat.button(left + half + 8, doneY, half, 20,
                Component.translatable("gui.done"), b -> onClose(),
                Component.translatable("packai.settings.tooltip.done")));

        if (!this.autoRefreshScheduled) {
            this.autoRefreshScheduled = true;
            ModelCatalog.refreshAsync(() -> {
                if (this.minecraft != null && this.minecraft.screen == this) {
                    rebuildUi();
                }
            });
        }
    }

    private void addTabButton(int x, int y, int w, Tab target, String langKey) {
        String tipKey = "packai.settings.tooltip.tab." + target.name().toLowerCase();
        if (this.tab == target) {
            this.activeTabX = x;
            this.activeTabW = w;
        }
        Button btn = WidgetCompat.button(x, y, w, 20, Component.translatable(langKey), b -> {
            this.tab = target;
            rebuildUi();
        }, Component.translatable(tipKey));
        btn.active = this.tab != target;
        this.addRenderableWidget(btn);
    }

    private void initConnection(int left, int y, int w, int half) {
        this.apiKeyBox = WidgetCompat.editBox(left, y, w - 70, 20,
                Component.translatable("packai.screen.api_key"),
                Component.translatable("packai.settings.tooltip.api_key"));
        this.apiKeyBox.setMaxLength(512);
        String key = this.draftApiKey != null ? this.draftApiKey : PackAiConfig.API_KEY.get();
        this.apiKeyBox.setValue(key == null ? "" : key);
        this.apiKeyBox.setFormatter((text, first) ->
                FormattedCharSequence.forward("*".repeat(Math.min(text.length(), 128)), Style.EMPTY));
        this.addRenderableWidget(this.apiKeyBox);
        this.addRenderableWidget(WidgetCompat.button(left + w - 64, y, 64, 20,
                Component.translatable("packai.screen.save_key"), b -> saveApiKey(),
                Component.translatable("packai.settings.tooltip.save_key")));

        y += 22;
        this.baseUrlBox = WidgetCompat.editBox(left, y, w, 20,
                Component.translatable("packai.settings.api_base"),
                Component.translatable("packai.settings.tooltip.api_base"));
        this.baseUrlBox.setMaxLength(256);
        String base = this.draftBaseUrl != null
                ? this.draftBaseUrl
                : PackAiConfig.API_BASE_URL.get();
        this.baseUrlBox.setValue(base == null ? "" : LlmClient.normalizeApiBaseUrl(base));
        this.addRenderableWidget(this.baseUrlBox);

        y += 22;
        this.addRenderableWidget(CycleButton.<String>builder(m -> Component.translatable("packai.screen.mode." + m))
                .withValues(MODES)
                .withInitialValue(PackAiConfig.resolvedMode())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.mode"))
                .create(left, y, half, 20, Component.translatable("packai.screen.mode"),
                        (btn, value) -> {
                            PackAiConfig.setMode(value);
                            rebuildUi();
                            ModelCatalog.refreshAsync(true, () -> {
                                if (this.minecraft != null && this.minecraft.screen == this) {
                                    rebuildUi();
                                }
                            });
                        }));

        int refreshW = 48;
        int modelW = half - refreshW - 4;
        Button modelBtn = WidgetCompat.button(left + half + 8, y, modelW, 20,
                modelButtonLabel(), b -> openModelPicker(),
                Component.translatable("packai.settings.tooltip.model"));
        modelBtn.active = !"offline".equals(PackAiConfig.resolvedMode());
        this.addRenderableWidget(modelBtn);
        Button refreshBtn = WidgetCompat.button(left + half + 8 + modelW + 4, y, refreshW, 20,
                Component.translatable("packai.screen.refresh_models"), b -> refreshModels(),
                Component.translatable("packai.settings.tooltip.refresh_models"));
        refreshBtn.active = !"offline".equals(PackAiConfig.resolvedMode());
        this.addRenderableWidget(refreshBtn);

        y += 22;
        this.addRenderableWidget(WidgetCompat.button(left, y, w, 20,
                Component.translatable("packai.settings.web_search"),
                b -> this.minecraft.setScreen(new WebSearchSettingsScreen(this)),
                Component.translatable("packai.settings.tooltip.web_search")));
    }

    private void initAsk(int left, int y, int w, int half) {
        int third = (w - 8) / 3;
        this.addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(String.valueOf(v)))
                .withValues(JEI_CHARS)
                .withInitialValue(nearest(JEI_CHARS, PackAiConfig.maxJeiChars()))
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.max_jei_chars"))
                .create(left, y, third, 20, Component.translatable("packai.settings.max_jei_chars"),
                        (btn, value) -> PackAiConfig.setMaxJeiChars(value)));
        this.addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(String.valueOf(v)))
                .withValues(HISTORY_TURNS)
                .withInitialValue(nearest(HISTORY_TURNS, PackAiConfig.historyTurns()))
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.history_turns"))
                .create(left + third + 4, y, third, 20, Component.translatable("packai.settings.history_turns"),
                        (btn, value) -> PackAiConfig.setHistoryTurns(value)));
        this.addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(String.valueOf(v)))
                .withValues(MAX_FACTS)
                .withInitialValue(nearest(MAX_FACTS, PackAiConfig.maxFacts()))
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.max_facts"))
                .create(left + 2 * (third + 4), y, third, 20, Component.translatable("packai.settings.max_facts"),
                        (btn, value) -> PackAiConfig.setMaxFacts(value)));

        y += 22;
        this.addRenderableWidget(CycleButton.<String>builder(s -> Component.translatable("packai.settings.sidebar." + s))
                .withValues(SIDEBARS)
                .withInitialValue(PackAiConfig.sidebarSide())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.sidebar"))
                .create(left, y, half, 20, Component.translatable("packai.settings.sidebar"),
                        (btn, value) -> PackAiConfig.setSidebarSide(value)));
        this.addRenderableWidget(CycleButton.<String>builder(
                        s -> Component.translatable("packai.settings.ingredient_nbt." + s))
                .withValues(INGREDIENT_NBT_POLICIES)
                .withInitialValue(PackAiConfig.ingredientNbtPolicy())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.ingredient_nbt"))
                .create(left + half + 8, y, half, 20, Component.translatable("packai.settings.ingredient_nbt"),
                        (btn, value) -> PackAiConfig.setIngredientNbtPolicy(value)));

        y += 22;
        this.addRenderableWidget(CycleButton.<Boolean>builder(
                        v -> Component.translatable(v
                                ? "packai.settings.ingredient_tooltip_req.on"
                                : "packai.settings.ingredient_tooltip_req.off"))
                .withValues(List.of(false, true))
                .withInitialValue(PackAiConfig.ingredientTooltipAsReq())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.ingredient_tooltip_req"))
                .create(left, y, half, 20,
                        Component.translatable("packai.settings.ingredient_tooltip_req"),
                        (btn, value) -> PackAiConfig.setIngredientTooltipAsReq(value)));
        this.addRenderableWidget(CycleButton.<Boolean>builder(
                        v -> Component.translatable(v
                                ? "packai.settings.scan_mod_jars.on"
                                : "packai.settings.scan_mod_jars.off"))
                .withValues(List.of(false, true))
                .withInitialValue(PackAiConfig.scanModJars())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.scan_mod_jars"))
                .create(left + half + 8, y, half, 20,
                        Component.translatable("packai.settings.scan_mod_jars"),
                        (btn, value) -> {
                            PackAiConfig.setScanModJars(value);
                            JarLightIndex.INSTANCE.reset();
                            if (value) {
                                AskService.INSTANCE.warmupAsync();
                            }
                        }));

        y += 22;
        this.addRenderableWidget(CycleButton.<Boolean>builder(
                        v -> Component.translatable(v
                                ? "packai.settings.unpack_stored_items.on"
                                : "packai.settings.unpack_stored_items.off"))
                .withValues(List.of(false, true))
                .withInitialValue(PackAiConfig.unpackStoredItems())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.unpack_stored_items"))
                .create(left, y, half, 20,
                        Component.translatable("packai.settings.unpack_stored_items"),
                        (btn, value) -> PackAiConfig.setUnpackStoredItems(value)));
        this.addRenderableWidget(CycleButton.<String>builder(
                        s -> Component.translatable("packai.settings.recipe_backend." + s))
                .withValues(List.of("auto", "jei", "emi"))
                .withInitialValue(PackAiConfig.recipeBackend())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.recipe_backend"))
                .create(left + half + 8, y, half, 20,
                        Component.translatable("packai.settings.recipe_backend"),
                        (btn, value) -> PackAiConfig.setRecipeBackend(value)));

        y += 22;
        this.addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(String.valueOf(v)))
                .withValues(CLIP_RADII)
                .withInitialValue(nearest(CLIP_RADII, PackAiConfig.packIndexClipRadius()))
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.pack_index_clip_radius"))
                .create(left, y, w, 20,
                        Component.translatable("packai.settings.pack_index_clip_radius"),
                        (btn, value) -> PackAiConfig.setPackIndexClipRadius(value)));

        y += 22;
        this.addRenderableWidget(CycleButton.<Boolean>builder(
                        v -> Component.translatable(v
                                ? "packai.settings.log_full_prompt.on"
                                : "packai.settings.log_full_prompt.off"))
                .withValues(List.of(false, true))
                .withInitialValue(PackAiConfig.logFullPrompt())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.log_full_prompt"))
                .create(left, y, half, 20,
                        Component.translatable("packai.settings.log_full_prompt"),
                        (btn, value) -> PackAiConfig.setLogFullPrompt(value)));
        this.addRenderableWidget(CycleButton.<Boolean>builder(
                        v -> Component.translatable(v
                                ? "packai.settings.show_token_usage.on"
                                : "packai.settings.show_token_usage.off"))
                .withValues(List.of(false, true))
                .withInitialValue(PackAiConfig.showTokenUsage())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.show_token_usage"))
                .create(left + half + 8, y, half, 20,
                        Component.translatable("packai.settings.show_token_usage"),
                        (btn, value) -> PackAiConfig.setShowTokenUsage(value)));
    }

    private void initRecipes(int left, int y, int w, int half) {
        this.addRenderableWidget(CycleButton.<String>builder(s -> Component.translatable("packai.settings.prefer_obtain." + s))
                .withValues(PREFER_OBTAINS)
                .withInitialValue(PackAiConfig.preferObtain())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.prefer_obtain"))
                .create(left, y, w, 20, Component.translatable("packai.settings.prefer_obtain"),
                        (btn, value) -> PackAiConfig.setPreferObtain(value)));

        y += 22;
        this.addRenderableWidget(WidgetCompat.button(left, y, w, 20,
                Component.translatable("packai.settings.recipe_cats"),
                b -> this.minecraft.setScreen(new RecipeCategoryScreen(this)),
                Component.translatable("packai.settings.tooltip.recipe_cats")));

        y += 22;
        this.addRenderableWidget(CycleButton.<Boolean>builder(
                        v -> Component.translatable(v
                                ? "packai.settings.hide_upgrade_recipes.on"
                                : "packai.settings.hide_upgrade_recipes.off"))
                .withValues(List.of(false, true))
                .withInitialValue(PackAiConfig.hideUpgradeRecipes())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.hide_upgrade_recipes"))
                .create(left, y, w, 20,
                        Component.translatable("packai.settings.hide_upgrade_recipes"),
                        (btn, value) -> PackAiConfig.setHideUpgradeRecipes(value)));

        y += 22;
        this.addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(String.valueOf(v)))
                .withValues(List.of(1, 2, 3, 4, 5, 6, 8))
                .withInitialValue(PackAiConfig.recipeCardsPerItem())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.recipe_cards_per_item"))
                .create(left, y, half, 20,
                        Component.translatable("packai.settings.recipe_cards_per_item"),
                        (btn, value) -> PackAiConfig.setRecipeCardsPerItem(value)));
        this.addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(String.valueOf(v)))
                .withValues(List.of(1, 2, 3, 4, 5, 6, 8))
                .withInitialValue(PackAiConfig.recipeCardsPerItemUse())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.recipe_cards_per_item_use"))
                .create(left + half + 8, y, half, 20,
                        Component.translatable("packai.settings.recipe_cards_per_item_use"),
                        (btn, value) -> PackAiConfig.setRecipeCardsPerItemUse(value)));

        y += 22;
        this.addRenderableWidget(CycleButton.<String>builder(
                        s -> Component.translatable("packai.settings.recipe_cards_mode." + s))
                .withValues(RECIPE_CARDS_MODES)
                .withInitialValue(PackAiConfig.recipeCardsMode())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.recipe_cards_mode"))
                .create(left, y, w, 20,
                        Component.translatable("packai.settings.recipe_cards_mode"),
                        (btn, value) -> PackAiConfig.setRecipeCardsMode(value)));
    }

    private void initQuests(int left, int y, int w, int half) {
        this.addRenderableWidget(CycleButton.<Boolean>builder(
                        v -> Component.translatable(v
                                ? "packai.settings.show_hidden_quests.on"
                                : "packai.settings.show_hidden_quests.off"))
                .withValues(List.of(false, true))
                .withInitialValue(PackAiConfig.showHiddenQuests())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.show_hidden_quests"))
                .create(left, y, half, 20,
                        Component.translatable("packai.settings.show_hidden_quests"),
                        (btn, value) -> {
                            PackAiConfig.setShowHiddenQuests(value);
                            // Drop cached graph edges that may still name spoiler quests.
                            AskEngine.INSTANCE.invalidateIndexes();
                        }));
        this.addRenderableWidget(CycleButton.<Boolean>builder(
                        v -> Component.translatable(v
                                ? "packai.settings.attach_quests.on"
                                : "packai.settings.attach_quests.off"))
                .withValues(List.of(false, true))
                .withInitialValue(PackAiConfig.attachRelatedQuests())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.attach_quests"))
                .create(left + half + 8, y, half, 20,
                        Component.translatable("packai.settings.attach_quests"),
                        (btn, value) -> PackAiConfig.setAttachRelatedQuests(value)));

        y += 22;
        this.addRenderableWidget(CycleButton.<Boolean>builder(
                        v -> Component.translatable(v
                                ? "packai.settings.quest_match_hotbar.on"
                                : "packai.settings.quest_match_hotbar.off"))
                .withValues(List.of(false, true))
                .withInitialValue(PackAiConfig.questMatchHotbar())
                .withTooltip(v -> WidgetCompat.tipLines("packai.settings.tooltip.quest_match_hotbar"))
                .create(left, y, w, 20,
                        Component.translatable("packai.settings.quest_match_hotbar"),
                        (btn, value) -> PackAiConfig.setQuestMatchHotbar(value)));
    }

    private void rebuildUi() {
        if (this.apiKeyBox != null) {
            this.draftApiKey = this.apiKeyBox.getValue();
        }
        if (this.baseUrlBox != null) {
            this.draftBaseUrl = this.baseUrlBox.getValue();
        }
        this.clearWidgets();
        this.init();
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

    private Component modelButtonLabel() {
        String model = PackAiConfig.uiModel();
        if (model.length() > 18) {
            model = model.substring(0, 16) + "...";
        }
        return Component.translatable("packai.screen.pick_model", model);
    }

    private void openModelPicker() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ModelPickerScreen(this));
        }
    }

    private void refreshModels() {
        this.status = Component.translatable("packai.status.models_refreshing").getString();
        ModelCatalog.invalidate();
        ModelCatalog.refreshAsync(true, () -> {
            if (this.minecraft != null && this.minecraft.screen == this) {
                this.status = Component.translatable("packai.status.models_refreshed").getString();
                rebuildUi();
            }
        });
    }

    private void saveApiKey() {
        if (this.apiKeyBox == null) {
            return;
        }
        PackAiConfig.setApiKey(this.apiKeyBox.getValue());
        this.apiKeyBox.setValue(PackAiConfig.API_KEY.get());
        ModelCatalog.invalidate();
        this.status = Component.translatable("packai.status.key_saved", PackAiConfig.API_KEY.get().length()).getString();
        ModelCatalog.refreshAsync(true, () -> {
            if (this.minecraft != null && this.minecraft.screen == this) {
                rebuildUi();
            }
        });
    }

    @Override
    public void onClose() {
        if (this.apiKeyBox != null) {
            PackAiConfig.setApiKey(this.apiKeyBox.getValue());
        } else if (this.draftApiKey != null) {
            PackAiConfig.setApiKey(this.draftApiKey);
        }
        String baseRaw = this.baseUrlBox != null ? this.baseUrlBox.getValue() : this.draftBaseUrl;
        if (baseRaw != null) {
            String base = LlmClient.normalizeApiBaseUrl(baseRaw);
            if (!base.isEmpty()) {
                PackAiConfig.API_BASE_URL.set(base);
            }
        }
        if (this.parent instanceof AiAssistantScreen ai) {
            ai.reloadLayout();
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void renderScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics.pose());
        GuiShell.panel(graphics, this.shellLeft, this.shellTop, this.shellRight, this.shellBottom,
                GuiShell.FILL_BODY, GuiShell.BORDER);
        GuiShell.accentBar(graphics, this.shellLeft, this.shellTop, this.shellRight);
        if (this.activeTabW > 0) {
            graphics.fill(this.activeTabX, this.tabBarY + 20, this.activeTabX + this.activeTabW, this.tabBarY + 22,
                    GuiShell.ACCENT);
        }
        super.render(graphics.pose(), mouseX, mouseY, partialTick);
        GuiShell.title(graphics, this.font, this.title, this.width / 2, 6);
        GuiShell.statusOk(graphics, this.font, this.status, this.width / 2, this.height - 48);
        WidgetCompat.renderHoveredTips(this, graphics.pose(), mouseX, mouseY);
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderScreen(new GuiGraphics(this.minecraft, this, pose), mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && this.apiKeyBox != null && this.apiKeyBox.isFocused()) {
            saveApiKey();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

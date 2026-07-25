package com.skps9.packai.client.gui;

import java.util.List;

import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.LlmClient;
import com.skps9.packai.logic.ModelCatalog;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Mods → Packai settings. Long API key paste lives here (not NeoForge string box).
 */
public class PackAiSettingsScreen extends Screen {
    private static final List<String> MODES = List.of("auto", "cloud", "ollama", "offline");
    private static final List<String> SIDEBARS = List.of("right", "left");
    private static final List<String> PREFER_OBTAINS = List.of("craft", "quest", "loot", "balanced");
    private static final List<String> INGREDIENT_NBT_POLICIES = List.of("auto", "always", "never");
    private static final List<Integer> JEI_CHARS = List.of(2000, 4000, 8000, 12000);
    private static final List<Integer> HISTORY_TURNS = List.of(0, 2, 4, 8, 12, 16);
    private static final List<Integer> MAX_FACTS = List.of(4, 8, 12, 16, 24, 32);

    private final Screen parent;
    private EditBox apiKeyBox;
    private EditBox baseUrlBox;
    private String status = "";
    /** Prevent init→refresh→rebuild→init loops. */
    private boolean autoRefreshScheduled;

    public PackAiSettingsScreen(Screen parent) {
        super(Component.translatable("packai.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(400, this.width - 40);
        int left = (this.width - w) / 2;
        int y = 40;
        int row = 24;

        this.apiKeyBox = new EditBox(this.font, left, y, w - 70, 20,
                Component.translatable("packai.screen.api_key"));
        this.apiKeyBox.setMaxLength(512);
        this.apiKeyBox.setHint(Component.translatable("packai.screen.api_key_hint"));
        this.apiKeyBox.setTooltip(tip("packai.settings.tooltip.api_key"));
        String key = PackAiConfig.API_KEY.get();
        this.apiKeyBox.setValue(key == null ? "" : key);
        this.apiKeyBox.setFormatter((text, first) ->
                FormattedCharSequence.forward("*".repeat(Math.min(text.length(), 128)), Style.EMPTY));
        this.addRenderableWidget(this.apiKeyBox);
        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.save_key"), b -> saveApiKey())
                .bounds(left + w - 64, y, 64, 20)
                .tooltip(tip("packai.settings.tooltip.save_key"))
                .build());

        y += row + 4;
        this.baseUrlBox = new EditBox(this.font, left, y, w, 20,
                Component.translatable("packai.settings.api_base"));
        this.baseUrlBox.setMaxLength(256);
        this.baseUrlBox.setHint(Component.literal("https://openrouter.ai/api/v1"));
        this.baseUrlBox.setTooltip(tip("packai.settings.tooltip.api_base"));
        String base = PackAiConfig.API_BASE_URL.get();
        this.baseUrlBox.setValue(base == null ? "" : LlmClient.normalizeApiBaseUrl(base));
        this.addRenderableWidget(this.baseUrlBox);

        y += row + 8;
        int half = w / 2 - 4;
        this.addRenderableWidget(CycleButton.<String>builder(m -> Component.translatable("packai.screen.mode." + m))
                .withValues(MODES)
                .withInitialValue(PackAiConfig.resolvedMode())
                .withTooltip(v -> tip("packai.settings.tooltip.mode"))
                .create(left, y, half, 20, Component.translatable("packai.screen.mode"),
                        (btn, value) -> {
                            PackAiConfig.setMode(value);
                            this.rebuildWidgets();
                            ModelCatalog.refreshAsync(true, () -> {
                                if (this.minecraft != null && this.minecraft.screen == this) {
                                    this.rebuildWidgets();
                                }
                            });
                        }));

        int refreshW = 48;
        int modelW = half - refreshW - 4;
        Button modelBtn = Button.builder(modelButtonLabel(), b -> openModelPicker())
                .bounds(left + half + 8, y, modelW, 20)
                .tooltip(tip("packai.settings.tooltip.model"))
                .build();
        modelBtn.active = !"offline".equals(PackAiConfig.resolvedMode());
        this.addRenderableWidget(modelBtn);
        Button refreshBtn = Button.builder(Component.translatable("packai.screen.refresh_models"), b -> refreshModels())
                .bounds(left + half + 8 + modelW + 4, y, refreshW, 20)
                .tooltip(tip("packai.settings.tooltip.refresh_models"))
                .build();
        refreshBtn.active = !"offline".equals(PackAiConfig.resolvedMode());
        this.addRenderableWidget(refreshBtn);

        y += row + 10;
        int third = (w - 8) / 3;
        this.addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(String.valueOf(v)))
                .withValues(JEI_CHARS)
                .withInitialValue(nearest(JEI_CHARS, PackAiConfig.maxJeiChars()))
                .withTooltip(v -> tip("packai.settings.tooltip.max_jei_chars"))
                .create(left, y, third, 20, Component.translatable("packai.settings.max_jei_chars"),
                        (btn, value) -> PackAiConfig.setMaxJeiChars(value)));
        this.addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(String.valueOf(v)))
                .withValues(HISTORY_TURNS)
                .withInitialValue(nearest(HISTORY_TURNS, PackAiConfig.historyTurns()))
                .withTooltip(v -> tip("packai.settings.tooltip.history_turns"))
                .create(left + third + 4, y, third, 20, Component.translatable("packai.settings.history_turns"),
                        (btn, value) -> PackAiConfig.setHistoryTurns(value)));
        this.addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(String.valueOf(v)))
                .withValues(MAX_FACTS)
                .withInitialValue(nearest(MAX_FACTS, PackAiConfig.maxFacts()))
                .withTooltip(v -> tip("packai.settings.tooltip.max_facts"))
                .create(left + 2 * (third + 4), y, third, 20, Component.translatable("packai.settings.max_facts"),
                        (btn, value) -> PackAiConfig.setMaxFacts(value)));

        y += row + 8;
        this.addRenderableWidget(CycleButton.<String>builder(s -> Component.translatable("packai.settings.sidebar." + s))
                .withValues(SIDEBARS)
                .withInitialValue(PackAiConfig.sidebarSide())
                .withTooltip(v -> tip("packai.settings.tooltip.sidebar"))
                .create(left, y, half, 20, Component.translatable("packai.settings.sidebar"),
                        (btn, value) -> PackAiConfig.setSidebarSide(value)));
        this.addRenderableWidget(CycleButton.<String>builder(s -> Component.translatable("packai.settings.prefer_obtain." + s))
                .withValues(PREFER_OBTAINS)
                .withInitialValue(PackAiConfig.preferObtain())
                .withTooltip(v -> tip("packai.settings.tooltip.prefer_obtain"))
                .create(left + half + 8, y, half, 20, Component.translatable("packai.settings.prefer_obtain"),
                        (btn, value) -> PackAiConfig.setPreferObtain(value)));

        y += row + 8;
        this.addRenderableWidget(Button.builder(Component.translatable("packai.settings.recipe_cats"),
                        b -> this.minecraft.setScreen(new RecipeCategoryScreen(this)))
                .bounds(left, y, half, 20)
                .tooltip(tip("packai.settings.tooltip.recipe_cats"))
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("packai.settings.web_search"),
                        b -> this.minecraft.setScreen(new WebSearchSettingsScreen(this)))
                .bounds(left + half + 8, y, half, 20)
                .tooltip(tip("packai.settings.tooltip.web_search"))
                .build());

        y += row + 8;
        this.addRenderableWidget(CycleButton.<String>builder(
                        s -> Component.translatable("packai.settings.ingredient_nbt." + s))
                .withValues(INGREDIENT_NBT_POLICIES)
                .withInitialValue(PackAiConfig.ingredientNbtPolicy())
                .withTooltip(v -> tip("packai.settings.tooltip.ingredient_nbt"))
                .create(left, y, half, 20, Component.translatable("packai.settings.ingredient_nbt"),
                        (btn, value) -> PackAiConfig.setIngredientNbtPolicy(value)));
        this.addRenderableWidget(CycleButton.<Boolean>builder(
                        v -> Component.translatable(v
                                ? "packai.settings.ingredient_tooltip_req.on"
                                : "packai.settings.ingredient_tooltip_req.off"))
                .withValues(List.of(false, true))
                .withInitialValue(PackAiConfig.ingredientTooltipAsReq())
                .withTooltip(v -> tip("packai.settings.tooltip.ingredient_tooltip_req"))
                .create(left + half + 8, y, half, 20,
                        Component.translatable("packai.settings.ingredient_tooltip_req"),
                        (btn, value) -> PackAiConfig.setIngredientTooltipAsReq(value)));

        y += row + 16;
        this.addRenderableWidget(Button.builder(Component.translatable("packai.settings.save_all"), b -> saveAll())
                .bounds(left, y, half, 20)
                .tooltip(tip("packai.settings.tooltip.save_all"))
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + half + 8, y, half, 20)
                .tooltip(tip("packai.settings.tooltip.done"))
                .build());

        if (!this.autoRefreshScheduled) {
            this.autoRefreshScheduled = true;
            ModelCatalog.refreshAsync(() -> {
                if (this.minecraft != null && this.minecraft.screen == this) {
                    this.rebuildWidgets();
                }
            });
        }
    }

    private static Tooltip tip(String key) {
        return Tooltip.create(Component.translatable(key));
    }

    private static int nearest(List<Integer> options, int current) {
        int best = options.get(0);
        int bestDist = Integer.MAX_VALUE;
        for (int o : options) {
            int d = Math.abs(o - current);
            if (d < bestDist) {
                bestDist = d;
                best = o;
            }
        }
        return best;
    }

    private Component modelButtonLabel() {
        String m = PackAiConfig.uiModel();
        if (m.length() > 18) {
            m = m.substring(0, 16) + "…";
        }
        return Component.translatable("packai.screen.pick_model", m);
    }

    private void openModelPicker() {
        this.minecraft.setScreen(new ModelPickerScreen(this));
    }

    private void refreshModels() {
        this.status = Component.translatable("packai.status.models_refreshing").getString();
        ModelCatalog.invalidate();
        ModelCatalog.refreshAsync(true, () -> {
            if (this.minecraft != null && this.minecraft.screen == this) {
                this.status = Component.translatable("packai.status.models_refreshed").getString();
                this.rebuildWidgets();
            }
        });
    }

    private void saveApiKey() {
        PackAiConfig.setApiKey(this.apiKeyBox.getValue());
        this.apiKeyBox.setValue(PackAiConfig.API_KEY.get());
        ModelCatalog.invalidate();
        this.status = Component.translatable("packai.status.key_saved", PackAiConfig.API_KEY.get().length()).getString();
        ModelCatalog.refreshAsync(true, () -> {
            if (this.minecraft != null && this.minecraft.screen == this) {
                this.rebuildWidgets();
            }
        });
    }

    private void saveAll() {
        saveApiKey();
        String base = LlmClient.normalizeApiBaseUrl(this.baseUrlBox.getValue());
        if (!base.isEmpty()) {
            PackAiConfig.API_BASE_URL.set(base);
            PackAiConfig.SPEC.save();
            this.baseUrlBox.setValue(base);
            ModelCatalog.invalidate();
        }
        this.status = Component.translatable("packai.status.settings_saved").getString();
    }

    @Override
    public void onClose() {
        if (this.parent instanceof AiAssistantScreen ai) {
            ai.reloadLayout();
        }
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        if (!this.status.isEmpty()) {
            graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height - 40, 0xA0FFA0);
        }
        graphics.drawCenteredString(this.font,
                Component.translatable("packai.settings.hint"),
                this.width / 2, this.height - 24, 0xAAAAAA);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && this.apiKeyBox != null && this.apiKeyBox.isFocused()) {
            saveApiKey();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

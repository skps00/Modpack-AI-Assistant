package com.skps9.packai.client.gui;

import java.util.List;

import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.LlmClient;
import com.skps9.packai.logic.ModelCatalog;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Mods → Packai settings. Long API key paste lives here (not NeoForge string box).
 */
public class PackAiSettingsScreen extends Screen {
    private static final List<String> MODES = List.of("auto", "cloud", "ollama", "offline");

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
        String key = PackAiConfig.API_KEY.get();
        this.apiKeyBox.setValue(key == null ? "" : key);
        this.apiKeyBox.setFormatter((text, first) ->
                FormattedCharSequence.forward("*".repeat(Math.min(text.length(), 128)), Style.EMPTY));
        this.addRenderableWidget(this.apiKeyBox);
        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.save_key"), b -> saveApiKey())
                .bounds(left + w - 64, y, 64, 20).build());

        y += row + 4;
        this.baseUrlBox = new EditBox(this.font, left, y, w, 20,
                Component.translatable("packai.settings.api_base"));
        this.baseUrlBox.setMaxLength(256);
        this.baseUrlBox.setHint(Component.literal("https://openrouter.ai/api/v1"));
        String base = PackAiConfig.API_BASE_URL.get();
        this.baseUrlBox.setValue(base == null ? "" : LlmClient.normalizeApiBaseUrl(base));
        this.addRenderableWidget(this.baseUrlBox);

        y += row + 8;
        int half = w / 2 - 4;
        this.addRenderableWidget(CycleButton.<String>builder(m -> Component.translatable("packai.screen.mode." + m))
                .withValues(MODES)
                .withInitialValue(PackAiConfig.resolvedMode())
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
                .bounds(left + half + 8, y, modelW, 20).build();
        modelBtn.active = !"offline".equals(PackAiConfig.resolvedMode());
        this.addRenderableWidget(modelBtn);
        Button refreshBtn = Button.builder(Component.translatable("packai.screen.refresh_models"), b -> refreshModels())
                .bounds(left + half + 8 + modelW + 4, y, refreshW, 20).build();
        refreshBtn.active = !"offline".equals(PackAiConfig.resolvedMode());
        this.addRenderableWidget(refreshBtn);

        y += row + 16;
        this.addRenderableWidget(Button.builder(Component.translatable("packai.settings.save_all"), b -> saveAll())
                .bounds(left, y, half, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + half + 8, y, half, 20).build());

        if (!this.autoRefreshScheduled) {
            this.autoRefreshScheduled = true;
            ModelCatalog.refreshAsync(() -> {
                if (this.minecraft != null && this.minecraft.screen == this) {
                    this.rebuildWidgets();
                }
            });
        }
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

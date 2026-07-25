package com.skps9.packai.client.gui;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.LlmClient;
import com.skps9.packai.logic.ModelCatalog;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * MinPlay settings: mode / API key / base URL / model text. Full picker = Parity polish.
 */
public class PackAiSettingsScreen extends Screen {
    private static final List<String> MODES = List.of("auto", "cloud", "ollama", "offline");

    private final Screen parent;
    private EditBox apiKeyBox;
    private EditBox baseUrlBox;
    private EditBox modelBox;
    private String status = "";

    public PackAiSettingsScreen(Screen parent) {
        super(Component.translatable("packai.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(400, this.width - 40);
        int left = (this.width - w) / 2;
        int y = 40;

        this.apiKeyBox = new EditBox(this.font, left, y, w - 70, 20,
                Component.translatable("packai.screen.api_key"));
        this.apiKeyBox.setMaxLength(512);
        String key = PackAiConfig.API_KEY.get();
        this.apiKeyBox.setValue(key == null ? "" : key);
        this.addRenderableWidget(this.apiKeyBox);
        this.addRenderableWidget(new Button(left + w - 64, y, 64, 20,
                Component.translatable("packai.screen.save_key"), b -> saveApiKey()));

        y += 28;
        this.baseUrlBox = new EditBox(this.font, left, y, w, 20,
                Component.translatable("packai.settings.api_base"));
        this.baseUrlBox.setMaxLength(256);
        String base = PackAiConfig.API_BASE_URL.get();
        this.baseUrlBox.setValue(base == null ? "" : LlmClient.normalizeApiBaseUrl(base));
        this.addRenderableWidget(this.baseUrlBox);

        y += 28;
        this.addRenderableWidget(CycleButton.<String>builder(m -> Component.translatable("packai.screen.mode." + m))
                .withValues(MODES)
                .withInitialValue(PackAiConfig.resolvedMode())
                .create(left, y, w / 2 - 4, 20, Component.translatable("packai.screen.mode"),
                        (btn, value) -> {
                            PackAiConfig.setMode(value);
                            rebuildUi();
                        }));

        this.modelBox = new EditBox(this.font, left + w / 2 + 4, y, w / 2 - 4, 20,
                Component.translatable("packai.settings.model"));
        this.modelBox.setMaxLength(128);
        this.modelBox.setValue(PackAiConfig.uiModel());
        this.addRenderableWidget(this.modelBox);

        y += 32;
        this.addRenderableWidget(new Button(left, y, w / 2 - 4, 20,
                Component.translatable("packai.screen.refresh_models"),
                b -> refreshModels()));
        this.addRenderableWidget(new Button(left + w / 2 + 4, y, w / 2 - 4, 20,
                Component.translatable("gui.done"), b -> onClose()));
    }

    private void rebuildUi() {
        this.clearWidgets();
        this.init();
    }

    private void saveApiKey() {
        PackAiConfig.setApiKey(this.apiKeyBox.getValue());
        PackAiConfig.API_BASE_URL.set(LlmClient.normalizeApiBaseUrl(this.baseUrlBox.getValue()));
        PackAiConfig.setUiModel(this.modelBox.getValue());
        this.status = Component.translatable("packai.web_settings.saved").getString();
    }

    private void refreshModels() {
        saveApiKey();
        this.status = Component.translatable("packai.status.models_refreshing").getString();
        ModelCatalog.refreshAsync(true, () -> {
            if (this.minecraft != null && this.minecraft.screen == this) {
                this.status = Component.translatable("packai.status.models_refreshed").getString();
            }
        });
    }

    @Override
    public void onClose() {
        saveApiKey();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
            if (this.parent instanceof AiAssistantScreen assist) {
                assist.reloadLayout();
            }
        }
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(pose);
        drawCenteredString(pose, this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        if (this.status != null && !this.status.isEmpty()) {
            drawCenteredString(pose, this.font, this.status, this.width / 2, this.height - 24, 0xA0FFA0);
        }
        super.render(pose, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

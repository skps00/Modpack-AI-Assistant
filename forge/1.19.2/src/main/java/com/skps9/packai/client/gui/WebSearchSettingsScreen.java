package com.skps9.packai.client.gui;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.WebSearch;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Web search master switch plus optional Tavily / Serper keys.
 */
public class WebSearchSettingsScreen extends Screen {
    private final Screen parent;
    private EditBox tavilyBox;
    private EditBox serperBox;
    private String status = "";

    public WebSearchSettingsScreen(Screen parent) {
        super(Component.translatable("packai.web_settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(400, this.width - 40);
        int left = (this.width - w) / 2;
        int y = 48;
        int row = 26;
        int half = (w - 8) / 2;

        this.addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.translatable(
                        v ? "packai.settings.ingredient_tooltip_req.on" : "packai.settings.ingredient_tooltip_req.off"))
                .withValues(List.of(false, true))
                .withInitialValue(PackAiConfig.webSearchEnabled())
                .withTooltip(v -> WidgetCompat.tipLines("packai.web_settings.tooltip.enable"))
                .create(left, y, w, 20, Component.translatable("packai.web_settings.enable"),
                        (btn, value) -> PackAiConfig.setWebSearchEnabled(value)));

        y += row + 6;
        this.tavilyBox = maskedKeyBox(left, y, w, "packai.web_settings.tavily",
                "packai.web_settings.tooltip.tavily", PackAiConfig.TAVILY_API_KEY.get());
        this.addRenderableWidget(this.tavilyBox);

        y += row;
        this.serperBox = maskedKeyBox(left, y, w, "packai.web_settings.serper",
                "packai.web_settings.tooltip.serper", PackAiConfig.SERPER_API_KEY.get());
        this.addRenderableWidget(this.serperBox);

        y += row + 12;
        this.addRenderableWidget(WidgetCompat.button(left, y, half, 20,
                Component.translatable("packai.web_settings.save"), b -> saveKeys(),
                Component.translatable("packai.web_settings.tooltip.save")));
        this.addRenderableWidget(WidgetCompat.button(left + half + 8, y, half, 20,
                Component.translatable("gui.done"), b -> onClose(),
                Component.translatable("packai.web_settings.tooltip.done")));
    }

    private EditBox maskedKeyBox(int x, int y, int w, String labelKey, String tipKey, String initial) {
        EditBox box = WidgetCompat.editBox(x, y, w, 20,
                Component.translatable(labelKey),
                Component.translatable(tipKey));
        box.setMaxLength(256);
        box.setValue(initial == null ? "" : initial);
        box.setFormatter((text, first) ->
                FormattedCharSequence.forward("*".repeat(Math.min(text.length(), 128)), Style.EMPTY));
        return box;
    }

    private void saveKeys() {
        PackAiConfig.setTavilyApiKey(this.tavilyBox.getValue());
        PackAiConfig.setSerperApiKey(this.serperBox.getValue());
        this.tavilyBox.setValue(PackAiConfig.TAVILY_API_KEY.get());
        this.serperBox.setValue(PackAiConfig.SERPER_API_KEY.get());
        this.status = Component.translatable("packai.web_settings.saved").getString();
    }

    private String providerLabel() {
        if (!PackAiConfig.webSearchEnabled()) {
            return Component.translatable("packai.web_settings.provider.off").getString();
        }
        if (!WebSearch.resolveTavilyKey().isEmpty()) {
            return Component.translatable("packai.web_settings.provider.tavily").getString();
        }
        if (!WebSearch.resolveSerperKey().isEmpty()) {
            return Component.translatable("packai.web_settings.provider.serper").getString();
        }
        return Component.translatable("packai.web_settings.provider.free").getString();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void renderScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics.pose());
        GuiShell.nestedShell(graphics, this.width, this.height);
        super.render(graphics.pose(), mouseX, mouseY, partialTick);
        WidgetCompat.renderHoveredTips(this, graphics.pose(), mouseX, mouseY);
        GuiShell.title(graphics, this.font, this.title, this.width / 2, 8);
        graphics.drawCenteredString(this.font, providerLabel(), this.width / 2, 28, GuiShell.ACCENT);
        GuiShell.mutedCentered(graphics, this.font,
                Component.translatable("packai.web_settings.hint"),
                this.width / 2, this.height - 40);
        GuiShell.statusOk(graphics, this.font, this.status, this.width / 2, this.height - 24);
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderScreen(new GuiGraphics(this.minecraft, this, pose), mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

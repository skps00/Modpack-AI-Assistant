package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.skps9.packai.client.ClientSetup;
import com.skps9.packai.config.PackAiConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Primary ask UI. Mode/model shortcuts; API key is on Mods settings screen.
 */
public class AiAssistantScreen extends Screen {
    private static final List<String> MODES = List.of("auto", "cloud", "ollama", "offline");
    private static final List<String> CLOUD_MODELS = List.of(
            "deepseek-v4-flash",
            "deepseek-v4-pro",
            "deepseek-chat",
            "gpt-4o-mini",
            "gpt-4o",
            "gpt-4.1-mini"
    );
    private static final List<String> OLLAMA_MODELS = List.of(
            "llama3.2",
            "llama3.1",
            "qwen2.5",
            "mistral",
            "phi3"
    );

    private EditBox input;
    private String answerText = "";
    private String draftInput = "";
    private boolean busy;
    private double scrollOffset;
    private int panelLeft;
    private int panelWidth;
    private int answerTop;
    private int answerBottom;

    public AiAssistantScreen() {
        super(Component.translatable("packai.screen.title"));
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(420, this.width - 40);
        this.panelLeft = (this.width - this.panelWidth) / 2;

        int btnH = 20;
        int btnGap = 4;
        int rows = 4;
        int bottomStack = btnH * rows + btnGap * (rows - 1) + 8;
        int inputY = this.height - bottomStack - 28;
        this.answerTop = 28;
        this.answerBottom = inputY - 8;

        this.input = new EditBox(this.font, this.panelLeft, inputY, this.panelWidth, 20,
                Component.translatable("packai.screen.hint"));
        this.input.setMaxLength(512);
        this.input.setHint(Component.translatable("packai.screen.hint"));
        if (!this.draftInput.isEmpty()) {
            this.input.setValue(this.draftInput);
        }
        this.addRenderableWidget(this.input);

        int y = inputY + 28;
        int half = this.panelWidth / 2 - 4;

        this.addRenderableWidget(CycleButton.<String>builder(m -> Component.translatable("packai.screen.mode." + m))
                .withValues(MODES)
                .withInitialValue(PackAiConfig.resolvedMode())
                .create(this.panelLeft, y, half, btnH,
                        Component.translatable("packai.screen.mode"),
                        (btn, value) -> {
                            rememberDraft();
                            PackAiConfig.setMode(value);
                            this.rebuildWidgets();
                        }));

        this.addRenderableWidget(buildModelCycle(this.panelLeft + half + 8, y, half, btnH));

        y += btnH + btnGap;
        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.send"), b -> sendCurrent())
                .bounds(this.panelLeft, y, half, btnH).build());
        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.next_step"), b ->
                        askPreset("根據我手上的物品與目前整合包，下一步我該做什麼？", true, false))
                .bounds(this.panelLeft + half + 8, y, half, btnH).build());

        y += btnH + btnGap;
        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.quest_next"), b ->
                        askPreset("根據任務書，我現在任務下一步該做什麼？我卡住了看不懂。", false, false))
                .bounds(this.panelLeft, y, half, btnH).build());
        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.quest_wrong"), b ->
                        askPreset("任務書好像不對或過時了，請依整合包實際配方說明。", true, true))
                .bounds(this.panelLeft + half + 8, y, half, btnH).build());

        y += btnH + btnGap;
        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.how_to_use"), b ->
                        askPreset("我手上的物品在這個整合包裡要怎麼用？有什麼特殊配方或注意事項？", false, false))
                .bounds(this.panelLeft, y, this.panelWidth, btnH).build());

        this.setInitialFocus(this.input);
    }

    private CycleButton<String> buildModelCycle(int x, int y, int w, int h) {
        String mode = PackAiConfig.resolvedMode();
        boolean ollama = "ollama".equals(mode);
        boolean offline = "offline".equals(mode);
        List<String> options = new ArrayList<>(new LinkedHashSet<>(ollama ? OLLAMA_MODELS : CLOUD_MODELS));
        String current = ollama
                ? safeModel(PackAiConfig.OLLAMA_MODEL.get(), "llama3.2")
                : safeModel(PackAiConfig.MODEL.get(), "gpt-4o-mini");
        if (!options.contains(current)) {
            options.add(0, current);
        }

        CycleButton<String> btn = CycleButton.<String>builder(Component::literal)
                .withValues(options)
                .withInitialValue(current)
                .create(x, y, w, h, Component.translatable("packai.screen.model"), (b, value) -> {
                    if ("ollama".equals(PackAiConfig.resolvedMode())) {
                        PackAiConfig.setOllamaModel(value);
                    } else {
                        PackAiConfig.setCloudModel(value);
                    }
                });
        btn.active = !offline;
        return btn;
    }

    private static String safeModel(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.trim();
    }

    private void rememberDraft() {
        if (this.input != null) {
            this.draftInput = this.input.getValue();
        }
    }

    private void sendCurrent() {
        String q = this.input.getValue().trim();
        if (q.isEmpty() || busy) {
            return;
        }
        askPreset(q, false, false);
    }

    private void askPreset(String question, boolean includeHotbar, boolean questOverride) {
        if (busy) {
            return;
        }
        busy = true;
        this.scrollOffset = 0;
        this.answerText = Component.translatable("packai.status.waiting").getString();
        ClientSetup.askService().askAsync(question, includeHotbar, questOverride, answer -> {
            this.answerText = answer == null ? "" : answer;
            this.scrollOffset = 0;
            busy = false;
        });
    }

    private List<FormattedCharSequence> wrappedLines() {
        return this.font.split(Component.literal(this.answerText == null ? "" : this.answerText), this.panelWidth);
    }

    private int lineStride() {
        return this.font.lineHeight + 2;
    }

    private int maxScroll(List<FormattedCharSequence> lines) {
        int visible = Math.max(1, (this.answerBottom - this.answerTop) / lineStride());
        return Math.max(0, lines.size() - visible) * lineStride();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        List<FormattedCharSequence> lines = wrappedLines();
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll(lines));

        graphics.fill(this.panelLeft - 4, this.answerTop - 4,
                this.panelLeft + this.panelWidth + 4, this.answerBottom + 2, 0x66000000);

        graphics.enableScissor(this.panelLeft - 2, this.answerTop, this.panelLeft + this.panelWidth + 2, this.answerBottom);
        int y = this.answerTop - (int) this.scrollOffset;
        for (FormattedCharSequence line : lines) {
            if (y + lineStride() >= this.answerTop && y <= this.answerBottom) {
                graphics.drawString(this.font, line, this.panelLeft, y, 0xE0E0E0, false);
            }
            y += lineStride();
        }
        graphics.disableScissor();

        if (maxScroll(lines) > 0) {
            graphics.drawString(this.font, Component.literal("↕ 滾輪捲動"), this.panelLeft, this.answerBottom + 2, 0x888888, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= this.answerTop && mouseY <= this.answerBottom
                && mouseX >= this.panelLeft - 4 && mouseX <= this.panelLeft + this.panelWidth + 4) {
            this.scrollOffset = Mth.clamp(this.scrollOffset - scrollY * lineStride() * 2, 0, maxScroll(wrappedLines()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && this.input != null && this.input.isFocused()) {
            sendCurrent();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

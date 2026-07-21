package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.skps9.packai.client.ClientSetup;
import com.skps9.packai.client.QuestBookOpener;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.ModelCatalog;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.QuestGuide;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Ask UI with scrollable answer and clickable quest open buttons.
 */
public class AiAssistantScreen extends Screen {
    private static final int MAX_QUEST_SLOTS = 3;
    private static final List<String> MODES = List.of("auto", "cloud", "ollama", "offline");

    private EditBox input;
    private String answerText = "";
    private String draftInput = "";
    private List<QuestGuide.Hit> questLinks = List.of();
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
        // Bottom chrome never depends on quest count → input/actions stay glued to bottom.
        int bottomStack = btnH * rows + btnGap * (rows - 1) + 8;
        int inputY = this.height - bottomStack - 28;

        int questCount = Math.min(MAX_QUEST_SLOTS, this.questLinks.size());
        // Pack quest buttons just above input; only shrink answer by what we actually show.
        int questStrip = questCount == 0
                ? 0
                : questCount * btnH + (questCount - 1) * 2 + 8;
        this.answerTop = 28;
        this.answerBottom = inputY - 8 - questStrip;

        this.input = new EditBox(this.font, this.panelLeft, inputY, this.panelWidth, 20,
                Component.translatable("packai.screen.hint"));
        this.input.setMaxLength(512);
        this.input.setHint(Component.translatable("packai.screen.hint"));
        if (!this.draftInput.isEmpty()) {
            this.input.setValue(this.draftInput);
        }
        this.addRenderableWidget(this.input);

        int qy = inputY - questStrip;
        for (int i = 0; i < questCount; i++) {
            QuestGuide.Hit hit = this.questLinks.get(i);
            String title = Plainify.humanizeText(hit.title() == null || hit.title().isBlank()
                    ? Component.translatable("packai.screen.quest_unnamed").getString()
                    : hit.title());
            if (title.length() > 28) {
                title = title.substring(0, 28) + "…";
            }
            final QuestGuide.Hit openHit = hit;
            this.addRenderableWidget(Button.builder(
                            Component.translatable("packai.screen.open_quest", title),
                            b -> QuestBookOpener.open(openHit))
                    .bounds(this.panelLeft, qy, this.panelWidth, btnH).build());
            qy += btnH + 2;
        }

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
                            ModelCatalog.refreshAsync(() -> {
                                if (this.minecraft != null && this.minecraft.screen == this) {
                                    rememberDraft();
                                    this.rebuildWidgets();
                                }
                            });
                        }));

        int refreshW = 36;
        int modelW = half - refreshW - 4;
        this.addRenderableWidget(buildModelCycle(this.panelLeft + half + 8, y, modelW, btnH));
        Button refreshBtn = Button.builder(Component.translatable("packai.screen.refresh_models"), b -> refreshModels())
                .bounds(this.panelLeft + half + 8 + modelW + 4, y, refreshW, btnH).build();
        refreshBtn.active = !"offline".equals(PackAiConfig.resolvedMode());
        this.addRenderableWidget(refreshBtn);

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
        ModelCatalog.refreshAsync(() -> {
            if (this.minecraft != null && this.minecraft.screen == this) {
                rememberDraft();
                this.rebuildWidgets();
            }
        });
    }

    private CycleButton<String> buildModelCycle(int x, int y, int w, int h) {
        String mode = PackAiConfig.resolvedMode();
        boolean offline = "offline".equals(mode);
        List<String> options = ModelCatalog.optionsForUi();
        String current = PackAiConfig.uiModel();
        if (!options.contains(current)) {
            options = new ArrayList<>(options);
            options.add(0, current);
        }

        CycleButton<String> btn = CycleButton.<String>builder(Component::literal)
                .withValues(options)
                .withInitialValue(options.contains(current) ? current : options.get(0))
                .create(x, y, w, h, Component.translatable("packai.screen.model"), (b, value) ->
                        PackAiConfig.setUiModel(value));
        btn.active = !offline;
        return btn;
    }

    private void rememberDraft() {
        if (this.input != null) {
            this.draftInput = this.input.getValue();
        }
    }

    private void refreshModels() {
        rememberDraft();
        ModelCatalog.invalidate();
        ModelCatalog.refreshAsync(true, () -> {
            if (this.minecraft != null && this.minecraft.screen == this) {
                rememberDraft();
                this.rebuildWidgets();
            }
        });
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
        this.questLinks = List.of();
        this.answerText = Component.translatable("packai.status.waiting").getString();
        rememberDraft();
        this.rebuildWidgets();
        ClientSetup.askService().askAsync(question, includeHotbar, questOverride, result -> {
            this.answerText = result.answer() == null ? "" : result.answer();
            this.questLinks = result.quests() == null ? List.of() : result.quests();
            this.scrollOffset = 0;
            busy = false;
            rememberDraft();
            this.rebuildWidgets();
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

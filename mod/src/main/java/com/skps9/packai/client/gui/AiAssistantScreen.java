package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.skps9.packai.client.ClientSetup;
import com.skps9.packai.client.QuestBookOpener;
import com.skps9.packai.client.ReplyNotifier;
import com.skps9.packai.client.chat.ChatMessage;
import com.skps9.packai.client.chat.ChatSession;
import com.skps9.packai.client.jei.JeiTargetResolver;
import com.skps9.packai.logic.AskResult;
import com.skps9.packai.logic.ItemResolver;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.QuestGuide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Cursor-like multi-turn chat UI with scrollable history and docked input.
 */
public class AiAssistantScreen extends Screen {
    private static final int MAX_QUEST_SLOTS = 3;
    private static final int MAX_SUGGESTED_ICONS = 8;
    private static final int USER_COLOR = 0xA0C8FF;
    private static final int AI_COLOR = 0xE0E0E0;
    private static final int SUGGEST_COLOR = 0xFFD080;

    private EditBox input;
    private String draftInput = "";
    private double scrollOffset;
    private boolean stickToBottom = true;
    private int panelLeft;
    private int panelWidth;
    private int chatTop;
    private int chatBottom;
    private int inputY;

    public AiAssistantScreen() {
        super(Component.translatable("packai.screen.title"));
    }

    /** Open assistant (if needed) and ask about a tooltip / JEI item after hold completes. */
    public static void openAndAskAbout(ItemStack stack) {
        if (stack.isEmpty() || ChatSession.isBusy()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        JeiTargetResolver.pin(stack);
        AiAssistantScreen screen;
        if (mc.screen instanceof AiAssistantScreen open) {
            screen = open;
        } else {
            mc.setScreen(new AiAssistantScreen());
            if (!(mc.screen instanceof AiAssistantScreen created)) {
                JeiTargetResolver.clearPin();
                return;
            }
            screen = created;
        }
        screen.askAboutStack(stack);
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(420, this.width - 40);
        this.panelLeft = (this.width - this.panelWidth) / 2;

        int btnH = 20;
        int btnGap = 4;
        int rows = 3;
        int bottomStack = btnH * rows + btnGap * (rows - 1) + 8;
        this.inputY = this.height - bottomStack - 28;
        int heldStrip = 18;
        int boxY = this.inputY;

        List<QuestGuide.Hit> questLinks = ChatSession.lastQuests();
        boolean busy = ChatSession.isBusy();
        int questCount = Math.min(MAX_QUEST_SLOTS, questLinks.size());
        int questStrip = questCount == 0
                ? 0
                : questCount * btnH + (questCount - 1) * 2 + 8;
        this.chatTop = 28;
        this.chatBottom = boxY - heldStrip - 6 - questStrip;

        this.input = new EditBox(this.font, this.panelLeft, boxY, this.panelWidth, 20,
                Component.translatable("packai.screen.hint"));
        this.input.setMaxLength(512);
        this.input.setHint(Component.translatable("packai.screen.hint"));
        if (!this.draftInput.isEmpty()) {
            this.input.setValue(this.draftInput);
        }
        this.input.setEditable(!busy);
        this.addRenderableWidget(this.input);

        int qy = boxY - heldStrip - 4 - questStrip;
        for (int i = 0; i < questCount; i++) {
            QuestGuide.Hit hit = questLinks.get(i);
            String title = QuestGuide.displayTitle(hit);
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

        int y = boxY + 28;
        int half = this.panelWidth / 2 - 4;

        Button sendBtn = Button.builder(Component.translatable("packai.screen.send"), b -> sendCurrent())
                .bounds(this.panelLeft, y, half, btnH).build();
        sendBtn.active = !busy;
        this.addRenderableWidget(sendBtn);

        Button regenBtn = Button.builder(Component.translatable("packai.screen.regenerate"), b -> regenerate())
                .bounds(this.panelLeft + half + 8, y, half, btnH).build();
        regenBtn.active = ChatSession.canRegenerate();
        this.addRenderableWidget(regenBtn);

        y += btnH + btnGap;
        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.clear_chat"), b -> clearChat())
                .bounds(this.panelLeft, y, half, btnH).build());
        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.quest_next"), b ->
                        askPreset("根據任務書，我現在任務下一步該做什麼？我卡住了看不懂。", false, false))
                .bounds(this.panelLeft + half + 8, y, half, btnH).build());

        y += btnH + btnGap;
        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.next_step"), b ->
                        askPreset("根據我手上的物品與目前整合包，下一步我該做什麼？", true, false))
                .bounds(this.panelLeft, y, half, btnH).build());
        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.jei_think"), b -> askJeiHovered())
                .bounds(this.panelLeft + half + 8, y, half, btnH).build());

        this.setInitialFocus(this.input);
    }

    private void rememberDraft() {
        if (this.input != null) {
            this.draftInput = this.input.getValue();
        }
    }

    private void clearChat() {
        if (ChatSession.isBusy()) {
            return;
        }
        ChatSession.clear();
        this.scrollOffset = 0;
        this.stickToBottom = true;
        rememberDraft();
        this.rebuildWidgets();
    }

    private void sendCurrent() {
        String q = this.input.getValue().trim();
        if (q.isEmpty() || ChatSession.isBusy()) {
            return;
        }
        this.input.setValue("");
        this.draftInput = "";
        startAsk(q, false, false, ChatSession.recentForLlm(), true);
    }

    private void askPreset(String question, boolean includeHotbar, boolean questOverride) {
        startAsk(question, includeHotbar, questOverride, ChatSession.recentForLlm(), true);
    }

    private void askJeiHovered() {
        if (ChatSession.isBusy() || this.minecraft == null) {
            return;
        }
        ItemStack target = JeiTargetResolver.resolve(this.minecraft, "");
        if (target.isEmpty()) {
            this.input.setValue(Component.translatable("packai.screen.jei_think_hint").getString());
            return;
        }
        JeiTargetResolver.pin(target);
        askAboutStack(target);
    }

    private void askAboutStack(ItemStack stack) {
        String name = stack.getHoverName().getString();
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String id = key == null ? "" : key.toString();
        String q = id.isEmpty()
                ? "「" + name + "」在這個整合包有什麼用途、配方和取得方式？"
                : "「" + name + "」(" + id + ") 在這個整合包有什麼用途、配方和取得方式？";
        startAsk(q, false, false, ChatSession.recentForLlm(), true);
    }

    private void regenerate() {
        Optional<ChatSession.RegenerateRequest> req = ChatSession.prepareRegenerate();
        if (req.isEmpty()) {
            return;
        }
        ChatSession.RegenerateRequest r = req.get();
        startAsk(r.question(), r.includeHotbar(), r.questOverride(), r.prior(), false);
    }

    private void startAsk(
            String question,
            boolean includeHotbar,
            boolean questOverride,
            List<ChatMessage> prior,
            boolean appendUser
    ) {
        if (ChatSession.isBusy()) {
            return;
        }
        ItemStack held = contextStack();
        String itemLabel = heldItemLabel(held);
        String itemId = heldItemId(held);
        ChatSession.setBusy(true);
        ChatSession.setLastQuests(List.of());
        ChatSession.setLastAsk(new ChatSession.LastAsk(question, includeHotbar, questOverride));
        if (appendUser) {
            ChatSession.append(ChatMessage.user(question, itemLabel, itemId));
        }
        ChatSession.append(ChatMessage.assistant(
                Component.translatable("packai.status.waiting").getString()));
        this.stickToBottom = true;
        rememberDraft();
        this.rebuildWidgets();

        ClientSetup.askService().askAsync(question, includeHotbar, questOverride, prior, AiAssistantScreen::onAskFinished);
    }

    static void onAskFinished(AskResult result) {
        String answer = result == null || result.answer() == null ? "" : result.answer();
        List<QuestGuide.Hit> quests = result == null || result.quests() == null ? List.of() : result.quests();
        List<String> items = result == null || result.suggestedItemIds() == null
                ? List.of()
                : result.suggestedItemIds();
        ChatSession.replaceLastAssistant(answer, items);
        ChatSession.setLastQuests(quests);
        ChatSession.setBusy(false);

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AiAssistantScreen open) {
            open.stickToBottom = true;
            open.rememberDraft();
            open.rebuildWidgets();
        } else {
            ReplyNotifier.alertReplyReady(quests);
        }
    }

    /** Main hand, else JEI hover / id in draft question. */
    private ItemStack contextStack() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack pinned = JeiTargetResolver.pinnedOrEmpty();
        if (!pinned.isEmpty()) {
            return pinned;
        }
        ItemStack held = this.minecraft.player.getMainHandItem();
        if (!held.isEmpty()) {
            return held;
        }
        String draft = this.input == null ? this.draftInput : this.input.getValue();
        return JeiTargetResolver.resolve(this.minecraft, draft == null ? "" : draft);
    }

    private static String heldItemLabel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Component.translatable("packai.screen.held_empty").getString();
        }
        return stack.getHoverName().getString();
    }

    private static String heldItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private void renderInputHeldStrip(GuiGraphics graphics) {
        ItemStack stack = contextStack();
        String name = heldItemLabel(stack);
        Component line = this.minecraft != null
                && this.minecraft.player != null
                && this.minecraft.player.getMainHandItem().isEmpty()
                && !stack.isEmpty()
                ? Component.translatable("packai.screen.jei_context", name)
                : Component.translatable("packai.screen.held_item", name);
        int y = this.inputY - 16;
        int x = this.panelLeft;
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x, y - 1);
            x += 18;
        }
        graphics.drawString(this.font, line, x, y + 3, 0xA0A0A0, false);
    }

    private record ChatLine(
            FormattedCharSequence text,
            int color,
            ItemStack icon,
            List<ItemStack> iconRow,
            String subLabel
    ) {
        ChatLine(FormattedCharSequence text, int color) {
            this(text, color, ItemStack.EMPTY, List.of(), "");
        }

        ChatLine(FormattedCharSequence text, int color, ItemStack icon) {
            this(text, color, icon, List.of(), "");
        }
    }

    private List<ChatLine> chatLines() {
        List<ChatLine> lines = new ArrayList<>();
        List<ChatMessage> msgs = ChatSession.snapshot();
        if (msgs.isEmpty()) {
            lines.add(new ChatLine(
                    Component.translatable("packai.screen.chat_empty").getVisualOrderText(), 0x888888));
            return lines;
        }
        for (ChatMessage msg : msgs) {
            String label = msg.isUser()
                    ? Component.translatable("packai.screen.chat_you").getString()
                    : Component.translatable("packai.screen.chat_ai").getString();
            int color = msg.isUser() ? USER_COLOR : AI_COLOR;
            String body = Plainify.forMinecraftUi(msg.text());
            if (msg.isUser() && msg.hasHeldItem()) {
                String tag = "[" + msg.heldItemLabel() + "] ";
                ItemStack icon = ItemResolver.stackFromId(msg.heldItemId());
                int wrap = this.panelWidth - (icon.isEmpty() ? 0 : 18);
                String head = label + tag + body;
                List<FormattedCharSequence> parts = this.font.split(Component.literal(head), Math.max(40, wrap));
                boolean first = true;
                for (FormattedCharSequence part : parts) {
                    lines.add(new ChatLine(part, color, first ? icon : ItemStack.EMPTY));
                    first = false;
                }
            } else {
                String block = label + body;
                for (FormattedCharSequence part : this.font.split(Component.literal(block), this.panelWidth)) {
                    lines.add(new ChatLine(part, color));
                }
            }
            if (!msg.isUser() && msg.hasSuggestedItems()) {
                List<ItemResolver.ResolvedItem> resolved = ItemResolver.resolveIds(msg.suggestedItemIds());
                List<ItemStack> row = new ArrayList<>();
                StringBuilder sub = new StringBuilder();
                int n = 0;
                for (ItemResolver.ResolvedItem ri : resolved) {
                    if (n >= MAX_SUGGESTED_ICONS) {
                        break;
                    }
                    ItemStack st = ItemResolver.stackFromId(ri.id());
                    if (st.isEmpty()) {
                        continue;
                    }
                    row.add(st);
                    if (ri.ambiguous()) {
                        if (!sub.isEmpty()) {
                            sub.append("  ");
                        }
                        sub.append(shortId(ri.id()));
                    }
                    n++;
                }
                if (!row.isEmpty()) {
                    lines.add(new ChatLine(
                            Component.translatable("packai.screen.suggested_items").getVisualOrderText(),
                            SUGGEST_COLOR,
                            ItemStack.EMPTY,
                            row,
                            sub.toString()));
                }
            }
            lines.add(new ChatLine(FormattedCharSequence.EMPTY, color));
        }
        return lines;
    }

    private static String shortId(String id) {
        int c = id.indexOf(':');
        return c < 0 ? id : id.substring(c + 1);
    }

    private int lineStride() {
        return this.font.lineHeight + 2;
    }

    private int maxScroll(List<ChatLine> lines) {
        int visible = Math.max(1, (this.chatBottom - this.chatTop) / lineStride());
        return Math.max(0, lines.size() - visible) * lineStride();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        renderInputHeldStrip(graphics);

        List<ChatLine> lines = chatLines();
        int max = maxScroll(lines);
        if (this.stickToBottom) {
            this.scrollOffset = max;
        } else {
            this.scrollOffset = Mth.clamp(this.scrollOffset, 0, max);
        }

        graphics.fill(this.panelLeft - 4, this.chatTop - 4,
                this.panelLeft + this.panelWidth + 4, this.chatBottom + 2, 0x66000000);

        graphics.enableScissor(this.panelLeft - 2, this.chatTop, this.panelLeft + this.panelWidth + 2, this.chatBottom);
        int y = this.chatTop - (int) this.scrollOffset;
        for (ChatLine line : lines) {
            if (y + lineStride() >= this.chatTop && y <= this.chatBottom) {
                int x = this.panelLeft;
                if (!line.icon().isEmpty()) {
                    graphics.renderItem(line.icon(), this.panelLeft, y - 1);
                    x = this.panelLeft + 18;
                } else if (!line.iconRow().isEmpty()) {
                    int ix = this.panelLeft;
                    for (ItemStack st : line.iconRow()) {
                        graphics.renderItem(st, ix, y - 1);
                        ix += 18;
                    }
                    x = ix + 4;
                }
                if (line.text() != FormattedCharSequence.EMPTY) {
                    graphics.drawString(this.font, line.text(), x, y, line.color(), false);
                }
                if (!line.subLabel().isEmpty()) {
                    graphics.drawString(this.font, line.subLabel(), this.panelLeft + 4, y + lineStride(),
                            0x888888, false);
                }
            }
            y += lineStride();
            if (!line.subLabel().isEmpty()) {
                y += lineStride();
            }
        }
        graphics.disableScissor();

        if (max > 0) {
            graphics.drawString(this.font, Component.translatable("packai.screen.chat_scroll"),
                    this.panelLeft, this.chatBottom + 2, 0x888888, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= this.chatTop && mouseY <= this.chatBottom
                && mouseX >= this.panelLeft - 4 && mouseX <= this.panelLeft + this.panelWidth + 4) {
            this.stickToBottom = false;
            List<ChatLine> lines = chatLines();
            this.scrollOffset = Mth.clamp(
                    this.scrollOffset - scrollY * lineStride() * 2, 0, maxScroll(lines));
            if (this.scrollOffset >= maxScroll(lines) - 1) {
                this.stickToBottom = true;
            }
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

    /** Instant think from assistant screen button (no hold). */
    public void onThinkKey() {
        askJeiHovered();
    }
}

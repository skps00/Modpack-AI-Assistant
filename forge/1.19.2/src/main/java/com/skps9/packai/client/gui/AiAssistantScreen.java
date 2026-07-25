package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skps9.packai.client.QuestBookOpener;
import com.skps9.packai.client.chat.ChatMessage;
import com.skps9.packai.client.chat.ChatSession;
import com.skps9.packai.client.jei.JeiTargetResolver;
import com.skps9.packai.client.service.AskService;
import com.skps9.packai.logic.AskResult;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.QuestGuide;
import com.skps9.packai.logic.RecipeCard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * MinPlay chat UI (PoseStack / 1.19.2). Full recipe cards + JEI = Parity.
 */
public class AiAssistantScreen extends Screen {
    private EditBox input;
    private String draftInput = "";
    private double scrollOffset;
    private int chatTop;
    private int chatBottom;
    private int panelLeft;
    private int panelWidth;

    public AiAssistantScreen() {
        super(Component.translatable("packai.screen.title"));
    }

    public void reloadLayout() {
        rebuildUi();
    }

    public static void openAndAskAbout(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        AiAssistantScreen screen;
        if (mc.screen instanceof AiAssistantScreen open) {
            screen = open;
        } else {
            mc.setScreen(new AiAssistantScreen());
            if (!(mc.screen instanceof AiAssistantScreen created)) {
                return;
            }
            screen = created;
        }
        if (ChatSession.isBusy()) {
            return;
        }
        JeiTargetResolver.pin(stack);
        screen.askAboutStack(stack);
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(480, this.width - 40);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        int inputY = this.height - 40;
        this.chatTop = 28;
        this.chatBottom = inputY - 8;
        boolean busy = ChatSession.isBusy();

        this.input = new EditBox(this.font, this.panelLeft, inputY, this.panelWidth - 140, 20,
                Component.translatable("packai.screen.hint"));
        this.input.setMaxLength(512);
        if (!this.draftInput.isEmpty()) {
            this.input.setValue(this.draftInput);
        }
        this.input.setEditable(!busy);
        this.addRenderableWidget(this.input);

        Button send = this.addRenderableWidget(new Button(
                this.panelLeft + this.panelWidth - 136, inputY, 64, 20,
                Component.translatable("packai.screen.send"),
                b -> sendCurrent()));
        send.active = !busy;

        this.addRenderableWidget(new Button(
                this.panelLeft + this.panelWidth - 68, inputY, 68, 20,
                Component.translatable("packai.screen.settings"),
                b -> {
                    rememberDraft();
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new PackAiSettingsScreen(this));
                    }
                }));

        int y = 8;
        this.addRenderableWidget(new Button(
                this.width - 90, y, 80, 20,
                Component.translatable("packai.screen.clear_chat"),
                b -> clearChat()));
        Button regen = this.addRenderableWidget(new Button(
                this.width - 180, y, 86, 20,
                Component.translatable("packai.screen.regenerate"),
                b -> regenerate()));
        regen.active = ChatSession.canRegenerate();

        List<QuestGuide.Hit> quests = ChatSession.lastQuests();
        if (!quests.isEmpty()) {
            QuestGuide.Hit hit = quests.get(0);
            String title = QuestGuide.displayTitle(hit);
            if (title.length() > 12) {
                title = title.substring(0, 12) + "…";
            }
            this.addRenderableWidget(new Button(
                    this.width - 270, y, 86, 20,
                    Component.translatable("packai.screen.open_quest_short", title),
                    b -> QuestBookOpener.open(hit)));
        }

        this.setInitialFocus(this.input);
    }

    private void rebuildUi() {
        this.clearWidgets();
        this.init();
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
        rememberDraft();
        rebuildUi();
    }

    private void sendCurrent() {
        String q = this.input.getValue().trim();
        if (q.isEmpty() || ChatSession.isBusy()) {
            return;
        }
        this.input.setValue("");
        this.draftInput = "";
        startAsk(q, false, false, ChatSession.recentForLlm());
    }

    private void askAboutStack(ItemStack stack) {
        String name = stack.getHoverName().getString();
        startAsk(name, true, false, ChatSession.recentForLlm());
    }

    private void regenerate() {
        var opt = ChatSession.prepareRegenerate();
        if (opt.isEmpty() || ChatSession.isBusy()) {
            return;
        }
        ChatSession.RegenerateRequest req = opt.get();
        startAsk(req.question(), req.includeHotbar(), req.questOverride(), req.prior());
    }

    private void startAsk(String question, boolean includeHotbar, boolean questOverride, List<ChatMessage> history) {
        if (question == null || question.isBlank() || ChatSession.isBusy()) {
            return;
        }
        ChatSession.setBusy(true);
        ChatSession.setLastAsk(new ChatSession.LastAsk(question, includeHotbar, questOverride));
        ChatSession.append(ChatMessage.user(question));
        ChatSession.append(ChatMessage.assistant(Component.translatable("packai.status.waiting").getString()));
        rebuildUi();
        AskService.INSTANCE.askAsync(question, includeHotbar, questOverride, history, this::onAskDone);
    }

    private void onAskDone(AskResult result) {
        ChatSession.setBusy(false);
        if (result == null) {
            ChatSession.replaceLastAssistant("", List.of());
            rebuildUi();
            return;
        }
        List<QuestGuide.Hit> quests = result.quests() == null ? List.of() : result.quests();
        ChatSession.setLastQuests(quests);
        ChatSession.replaceLastAssistant(
                result.answer() == null ? "" : result.answer(),
                result.suggestedItemIds() == null ? List.of() : result.suggestedItemIds(),
                result.recipeCards() == null ? List.of() : result.recipeCards());
        rebuildUi();
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(pose);
        drawCenteredString(pose, this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        List<String> lines = chatTextLines();
        int y = this.chatTop - (int) this.scrollOffset;
        int maxY = this.chatBottom;
        for (String line : lines) {
            List<FormattedCharSequence> wrapped = this.font.split(Component.literal(line), this.panelWidth);
            for (FormattedCharSequence seq : wrapped) {
                if (y + 10 >= this.chatTop && y <= maxY) {
                    this.font.draw(pose, seq, this.panelLeft, y, line.startsWith("You:") ? 0xA0C8FF : 0xE0E0E0);
                }
                y += 10;
            }
            y += 4;
        }
        super.render(pose, mouseX, mouseY, partialTick);
    }

    private List<String> chatTextLines() {
        List<String> out = new ArrayList<>();
        for (ChatMessage msg : ChatSession.snapshot()) {
            String prefix = msg.role() == ChatMessage.Role.USER ? "You: " : "AI: ";
            String body = msg.text() == null ? "" : msg.text();
            for (String part : body.split("\n", -1)) {
                out.add(prefix + part);
                prefix = "    ";
            }
            if (msg.role() == ChatMessage.Role.ASSISTANT && msg.hasRecipeCards()) {
                for (RecipeCard card : msg.recipeCards()) {
                    out.add("  ▸ " + formatRecipeCard(card));
                }
            }
        }
        if (out.isEmpty()) {
            out.add(Component.translatable("packai.screen.chat_empty").getString());
        }
        return out;
    }

    /** Best-effort text card (PoseStack UI — full icon grid is Parity polish gap). */
    private static String formatRecipeCard(RecipeCard card) {
        String cat = Plainify.stripMcFormat(card.categoryTitle() == null ? "" : card.categoryTitle());
        if (card.layout() == RecipeCard.Layout.CRAFTING_3X3) {
            StringBuilder sb = new StringBuilder(cat).append(" [3x3]");
            for (ItemStack s : card.grid()) {
                if (s != null && !s.isEmpty()) {
                    sb.append(' ').append(Plainify.stripMcFormat(s.getHoverName().getString()));
                } else {
                    sb.append(" ·");
                }
            }
            if (!card.outputs().isEmpty() && !card.outputs().get(0).isEmpty()) {
                sb.append(" → ").append(Plainify.stripMcFormat(card.outputs().get(0).getHoverName().getString()));
            }
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder(cat).append(" [flow]");
        for (ItemStack s : card.inputs()) {
            if (s != null && !s.isEmpty()) {
                sb.append(' ').append(Plainify.stripMcFormat(s.getHoverName().getString()));
            }
        }
        if (!card.outputs().isEmpty()) {
            sb.append(" →");
            for (ItemStack s : card.outputs()) {
                if (s != null && !s.isEmpty()) {
                    sb.append(' ').append(Plainify.stripMcFormat(s.getHoverName().getString()));
                }
            }
        }
        return sb.toString();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        this.scrollOffset = Math.max(0, this.scrollOffset - delta * 12);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            sendCurrent();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        rememberDraft();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

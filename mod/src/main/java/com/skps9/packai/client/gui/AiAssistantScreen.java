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
import com.skps9.packai.client.jei.JeiSoftIngredients;
import com.skps9.packai.client.jei.SuggestIcons;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.AskResult;
import com.skps9.packai.logic.ItemResolver;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.QuestGuide;
import com.skps9.packai.logic.RecipeCard;
import com.skps9.packai.logic.RecipeExtra;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Cursor-like multi-turn chat UI with scrollable history and docked input.
 */
public class AiAssistantScreen extends Screen {
    private static final int MAX_QUEST_SLOTS = 3;
    private static final int MAX_SUGGESTED_ICONS = 8;
    /** Standard item bitmap; oversized mod renders are clipped to this. */
    private static final int ICON_SIZE = 16;
    /** Icon column width including gap before text. */
    private static final int ICON_COL = 20;
    private static final int USER_COLOR = 0xA0C8FF;
    private static final int AI_COLOR = 0xE0E0E0;
    private static final int SUGGEST_COLOR = 0xFFD080;

    private final List<HoverHit> hoverHits = new ArrayList<>();
    private EditBox input;
    private String draftInput = "";
    private double scrollOffset;
    private boolean stickToBottom = true;
    private int panelLeft;
    private int panelWidth;
    private int sideLeft;
    private int sideWidth;
    private int chatTop;
    private int chatBottom;
    private int inputY;
    private int questIndex;
    private List<ChatLine> cachedChatLines;
    private int cachedChatGen = -1;
    private int cachedChatPanelWidth = -1;

    public AiAssistantScreen() {
        super(Component.translatable("packai.screen.title"));
    }

    /** Called when returning from settings so sidebar side applies. */
    public void reloadLayout() {
        this.rebuildWidgets();
    }

    /** Open assistant (if needed) and ask about a tooltip / JEI item after hold completes. */
    public static void openAndAskAbout(ItemStack stack) {
        if (stack.isEmpty()) {
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
        // Thinking already: screen is open for watching; do not queue another ask.
        if (ChatSession.isBusy()) {
            return;
        }
        JeiTargetResolver.pin(stack);
        screen.askAboutStack(stack);
    }

    @Override
    protected void init() {
        int btnH = 20;
        int btnGap = 3;
        int heldStrip = 18;
        this.sideWidth = 108;
        int gap = 8;
        int totalW = Math.min(560, this.width - 24);
        int chatW = Math.max(200, totalW - this.sideWidth - gap);
        int origin = (this.width - (chatW + gap + this.sideWidth)) / 2;
        boolean right = PackAiConfig.sidebarOnRight();
        if (right) {
            this.panelLeft = origin;
            this.sideLeft = origin + chatW + gap;
        } else {
            this.sideLeft = origin;
            this.panelLeft = origin + this.sideWidth + gap;
        }
        this.panelWidth = chatW;

        this.inputY = this.height - 44;
        this.chatTop = 28;
        this.chatBottom = this.inputY - heldStrip - 6;

        boolean busy = ChatSession.isBusy();
        List<QuestGuide.Hit> questLinks = ChatSession.lastQuests();
        int questCount = Math.min(MAX_QUEST_SLOTS, questLinks.size());
        if (questCount > 0) {
            this.questIndex = Mth.clamp(this.questIndex, 0, questCount - 1);
        } else {
            this.questIndex = 0;
        }

        this.input = new EditBox(this.font, this.panelLeft, this.inputY, this.panelWidth, 20,
                Component.translatable("packai.screen.hint"));
        this.input.setMaxLength(512);
        this.input.setHint(Component.translatable("packai.screen.hint"));
        if (!this.draftInput.isEmpty()) {
            this.input.setValue(this.draftInput);
        }
        this.input.setEditable(!busy);
        this.addRenderableWidget(this.input);

        int sy = this.chatTop;
        int sw = this.sideWidth;

        if (questCount > 0) {
            QuestGuide.Hit hit = questLinks.get(this.questIndex);
            String title = QuestGuide.displayTitle(hit);
            if (title.length() > 14) {
                title = title.substring(0, 14) + "…";
            }
            final QuestGuide.Hit openHit = hit;
            this.addRenderableWidget(Button.builder(
                            Component.translatable("packai.screen.open_quest_short", title),
                            b -> QuestBookOpener.open(openHit))
                    .bounds(this.sideLeft, sy, sw, btnH).build());
            sy += btnH + btnGap;
            if (questCount > 1) {
                int shown = this.questIndex + 1;
                this.addRenderableWidget(Button.builder(
                                Component.translatable("packai.screen.quest_more", shown, questCount),
                                b -> {
                                    this.questIndex = (this.questIndex + 1) % questCount;
                                    rememberDraft();
                                    this.rebuildWidgets();
                                })
                        .bounds(this.sideLeft, sy, sw, btnH).build());
                sy += btnH + btnGap;
            }
            sy += 4;
        }

        Button sendBtn = Button.builder(Component.translatable("packai.screen.send"), b -> sendCurrent())
                .bounds(this.sideLeft, sy, sw, btnH).build();
        sendBtn.active = !busy;
        this.addRenderableWidget(sendBtn);
        sy += btnH + btnGap;

        Button regenBtn = Button.builder(Component.translatable("packai.screen.regenerate"), b -> regenerate())
                .bounds(this.sideLeft, sy, sw, btnH).build();
        regenBtn.active = ChatSession.canRegenerate();
        this.addRenderableWidget(regenBtn);
        sy += btnH + btnGap;

        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.clear_chat"), b -> clearChat())
                .bounds(this.sideLeft, sy, sw, btnH).build());
        sy += btnH + btnGap;

        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.quest_next"), b ->
                        askTemplate("packai.ask.quest_next", null, null, false, false))
                .bounds(this.sideLeft, sy, sw, btnH).build());
        sy += btnH + btnGap;

        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.next_step_short"), b ->
                        askTemplate("packai.ask.held_next", null, null, true, false))
                .bounds(this.sideLeft, sy, sw, btnH).build());
        sy += btnH + btnGap + 6;

        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.jump_latest"), b -> {
            this.stickToBottom = true;
            this.scrollOffset = maxScroll(chatLines());
        }).bounds(this.sideLeft, sy, sw, btnH).build());
        sy += btnH + btnGap;

        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.settings"), b -> {
            rememberDraft();
            if (this.minecraft != null) {
                this.minecraft.setScreen(new PackAiSettingsScreen(this));
            }
        }).bounds(this.sideLeft, sy, sw, btnH).build());

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
        startAsk(q, false, false, ChatSession.recentForLlm(), true, null, null, null);
    }

    private void askTemplate(
            String templateKey,
            String arg0,
            String arg1,
            boolean includeHotbar,
            boolean questOverride
    ) {
        String q = resolveTemplate(templateKey, arg0, arg1);
        startAsk(q, includeHotbar, questOverride, ChatSession.recentForLlm(), true, templateKey, arg0, arg1);
    }

    private static String resolveTemplate(String templateKey, String arg0, String arg1) {
        if (templateKey == null || templateKey.isBlank()) {
            return "";
        }
        if (arg0 != null && arg1 != null) {
            return Component.translatable(templateKey, arg0, arg1).getString();
        }
        if (arg0 != null) {
            return Component.translatable(templateKey, arg0).getString();
        }
        return Component.translatable(templateKey).getString();
    }

    private void askAboutStack(ItemStack stack) {
        String name = stack.getHoverName().getString();
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String id = key == null ? "" : key.toString();
        if (id.isEmpty()) {
            askTemplate("packai.ask.item_about", name, null, false, false);
        } else {
            askTemplate("packai.ask.item_about_id", name, id, false, false);
        }
    }

    private void regenerate() {
        Optional<ChatSession.RegenerateRequest> req = ChatSession.prepareRegenerate();
        if (req.isEmpty()) {
            return;
        }
        ChatSession.RegenerateRequest r = req.get();
        String question = r.question();
        String templateKey = r.templateKey();
        String arg0 = r.templateArg0();
        String arg1 = r.templateArg1();
        // Rebuild from template in the *current* game language (lang change + regen).
        if (r.hasTemplate()) {
            question = resolveTemplate(templateKey, arg0, arg1);
            ChatSession.replaceLastUserText(question);
        }
        startAsk(question, r.includeHotbar(), r.questOverride(), r.prior(), false, templateKey, arg0, arg1);
    }

    private void startAsk(
            String question,
            boolean includeHotbar,
            boolean questOverride,
            List<ChatMessage> prior,
            boolean appendUser,
            String templateKey,
            String templateArg0,
            String templateArg1
    ) {
        if (ChatSession.isBusy()) {
            return;
        }
        ItemStack held = contextStack();
        String itemLabel = heldItemLabel(held);
        String itemId = heldItemId(held);
        ChatSession.setBusy(true);
        ChatSession.setLastQuests(List.of());
        ChatSession.setLastAsk(new ChatSession.LastAsk(
                question, includeHotbar, questOverride, templateKey, templateArg0, templateArg1));
        if (appendUser) {
            ChatSession.append(ChatMessage.user(question, itemLabel, itemId, held));
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
        List<RecipeCard> cards = result == null || result.recipeCards() == null
                ? List.of()
                : result.recipeCards();
        ChatSession.replaceLastAssistant(answer, items, cards);
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
        // getString() can still contain § codes from lang/custom names; Font.split would paint gold.
        return Plainify.stripMcFormat(stack.getHoverName().getString());
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
            addItemHover(x, y - 1, stack);
            x += ICON_COL;
        }
        graphics.drawString(this.font, line, x, y + 3, 0xA0A0A0, false);
    }

    private record ChatLine(
            FormattedCharSequence text,
            int color,
            ItemStack icon,
            List<ItemStack> iconRow,
            String subLabel,
            RecipeCard recipeCard
    ) {
        ChatLine(FormattedCharSequence text, int color) {
            this(text, color, ItemStack.EMPTY, List.of(), "", null);
        }

        ChatLine(FormattedCharSequence text, int color, ItemStack icon) {
            this(text, color, icon, List.of(), "", null);
        }

        ChatLine(FormattedCharSequence text, int color, ItemStack icon, List<ItemStack> iconRow, String subLabel) {
            this(text, color, icon, iconRow, subLabel, null);
        }

        static ChatLine recipe(RecipeCard card) {
            return new ChatLine(FormattedCharSequence.EMPTY, SUGGEST_COLOR, ItemStack.EMPTY, List.of(), "", card);
        }
    }

    private List<ChatLine> chatLines() {
        if (cachedChatLines != null
                && cachedChatGen == ChatSession.generation()
                && cachedChatPanelWidth == this.panelWidth) {
            return cachedChatLines;
        }
        List<ChatLine> lines = buildChatLines();
        cachedChatLines = lines;
        cachedChatGen = ChatSession.generation();
        cachedChatPanelWidth = this.panelWidth;
        return lines;
    }

    private List<ChatLine> buildChatLines() {
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
                String tag = "[" + Plainify.stripMcFormat(msg.heldItemLabel()) + "] ";
                ItemStack icon = msg.iconOrEmpty();
                if (icon.isEmpty()) {
                    icon = ItemResolver.stackFromId(msg.heldItemId());
                }
                int wrap = this.panelWidth - (icon.isEmpty() ? 0 : ICON_COL);
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
                List<ItemStack> row = new ArrayList<>();
                StringBuilder sub = new StringBuilder();
                int n = 0;
                for (String ref : msg.suggestedItemIds()) {
                    if (n >= MAX_SUGGESTED_ICONS) {
                        break;
                    }
                    ItemStack st = SuggestIcons.resolveRef(ref);
                    if (st.isEmpty()) {
                        continue;
                    }
                    row.add(st);
                    String labelName = SuggestIcons.labelFor(ref, st);
                    if (!sub.isEmpty()) {
                        sub.append("  ");
                    }
                    sub.append(labelName);
                    n++;
                }
                if (!row.isEmpty()) {
                    lines.add(new ChatLine(
                            Component.translatable("packai.screen.suggested_items").getVisualOrderText(),
                            SUGGEST_COLOR));
                    lines.add(new ChatLine(
                            FormattedCharSequence.EMPTY,
                            SUGGEST_COLOR,
                            ItemStack.EMPTY,
                            row,
                            sub.toString()));
                }
            }
            if (!msg.isUser() && msg.hasRecipeCards()) {
                for (RecipeCard card : msg.recipeCards()) {
                    if (card != null && !card.isEmpty()) {
                        lines.add(ChatLine.recipe(card));
                    }
                }
            }
            lines.add(new ChatLine(FormattedCharSequence.EMPTY, color));
        }
        return lines;
    }

    private int lineStride() {
        return this.font.lineHeight + 2;
    }

    /** Rows with item icons / recipe cards need enough height. */
    private int strideOf(ChatLine line) {
        if (line.recipeCard() != null) {
            return recipeCardHeight(line.recipeCard());
        }
        if (!line.icon().isEmpty() || !line.iconRow().isEmpty()) {
            return Math.max(lineStride(), ICON_SIZE + 4);
        }
        return lineStride();
    }

    private int recipeCardHeight(RecipeCard card) {
        int title = this.font.lineHeight + 4;
        if (card.layout() == RecipeCard.Layout.CRAFTING_3X3) {
            return title + 3 * 18 + 6;
        }
        int slots = card.catalysts().size()
                + card.inputs().size()
                + card.outputs().size()
                + card.fluidInputs().size()
                + card.fluidOutputs().size()
                + card.otherInputs().size()
                + card.otherOutputs().size();
        int rowBudget = Math.max(ICON_COL, this.panelWidth - 24);
        int rows = Math.max(1, (slots * ICON_COL + 24 + rowBudget - 1) / rowBudget);
        return title + rows * (ICON_SIZE + 4) + 4;
    }

    private int contentHeight(List<ChatLine> lines) {
        int h = 0;
        for (ChatLine line : lines) {
            h += strideOf(line);
            if (!line.subLabel().isEmpty()) {
                h += lineStride();
            }
        }
        return h;
    }

    private int maxScroll(List<ChatLine> lines) {
        int view = Math.max(1, this.chatBottom - this.chatTop);
        return Math.max(0, contentHeight(lines) - view);
    }

    private void renderRecipeCard(GuiGraphics graphics, RecipeCard card, int left, int top) {
        String cat = Plainify.stripMcFormat(card.categoryTitle());
        Component title = Component.translatable("packai.screen.recipe", cat);
        graphics.drawString(this.font, title, left, top, SUGGEST_COLOR, false);
        int y = top + this.font.lineHeight + 2;
        if (card.layout() == RecipeCard.Layout.CRAFTING_3X3) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int sx = left + col * 18;
                    int sy = y + row * 18;
                    graphics.fill(sx, sy, sx + 16, sy + 16, 0x66000000);
                    ItemStack slot = card.grid().size() > row * 3 + col
                            ? card.grid().get(row * 3 + col)
                            : ItemStack.EMPTY;
                    if (!slot.isEmpty()) {
                        graphics.renderItem(slot, sx, sy);
                        addItemHover(sx, sy, slot);
                    }
                }
            }
            int ox = left + 3 * 18 + 10;
            int oy = y + 18;
            graphics.drawString(this.font, "->", left + 3 * 18 + 2, oy + 4, 0xA0A0A0, false);
            graphics.fill(ox, oy, ox + 16, oy + 16, 0x66000000);
            if (!card.outputs().isEmpty()) {
                graphics.renderItem(card.outputs().get(0), ox, oy);
                addItemHover(ox, oy, card.outputs().get(0));
            }
            return;
        }

        // FLOW: catalysts | others/fluids/items -> fluids/items/others
        int x = left;
        int rowStart = left;
        int maxX = left + this.panelWidth - 4;
        int[] yy = {y};
        if (!card.catalysts().isEmpty()) {
            for (ItemStack st : card.catalysts()) {
                x = wrapFlowX(x, rowStart, maxX, yy);
                drawItemSlot(graphics, st, x, yy[0], 0x44004466);
                x += ICON_COL;
            }
            graphics.drawString(this.font, ":", x - 2, yy[0] + 4, 0x888888, false);
            x += 6;
        }
        for (RecipeExtra extra : card.otherInputs()) {
            x = wrapFlowX(x, rowStart, maxX, yy);
            drawExtraSlot(graphics, extra, x, yy[0]);
            x += ICON_COL;
        }
        for (FluidStack fluid : card.fluidInputs()) {
            x = wrapFlowX(x, rowStart, maxX, yy);
            drawFluidSlot(graphics, fluid, x, yy[0]);
            x += ICON_COL;
        }
        for (ItemStack st : card.inputs()) {
            x = wrapFlowX(x, rowStart, maxX, yy);
            drawItemSlot(graphics, st, x, yy[0], 0x66000000);
            x += ICON_COL;
        }
        x = wrapFlowX(x, rowStart, maxX, yy);
        graphics.drawString(this.font, "->", x, yy[0] + 4, 0xA0A0A0, false);
        x += 14;
        for (FluidStack fluid : card.fluidOutputs()) {
            x = wrapFlowX(x, rowStart, maxX, yy);
            drawFluidSlot(graphics, fluid, x, yy[0]);
            x += ICON_COL;
        }
        for (ItemStack st : card.outputs()) {
            x = wrapFlowX(x, rowStart, maxX, yy);
            drawItemSlot(graphics, st, x, yy[0], 0x66000000);
            x += ICON_COL;
        }
        for (RecipeExtra extra : card.otherOutputs()) {
            x = wrapFlowX(x, rowStart, maxX, yy);
            drawExtraSlot(graphics, extra, x, yy[0]);
            x += ICON_COL;
        }
    }

    private static int wrapFlowX(int x, int rowStart, int maxX, int[] yy) {
        if (x + ICON_SIZE > maxX) {
            yy[0] += ICON_SIZE + 4;
            return rowStart;
        }
        return x;
    }

    private void drawItemSlot(GuiGraphics graphics, ItemStack stack, int x, int y, int bg) {
        graphics.fill(x, y, x + 16, y + 16, bg);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x, y);
            addItemHover(x, y, stack);
        }
    }

    private void drawFluidSlot(GuiGraphics graphics, FluidStack fluid, int x, int y) {
        graphics.fill(x, y, x + 16, y + 16, 0x66000000);
        if (fluid == null || fluid.isEmpty()) {
            return;
        }
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation still = ext.getStillTexture(fluid);
        int color = ext.getTintColor(fluid);
        if (still != null) {
            TextureAtlasSprite sprite = this.minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(still);
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            graphics.blit(x + 1, y + 1, 0, 14, 14, sprite, r, g, b, 1f);
        } else {
            graphics.fill(x + 1, y + 1, x + 15, y + 15, 0xFF000000 | (color & 0xFFFFFF));
        }
        addFluidHover(x, y, fluid);
    }

    private void drawExtraSlot(GuiGraphics graphics, RecipeExtra extra, int x, int y) {
        if (extra == null || extra.isEmpty()) {
            return;
        }
        graphics.fill(x, y, x + 16, y + 16, 0x66000000);
        boolean drawn = !extra.softId().isBlank()
                && JeiSoftIngredients.render(graphics, extra.softId(), x, y);
        if (!drawn) {
            int tint = extra.tint() | 0xFF000000;
            graphics.fill(x + 2, y + 1, x + 14, y + 15, tint);
            graphics.fill(x + 3, y + 2, x + 13, y + 14, 0x44000000);
            graphics.drawCenteredString(this.font, "G", x + 8, y + 4, 0xFFFFFF);
        }
        List<Component> tip = !extra.softId().isBlank()
                ? JeiSoftIngredients.tooltip(extra.softId())
                : List.of();
        if (tip.isEmpty()) {
            tip = List.of(Component.literal(Plainify.stripMcFormat(extra.tooltipLine())));
        }
        addTextHover(x, y, tip);
    }

    private void addItemHover(int x, int y, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        this.hoverHits.add(new HoverHit(x, y, x + ICON_SIZE, y + ICON_SIZE, stack.copy(), FluidStack.EMPTY, List.of()));
    }

    private void addFluidHover(int x, int y, FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return;
        }
        this.hoverHits.add(new HoverHit(x, y, x + ICON_SIZE, y + ICON_SIZE, ItemStack.EMPTY, fluid.copy(), List.of()));
    }

    private void addTextHover(int x, int y, List<Component> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        this.hoverHits.add(new HoverHit(x, y, x + ICON_SIZE, y + ICON_SIZE, ItemStack.EMPTY, FluidStack.EMPTY, List.copyOf(lines)));
    }

    private void renderHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (HoverHit hit : this.hoverHits) {
            if (mouseX < hit.x0 || mouseX >= hit.x1 || mouseY < hit.y0 || mouseY >= hit.y1) {
                continue;
            }
            if (!hit.item.isEmpty()) {
                graphics.renderTooltip(this.font, hit.item, mouseX, mouseY);
            } else if (!hit.fluid.isEmpty()) {
                List<Component> tip = new ArrayList<>();
                tip.add(hit.fluid.getHoverName());
                tip.add(Component.literal(hit.fluid.getAmount() + " mB"));
                graphics.renderTooltip(this.font, tip, Optional.empty(), mouseX, mouseY);
            } else if (!hit.text.isEmpty()) {
                graphics.renderTooltip(this.font, hit.text, Optional.empty(), mouseX, mouseY);
            }
            return;
        }
    }

    private record HoverHit(
            int x0,
            int y0,
            int x1,
            int y1,
            ItemStack item,
            FluidStack fluid,
            List<Component> text
    ) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.hoverHits.clear();
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
        graphics.fill(this.sideLeft - 2, this.chatTop - 4,
                this.sideLeft + this.sideWidth + 2, this.chatBottom + 2, 0x44000000);

        graphics.enableScissor(this.panelLeft - 2, this.chatTop, this.panelLeft + this.panelWidth + 2, this.chatBottom);
        int y = this.chatTop - (int) this.scrollOffset;
        for (ChatLine line : lines) {
            int stride = strideOf(line);
            if (y + stride >= this.chatTop && y <= this.chatBottom) {
                if (line.recipeCard() != null) {
                    renderRecipeCard(graphics, line.recipeCard(), this.panelLeft, y);
                } else {
                    int textY = y + Math.max(0, (stride - this.font.lineHeight) / 2);
                    if (!line.icon().isEmpty()) {
                        graphics.renderItem(line.icon(), this.panelLeft, y);
                        addItemHover(this.panelLeft, y, line.icon());
                        if (line.text() != FormattedCharSequence.EMPTY) {
                            graphics.drawString(this.font, line.text(), this.panelLeft + ICON_COL, textY,
                                    line.color(), false);
                        }
                    } else if (!line.iconRow().isEmpty()) {
                        int ix = this.panelLeft;
                        for (ItemStack st : line.iconRow()) {
                            graphics.renderItem(st, ix, y);
                            addItemHover(ix, y, st);
                            ix += ICON_COL;
                        }
                    } else if (line.text() != FormattedCharSequence.EMPTY) {
                        graphics.drawString(this.font, line.text(), this.panelLeft, textY, line.color(), false);
                    }
                }
                if (!line.subLabel().isEmpty()) {
                    graphics.drawString(this.font, line.subLabel(), this.panelLeft + 4, y + stride,
                            0x888888, false);
                }
            }
            y += stride;
            if (!line.subLabel().isEmpty()) {
                y += lineStride();
            }
        }
        graphics.disableScissor();

        if (max > 0) {
            graphics.drawString(this.font, Component.translatable("packai.screen.chat_scroll"),
                    this.panelLeft, this.chatBottom + 2, 0x888888, false);
        }
        renderHoverTooltip(graphics, mouseX, mouseY);
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
}

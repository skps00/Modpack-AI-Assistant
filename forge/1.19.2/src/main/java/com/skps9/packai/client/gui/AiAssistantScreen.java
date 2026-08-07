package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skps9.packai.client.ClientSetup;
import com.skps9.packai.client.QuestBookOpener;
import com.skps9.packai.client.ReplyNotifier;
import com.skps9.packai.client.chat.ChatMessage;
import com.skps9.packai.client.chat.ChatSession;
import com.skps9.packai.client.jei.JeiSoftIngredients;
import com.skps9.packai.client.jei.JeiTargetResolver;
import com.skps9.packai.client.jei.SuggestIcons;
import com.skps9.packai.client.knowledge.ItemSearch;
import com.skps9.packai.client.knowledge.PackKnowledge;
import com.skps9.packai.client.service.AskService;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.AskResult;
import com.skps9.packai.logic.ItemRef;
import com.skps9.packai.logic.ItemResolver;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.QuestGuide;
import com.skps9.packai.logic.RecipeCard;
import com.skps9.packai.logic.RecipeEmbed;
import com.skps9.packai.logic.RecipeExtra;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

/**
 * Cursor-like multi-turn chat UI with scrollable history and docked input.
 */
public class AiAssistantScreen extends Screen {
    private static final int MAX_QUEST_SLOTS = 3;
    private static final int MAX_SUGGESTED_ICONS = 8;
    private static final int ICON_SIZE = 16;
    private static final int ICON_COL = 20;
    /** Vanilla crafting / JEI slot step (matches {@code JeiRecipeCards.JEI_SLOT_STRIDE}). */
    private static final int CRAFTING_SLOT_STRIDE = 18;
    private static final int USER_COLOR = 0xA0C8FF;
    private static final int AI_COLOR = 0xE0E0E0;
    private static final int SUGGEST_COLOR = 0xFFD080;
    private static final int SEARCH_ROW_H = 16;
    private static final int SEARCH_MAX_HITS = ItemSearch.DEFAULT_LIMIT;

    private final List<HoverHit> hoverHits = new ArrayList<>();
    private final List<SearchHitRect> searchHitRects = new ArrayList<>();
    private EditBox input;
    private EditBox searchBox;
    private String draftInput = "";
    private String draftSearch = "";
    private List<ItemSearch.Hit> searchHits = List.of();
    /** Strip/ask focus after last ask — ignores live JEI hover while screen open. */
    private ItemStack lastAskFocus = ItemStack.EMPTY;
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
                title = title.substring(0, 14) + "...";
            }
            QuestGuide.Hit openHit = hit;
            this.addRenderableWidget(WidgetCompat.button(
                    this.sideLeft, sy, sw, btnH,
                    Component.translatable("packai.screen.open_quest_short", title),
                    b -> QuestBookOpener.open(openHit)));
            sy += btnH + btnGap;
            if (questCount > 1) {
                int shown = this.questIndex + 1;
                this.addRenderableWidget(WidgetCompat.button(
                        this.sideLeft, sy, sw, btnH,
                        Component.translatable("packai.screen.quest_more", shown, questCount),
                        b -> {
                            this.questIndex = (this.questIndex + 1) % questCount;
                            rememberDraft();
                            rebuildUi();
                        }));
                sy += btnH + btnGap;
            }
            sy += 4;
        }

        Button sendBtn = WidgetCompat.button(this.sideLeft, sy, sw, btnH,
                Component.translatable("packai.screen.send"), b -> sendCurrent());
        sendBtn.active = !busy;
        this.addRenderableWidget(sendBtn);
        sy += btnH + btnGap;

        Button regenBtn = WidgetCompat.button(this.sideLeft, sy, sw, btnH,
                Component.translatable("packai.screen.regenerate"), b -> regenerate());
        regenBtn.active = ChatSession.canRegenerate();
        this.addRenderableWidget(regenBtn);
        sy += btnH + btnGap;

        this.addRenderableWidget(WidgetCompat.button(this.sideLeft, sy, sw, btnH,
                Component.translatable("packai.screen.clear_chat"), b -> clearChat()));
        sy += btnH + btnGap;

        int pickCount = ChatSession.pendingItemCount();
        Component pickLabel = pickCount > 0
                ? Component.translatable("packai.screen.pick_items_n", pickCount)
                : Component.translatable("packai.screen.pick_items");
        this.addRenderableWidget(WidgetCompat.button(this.sideLeft, sy, sw, btnH, pickLabel, b -> {
            rememberDraft();
            if (this.minecraft != null) {
                this.minecraft.setScreen(new InvPickScreen(this));
            }
        }));
        sy += btnH + btnGap;

        this.searchBox = new EditBox(this.font, this.sideLeft, sy, sw, 16,
                Component.translatable("packai.screen.search_hint"));
        this.searchBox.setMaxLength(128);
        if (!this.draftSearch.isEmpty()) {
            this.searchBox.setValue(this.draftSearch);
        }
        this.searchBox.setResponder(this::onSearchChanged);
        this.searchBox.setEditable(!busy);
        this.addRenderableWidget(this.searchBox);
        onSearchChanged(this.searchBox.getValue());
        sy += 18 + btnGap;

        this.addRenderableWidget(WidgetCompat.button(this.sideLeft, sy, sw, btnH,
                Component.translatable("packai.screen.quest_next"),
                b -> askTemplate("packai.ask.quest_next", null, null, false),
                Component.translatable("packai.screen.tooltip.quest_next")));
        sy += btnH + btnGap;

        this.addRenderableWidget(WidgetCompat.button(this.sideLeft, sy, sw, btnH,
                Component.translatable("packai.screen.next_step_short"),
                b -> askNextStep(),
                Component.translatable("packai.screen.tooltip.next_step")));
        sy += btnH + btnGap + 6;

        this.addRenderableWidget(WidgetCompat.button(this.sideLeft, sy, sw, btnH,
                Component.translatable("packai.screen.jump_latest"), b -> {
                    this.stickToBottom = true;
                    this.scrollOffset = maxScroll(chatLines());
                }));
        sy += btnH + btnGap;

        this.addRenderableWidget(WidgetCompat.button(this.sideLeft, sy, sw, btnH,
                Component.translatable("packai.screen.settings"), b -> {
                    rememberDraft();
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new PackAiSettingsScreen(this));
                    }
                }));

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
        if (this.searchBox != null) {
            this.draftSearch = this.searchBox.getValue();
        }
    }

    private void onSearchChanged(String raw) {
        this.draftSearch = raw == null ? "" : raw;
        this.searchHits = PackKnowledge.searchItems(this.draftSearch, SEARCH_MAX_HITS);
    }

    /** Click search hit: pin + pending focus (like InvPick). askNow → same get+use as hold-Y. */
    private void applySearchHit(ItemStack stack, boolean askNow) {
        if (stack == null || stack.isEmpty() || ChatSession.isBusy()) {
            return;
        }
        JeiTargetResolver.pin(stack);
        ChatSession.setPendingItems(List.of(AskService.fromStack(stack)));
        this.lastAskFocus = stack.copy();
        if (this.searchBox != null) {
            this.searchBox.setValue("");
        }
        this.draftSearch = "";
        this.searchHits = List.of();
        if (askNow) {
            askAboutStack(stack);
        }
    }

    private void clearChat() {
        if (ChatSession.isBusy()) {
            return;
        }
        JeiTargetResolver.clearPin();
        this.lastAskFocus = ItemStack.EMPTY;
        ChatSession.clear();
        this.scrollOffset = 0;
        this.stickToBottom = true;
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
        startAsk(q, ChatSession.pendingItems(), false, ChatSession.recentForLlm(), true, null, null, null);
    }

    /**
     * Targeted next: strip focus / pending only — never dump hotbar into pending.
     * Empty pending = focus-only ask (same as Ask == strip). No focus and no picks → toast.
     */
    private void askNextStep() {
        if (this.minecraft == null || ChatSession.isBusy()) {
            return;
        }
        ItemStack focus = contextStack();
        List<ItemRef> pending = ChatSession.pendingItems();
        if (pending.isEmpty() && (focus == null || focus.isEmpty())) {
            if (this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        Component.translatable("packai.status.need_target"), true);
            }
            return;
        }
        askTemplate("packai.ask.held_next", null, null, false);
    }

    private void askTemplate(
            String templateKey,
            String arg0,
            String arg1,
            boolean questOverride
    ) {
        String q = resolveTemplate(templateKey, arg0, arg1);
        // Template injects mod:id — pin so strip preview matches ask focus (draft may lack id).
        if (JeiTargetResolver.pinnedOrEmpty().isEmpty()
                && arg1 != null
                && arg1.indexOf(':') > 0) {
            ItemStack fromId = ItemResolver.stackFromId(arg1);
            if (!fromId.isEmpty()) {
                JeiTargetResolver.pin(fromId);
            }
        }
        startAsk(q, ChatSession.pendingItems(), questOverride, ChatSession.recentForLlm(), true, templateKey, arg0, arg1);
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

    /**
     * Y / ThinkHold entry: focused ask on hovered item only.
     * Clears InvPick multi-select so pending strip/extras do not bleed in;
     * chat history kept. InvPick Ask button still uses pending as-is.
     */
    private void askAboutStack(ItemStack stack) {
        String name = stack.getHoverName().getString();
        var key = Registry.ITEM.getKey(stack.getItem());
        String id = key == null ? "" : key.toString();
        if (id.isEmpty()) {
            ChatSession.clearPendingItems();
            askTemplate("packai.ask.item_about", name, null, false);
        } else {
            ChatSession.setPendingItems(List.of(AskService.fromStack(stack)));
            askTemplate("packai.ask.item_about_id", name, id, false);
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
        if (r.hasTemplate()) {
            question = resolveTemplate(templateKey, arg0, arg1);
            ChatSession.replaceLastUserText(question);
        }
        ChatSession.setPendingItems(r.selectedItems());
        // Regen: keep strip/ask on original user bubble item (not live JEI hover).
        ItemStack priorIcon = ItemStack.EMPTY;
        List<ChatMessage> snap = ChatSession.snapshot();
        if (!snap.isEmpty()) {
            ChatMessage last = snap.get(snap.size() - 1);
            if (last.isUser()) {
                priorIcon = last.iconOrEmpty();
            }
        }
        if (!priorIcon.isEmpty()) {
            JeiTargetResolver.pin(priorIcon);
        } else if (r.hasTemplate()
                && r.templateArg1() != null
                && r.templateArg1().indexOf(':') > 0
                && JeiTargetResolver.pinnedOrEmpty().isEmpty()) {
            ItemStack fromId = ItemResolver.stackFromId(r.templateArg1());
            if (!fromId.isEmpty()) {
                JeiTargetResolver.pin(fromId);
            }
        }
        startAsk(question, r.selectedItems(), r.questOverride(), r.prior(), false, templateKey, arg0, arg1);
    }

    private void startAsk(
            String question,
            List<ItemRef> selectedItems,
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
        List<ItemRef> selected = selectedItems == null ? List.of() : selectedItems;
        ItemStack held = contextStack();
        if (held.isEmpty() && !selected.isEmpty()) {
            held = ItemResolver.stackFromRef(selected.get(0));
        }
        // Lock strip to this ask's focus (live JEI hover ignored while screen open).
        if (!held.isEmpty()) {
            this.lastAskFocus = held.copy();
        }
        // Chat bubble lists ALL pending picks: [A][B] … — not only strip focus.
        String itemLabel = selectedItemLabels(selected, held);
        String itemId = heldItemId(held);
        ChatSession.setBusy(true);
        ChatSession.setLastQuests(List.of());
        ChatSession.setLastAsk(new ChatSession.LastAsk(
                question, selected, questOverride, templateKey, templateArg0, templateArg1));
        if (appendUser) {
            ChatSession.append(ChatMessage.user(question, itemLabel, itemId, held));
        }
        ChatSession.append(ChatMessage.assistant(
                Component.translatable("packai.status.waiting").getString()));
        this.stickToBottom = true;
        rememberDraft();
        rebuildUi();

        ClientSetup.askService().askAsync(
                question, selected, questOverride, prior, held, AiAssistantScreen::onAskFinished);
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
            open.rebuildUi();
        } else {
            ReplyNotifier.alertReplyReady(quests);
        }
    }

    /**
     * Stable strip/ask focus while assistant open: pin, id in draft, pending picks, lastAskFocus.
     * Never live JEI ingredient-list hover (that stuck「黑暗祭壇」over cursed_ingot ask).
     * Sticky lastAskFocus is ignored when InvPick pending no longer contains it.
     */
    private ItemStack contextStack() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        String draft = this.input == null ? this.draftInput : this.input.getValue();
        ItemStack stable = JeiTargetResolver.resolveStable(this.minecraft, draft == null ? "" : draft);
        if (!stable.isEmpty()) {
            return stable;
        }
        List<ItemRef> pending = ChatSession.pendingItems();
        if (!pending.isEmpty()) {
            if (!this.lastAskFocus.isEmpty()) {
                String lid = heldItemId(this.lastAskFocus);
                for (ItemRef ref : pending) {
                    if (ref.isPresent() && ref.id().equalsIgnoreCase(lid)) {
                        // Prefer InvPick sample NBT over sticky bare rebuild.
                        if (ref.hasSample()) {
                            return ItemResolver.stackFromRef(ref);
                        }
                        return this.lastAskFocus;
                    }
                }
            }
            return ItemResolver.stackFromRef(pending.get(0));
        }
        return this.lastAskFocus.isEmpty() ? ItemStack.EMPTY : this.lastAskFocus;
    }

    /** ``name`` or ``a][b][c`` so chat renders ``[a][b][c]``. Cap = pending max. */
    static String selectedItemLabels(List<ItemRef> selected, ItemStack fallback) {
        if (selected != null && !selected.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (ItemRef ref : selected) {
                if (ref == null || !ref.isPresent()) {
                    continue;
                }
                if (n >= ChatSession.MAX_PENDING_ITEMS) {
                    break;
                }
                if (sb.length() > 0) {
                    sb.append("][");
                }
                String name = ref.label();
                if (name == null || name.isBlank()) {
                    name = ref.id();
                }
                sb.append(Plainify.stripMcFormat(name));
                n++;
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        return heldItemLabel(fallback);
    }

    private static String heldItemLabel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Component.translatable("packai.screen.held_empty").getString();
        }
        return Plainify.stripMcFormat(stack.getHoverName().getString());
    }

    private static String heldItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        var key = Registry.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private void renderInputHeldStrip(GuiGraphics graphics) {
        ItemStack focus = contextStack();
        List<ItemRef> pending = ChatSession.pendingItems();
        String focusId = heldItemId(focus);
        boolean focusInPending = false;
        if (!focusId.isEmpty()) {
            for (ItemRef ref : pending) {
                if (ref.isPresent() && focusId.equalsIgnoreCase(ref.id())) {
                    focusInPending = true;
                    break;
                }
            }
        }

        int y = this.inputY - 16;
        int x = this.panelLeft;
        // JEI/recipe focus lead icon when not already among pending picks.
        if (!focus.isEmpty() && !focusInPending) {
            graphics.renderItem(focus, x, y - 1);
            addItemHover(x, y - 1, focus);
            x += ICON_COL;
        }
        int shown = 0;
        for (ItemRef ref : pending) {
            if (shown >= ChatSession.MAX_PENDING_ITEMS) {
                break;
            }
            if (!ref.isPresent()) {
                continue;
            }
            ItemStack stack = ItemResolver.stackFromRef(ref);
            if (stack.isEmpty()) {
                continue;
            }
            graphics.renderItem(stack, x, y - 1);
            addItemHover(x, y - 1, stack);
            x += ICON_COL;
            shown++;
        }

        Component line;
        // Pending owns the strip label — never show sticky「目标」when picks exist.
        if (!pending.isEmpty()) {
            line = Component.translatable("packai.screen.picked_n", pending.size());
        } else {
            line = Component.translatable("packai.screen.targeted_item", heldItemLabel(focus));
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
        if (this.cachedChatLines != null
                && this.cachedChatGen == ChatSession.generation()
                && this.cachedChatPanelWidth == this.panelWidth) {
            return this.cachedChatLines;
        }
        List<ChatLine> lines = buildChatLines();
        this.cachedChatLines = lines;
        this.cachedChatGen = ChatSession.generation();
        this.cachedChatPanelWidth = this.panelWidth;
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
            } else if (msg.isUser()) {
                String block = label + body;
                for (FormattedCharSequence part : this.font.split(Component.literal(block), this.panelWidth)) {
                    lines.add(new ChatLine(part, color));
                }
            } else {
                appendAssistantBody(lines, label, body, color, msg.recipeCards());
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
            lines.add(new ChatLine(FormattedCharSequence.EMPTY, color));
        }
        return lines;
    }

    private void appendAssistantBody(
            List<ChatLine> lines,
            String label,
            String body,
            int color,
            List<RecipeCard> recipeCards
    ) {
        List<RecipeCard> cards = recipeCards == null ? List.of() : recipeCards;
        List<RecipeEmbed.Part> parts = RecipeEmbed.parts(body, cards);
        if (parts.isEmpty()) {
            for (FormattedCharSequence part : this.font.split(Component.literal(label), this.panelWidth)) {
                lines.add(new ChatLine(part, color));
            }
            return;
        }
        boolean firstText = true;
        for (RecipeEmbed.Part part : parts) {
            if (part.isCard()) {
                int idx = part.cardIndex();
                if (idx >= 0 && idx < cards.size()) {
                    RecipeCard card = cards.get(idx);
                    if (card != null && !card.isEmpty()) {
                        lines.add(ChatLine.recipe(card));
                    }
                }
                continue;
            }
            if (part.isItem()) {
                ItemStack stack = ItemResolver.stackFromId(part.text());
                if (stack.isEmpty()) {
                    // Soft-fail: leave registry id as plain text.
                    String id = part.text() == null ? "" : part.text();
                    if (!id.isEmpty()) {
                        String block = firstText ? label + id : id;
                        firstText = false;
                        for (FormattedCharSequence fp : this.font.split(Component.literal(block), this.panelWidth)) {
                            lines.add(new ChatLine(fp, color));
                        }
                    }
                    continue;
                }
                String name = Plainify.stripMcFormat(stack.getHoverName().getString());
                String block = firstText ? label + name : name;
                firstText = false;
                int wrap = Math.max(40, this.panelWidth - ICON_COL);
                List<FormattedCharSequence> fps = this.font.split(Component.literal(block), wrap);
                boolean first = true;
                for (FormattedCharSequence fp : fps) {
                    lines.add(new ChatLine(fp, color, first ? stack : ItemStack.EMPTY));
                    first = false;
                }
                continue;
            }
            String chunk = part.text() == null ? "" : part.text();
            if (chunk.isEmpty()) {
                continue;
            }
            String block = firstText ? label + chunk : chunk;
            firstText = false;
            for (FormattedCharSequence fp : this.font.split(Component.literal(block), this.panelWidth)) {
                lines.add(new ChatLine(fp, color));
            }
        }
    }

    private int lineStride() {
        return this.font.lineHeight + 2;
    }

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
            return title + 3 * CRAFTING_SLOT_STRIDE + 6;
        }
        if (card.layout() == RecipeCard.Layout.SHAPED) {
            int shapedH = shapedBoundsHeight(card);
            int extras = card.catalysts().size()
                    + card.fluidInputs().size() + card.fluidOutputs().size()
                    + card.otherInputs().size() + card.otherOutputs().size()
                    + card.outputs().size();
            int extraRows = extras <= 0 ? 0 : 1 + (extras - 1) / Math.max(1, this.panelWidth / ICON_COL);
            int tip = shapedNeedsPreviewTip(card) ? this.font.lineHeight + 2 : 0;
            return title + shapedH + tip + extraRows * (ICON_SIZE + 4) + 8;
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

    private static final int MAX_SHAPED_CARD_H = 168; // Create 9×9 JEI ≈160px; chat can scroll

    private float shapedScale(RecipeCard card) {
        List<RecipeCard.PlacedItem> placed = card.placedInputs();
        if (placed == null || placed.isEmpty()) {
            return 1.0f;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (RecipeCard.PlacedItem p : placed) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }
        int bw = Math.max(ICON_SIZE, maxX - minX + ICON_SIZE);
        int bh = Math.max(ICON_SIZE, maxY - minY + ICON_SIZE);
        int maxW = Math.max(48, this.panelWidth - 28);
        return Math.min(1.0f, Math.min(maxW / (float) bw, MAX_SHAPED_CARD_H / (float) bh));
    }

    private boolean shapedNeedsPreviewTip(RecipeCard card) {
        return shapedScale(card) < 0.999f
                || (card.categoryTitle() != null && card.categoryTitle().contains("grid truncated"));
    }

    private int shapedBoundsHeight(RecipeCard card) {
        List<RecipeCard.PlacedItem> placed = card.placedInputs();
        if (placed == null || placed.isEmpty()) {
            return ICON_SIZE + 4;
        }
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (RecipeCard.PlacedItem p : placed) {
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
        }
        int bh = Math.max(ICON_SIZE, maxY - minY + ICON_SIZE);
        float scale = shapedScale(card);
        return Math.max(ICON_SIZE + 4, Math.round(bh * scale) + 4);
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
                    int sx = left + col * CRAFTING_SLOT_STRIDE;
                    int sy = y + row * CRAFTING_SLOT_STRIDE;
                    graphics.fill(sx, sy, sx + ICON_SIZE, sy + ICON_SIZE, 0x66000000);
                    ItemStack slot = card.grid().size() > row * 3 + col
                            ? card.grid().get(row * 3 + col)
                            : ItemStack.EMPTY;
                    if (!slot.isEmpty()) {
                        graphics.renderItem(slot, sx, sy);
                        addItemHover(sx, sy, slot);
                    }
                }
            }
            int ox = left + 3 * CRAFTING_SLOT_STRIDE + 10;
            int oy = y + CRAFTING_SLOT_STRIDE;
            graphics.drawString(this.font, "->", left + 3 * CRAFTING_SLOT_STRIDE + 2, oy + 4, 0xA0A0A0, false);
            graphics.fill(ox, oy, ox + ICON_SIZE, oy + ICON_SIZE, 0x66000000);
            if (!card.outputs().isEmpty()) {
                graphics.renderItem(card.outputs().get(0), ox, oy);
                addItemHover(ox, oy, card.outputs().get(0));
            }
            return;
        }

        if (card.layout() == RecipeCard.Layout.SHAPED) {
            y = renderShapedInputs(graphics, card, left, y);
            if (shapedNeedsPreviewTip(card)) {
                graphics.drawString(
                        this.font,
                        Component.translatable("packai.screen.recipe_grid_preview"),
                        left,
                        y,
                        0xA0A0A0,
                        false);
                y += this.font.lineHeight + 2;
            }
            // Footer: catalysts / fluids / soft / outputs in a compact FLOW row.
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
            return;
        }

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

    /** Draw JEI-shaped inputs scaled to panel width; returns y below the shaped block. */
    private int renderShapedInputs(GuiGraphics graphics, RecipeCard card, int left, int top) {
        List<RecipeCard.PlacedItem> placed = card.placedInputs();
        if (placed == null || placed.isEmpty()) {
            return top;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (RecipeCard.PlacedItem p : placed) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }
        int bh = Math.max(ICON_SIZE, maxY - minY + ICON_SIZE);
        float scale = shapedScale(card);
        // When scale < 1, still draw ICON_SIZE icons at scaled positions (may overlap slightly — ok).
        int step = Math.max(10, Math.round(ICON_SIZE * scale));
        for (RecipeCard.PlacedItem p : placed) {
            int sx = left + Math.round((p.x() - minX) * scale);
            int sy = top + Math.round((p.y() - minY) * scale);
            if (sx + ICON_SIZE > left + this.panelWidth) {
                continue; // clip horizontally
            }
            graphics.fill(sx, sy, sx + Math.min(ICON_SIZE, step), sy + Math.min(ICON_SIZE, step), 0x66000000);
            if (!p.stack().isEmpty()) {
                graphics.renderItem(p.stack(), sx, sy);
                addItemHover(sx, sy, p.stack());
            }
        }
        return top + Math.round(bh * scale) + 4;
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
        TextureAtlasSprite sprite = null;
        if (this.minecraft != null) {
            var still = ext.getStillTexture(fluid);
            if (still != null) {
                sprite = this.minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(still);
            }
        }
        int color = ext.getTintColor(fluid);
        if (sprite != null) {
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            graphics.blit(x + 1, y + 1, 0, 14, 14, sprite, r, g, b, 1.0F);
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
        // Last-added wins (strip icons registered after chat so they stay on top).
        for (int i = this.hoverHits.size() - 1; i >= 0; i--) {
            HoverHit hit = this.hoverHits.get(i);
            if (mouseX < hit.x0 || mouseX >= hit.x1 || mouseY < hit.y0 || mouseY >= hit.y1) {
                continue;
            }
            if (!hit.item.isEmpty()) {
                graphics.renderTooltip(this.font, hit.item, mouseX, mouseY);
            } else if (!hit.fluid.isEmpty()) {
                List<Component> tip = new ArrayList<>();
                tip.add(hit.fluid.getDisplayName());
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

    private void renderScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.hoverHits.clear();
        this.renderBackground(graphics.pose());
        super.render(graphics.pose(), mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

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
                    this.panelLeft, this.chatBottom - this.font.lineHeight, 0x888888, false);
        }
        // After chat panel so icons + hover hits sit above fills / scroll hint.
        renderSearchHits(graphics);
        renderInputHeldStrip(graphics);
        renderHoverTooltip(graphics, mouseX, mouseY);
    }

    private void renderSearchHits(GuiGraphics graphics) {
        this.searchHitRects.clear();
        if (this.searchHits.isEmpty()) {
            return;
        }
        int n = Math.min(this.searchHits.size(), SEARCH_MAX_HITS);
        int boxH = n * SEARCH_ROW_H + 4;
        int top = this.inputY - 16 - 6 - boxH;
        int left = this.panelLeft;
        int right = this.panelLeft + this.panelWidth;
        graphics.fill(left - 2, top, right + 2, top + boxH, 0xCC101018);
        int y = top + 2;
        for (int i = 0; i < n; i++) {
            ItemSearch.Hit hit = this.searchHits.get(i);
            ItemStack stack = hit.stack();
            graphics.renderItem(stack, left, y);
            String label = ellipsize(hit.label().isBlank() ? hit.id() : hit.label(), this.panelWidth - 22);
            graphics.drawString(this.font, label, left + 18, y + 4, 0xE0E0E0, false);
            this.searchHitRects.add(new SearchHitRect(left, y, right, y + SEARCH_ROW_H, stack));
            addItemHover(left, y, stack);
            y += SEARCH_ROW_H;
        }
    }

    private String ellipsize(String s, int maxPx) {
        if (s == null) {
            return "";
        }
        if (this.font.width(s) <= maxPx) {
            return s;
        }
        String cur = s;
        while (cur.length() > 1 && this.font.width(cur + "…") > maxPx) {
            cur = cur.substring(0, cur.length() - 1);
        }
        return cur + "…";
    }

    private record SearchHitRect(int x0, int y0, int x1, int y1, ItemStack stack) {}

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderScreen(new GuiGraphics(this.minecraft, this, pose), mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            for (SearchHitRect hit : this.searchHitRects) {
                if (mouseX >= hit.x0() && mouseX < hit.x1() && mouseY >= hit.y0() && mouseY < hit.y1()) {
                    boolean askNow = button == 1 || hasShiftDown();
                    applySearchHit(hit.stack(), askNow);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (mouseY >= this.chatTop && mouseY <= this.chatBottom
                && mouseX >= this.panelLeft - 4 && mouseX <= this.panelLeft + this.panelWidth + 4) {
            this.stickToBottom = false;
            List<ChatLine> lines = chatLines();
            this.scrollOffset = Mth.clamp(
                    this.scrollOffset - scrollDelta * lineStride() * 2, 0, maxScroll(lines));
            if (this.scrollOffset >= maxScroll(lines) - 1) {
                this.stickToBottom = true;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && this.searchBox != null && this.searchBox.isFocused()) {
            if (!this.searchHits.isEmpty()) {
                applySearchHit(this.searchHits.get(0).stack(), hasShiftDown());
                return true;
            }
        }
        if ((keyCode == 257 || keyCode == 335) && this.input != null && this.input.isFocused()) {
            sendCurrent();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        // Drop pin on close; lastAskFocus lives only while this screen instance exists.
        JeiTargetResolver.clearPin();
        rememberDraft();
        super.onClose();
    }
}

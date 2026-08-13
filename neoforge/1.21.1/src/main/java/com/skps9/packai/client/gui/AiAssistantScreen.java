package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.skps9.packai.client.ClientSetup;
import com.skps9.packai.client.QuestBookOpener;
import com.skps9.packai.client.ReplyNotifier;
import com.skps9.packai.client.chat.ChatMessage;
import com.skps9.packai.client.chat.ChatSession;
import com.skps9.packai.client.jei.JeiLayoutDraw;
import com.skps9.packai.client.jei.JeiTargetResolver;
import com.skps9.packai.client.jei.JeiSoftIngredients;
import com.skps9.packai.client.jei.SuggestIcons;
import com.skps9.packai.client.knowledge.PackKnowledge;
import com.skps9.packai.client.service.AskService;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.AskResult;
import com.skps9.packai.logic.ItemRef;
import com.skps9.packai.logic.ItemResolver;
import com.skps9.packai.logic.FormatRequirements;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.QuestGuide;
import com.skps9.packai.logic.RecipeCard;
import com.skps9.packai.logic.RecipeEmbed;
import com.skps9.packai.logic.RecipeExtra;
import com.skps9.packai.logic.TokenUsage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
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
    private static final int MAX_SUGGESTED_ICONS = 8;
    /** Standard item bitmap; oversized mod renders are clipped to this. */
    private static final int ICON_SIZE = 16;
    /** Icon column width including gap before text. */
    private static final int ICON_COL = 20;
    /** Vanilla crafting / JEI slot step (matches {@code JeiRecipeCards.JEI_SLOT_STRIDE}). */
    private static final int CRAFTING_SLOT_STRIDE = 18;
    /** Px between caption row and JEI card top (card ChatLine extraPad). */
    private static final int CAPTION_TO_CARD_GAP = 4;
    /** Trailing px inside recipe-card stride after body/footer (not a full blank ChatLine). */
    private static final int CARD_BODY_TAIL = 4;
    /** Chat overflow below JEI getRect for clock/flame — less than {@link JeiLayoutDraw#OUTSIDE_DRAW_PAD}. */
    private static final int CARD_OVERFLOW_PAD = 6;
    /** Extra pad on numbered step rows (1. / 2.) — keep readable, not airy. */
    private static final int NUMBERED_STEP_PAD = 2;
    /** Thin vertical bar on the chat clip's right edge (same offset as wheel). */
    private static final int SCROLLBAR_W = 5;
    private static final int USER_COLOR = 0xA0C8FF;
    private static final int AI_COLOR = 0xE0E0E0;
    private static final int SUGGEST_COLOR = 0xFFD080;
    /** Clickable quest title in chat history. */
    private static final int QUEST_LINK_COLOR = 0x6EC8FF;
    private final List<HoverHit> hoverHits = new ArrayList<>();
    private final List<QuestClickRect> questClickRects = new ArrayList<>();
    private EditBox input;
    private String draftInput = "";
    /** Strip/ask focus after last ask — ignores live JEI hover while screen open. */
    private ItemStack lastAskFocus = ItemStack.EMPTY;
    private double scrollOffset;
    private boolean stickToBottom = true;
    private boolean draggingScrollbar;
    private int scrollbarGrabY;
    private int lastMouseX;
    private int lastMouseY;
    private int panelLeft;
    private int panelWidth;
    private int sideLeft;
    private int sideWidth;
    private int chatTop;
    private int chatBottom;
    private int inputY;
    /** Sidebar hairline between ask shortcuts and jump/settings (visual only). */
    private int sideDividerY;
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
        PackKnowledge.ensureItemIndex();
        int btnH = 20;
        int btnGap = 4;
        int heldStrip = 18;
        this.sideWidth = 112;
        int gap = 10;
        int totalW = Math.min(580, this.width - 24);
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

        this.inputY = this.height - 46;
        this.chatTop = 34;
        this.chatBottom = this.inputY - heldStrip - 8;

        boolean busy = ChatSession.isBusy();

        this.input = new EditBox(this.font, this.panelLeft, this.inputY, this.panelWidth, 20,
                Component.translatable("packai.screen.hint"));
        this.input.setMaxLength(512);
        this.input.setHint(Component.translatable("packai.screen.hint"));
        this.input.setTooltip(Tooltip.create(Component.translatable("packai.screen.tooltip.input")));
        if (!this.draftInput.isEmpty()) {
            this.input.setValue(this.draftInput);
        }
        this.input.setEditable(!busy);
        this.addRenderableWidget(this.input);

        int sy = this.chatTop;
        int sw = this.sideWidth;

        Button sendBtn = Button.builder(Component.translatable("packai.screen.send"), b -> sendCurrent())
                .tooltip(Tooltip.create(Component.translatable("packai.screen.tooltip.send")))
                .bounds(this.sideLeft, sy, sw, btnH).build();
        sendBtn.active = !busy;
        this.addRenderableWidget(sendBtn);
        sy += btnH + btnGap;

        Button regenBtn = Button.builder(Component.translatable("packai.screen.regenerate"), b -> regenerate())
                .tooltip(Tooltip.create(Component.translatable("packai.screen.tooltip.regenerate")))
                .bounds(this.sideLeft, sy, sw, btnH).build();
        regenBtn.active = ChatSession.canRegenerate();
        this.addRenderableWidget(regenBtn);
        sy += btnH + btnGap;

        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.clear_chat"), b -> clearChat())
                .tooltip(Tooltip.create(Component.translatable("packai.screen.tooltip.clear_chat")))
                .bounds(this.sideLeft, sy, sw, btnH).build());
        sy += btnH + btnGap;

        int pickCount = ChatSession.pendingItemCount();
        Component pickLabel = pickCount > 0
                ? Component.translatable("packai.screen.pick_items_n", pickCount)
                : Component.translatable("packai.screen.pick_items");
        this.addRenderableWidget(Button.builder(pickLabel, b -> {
            rememberDraft();
            if (this.minecraft != null) {
                this.minecraft.setScreen(new InvPickScreen(this));
            }
        }).tooltip(Tooltip.create(Component.translatable("packai.screen.tooltip.pick_items")))
                .bounds(this.sideLeft, sy, sw, btnH).build());
        sy += btnH + btnGap;

        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.quest_next"), b ->
                        askTemplate("packai.ask.quest_next", null, null, false))
                .tooltip(Tooltip.create(Component.translatable("packai.screen.tooltip.quest_next")))
                .bounds(this.sideLeft, sy, sw, btnH).build());
        sy += btnH + btnGap;

        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.next_step_short"), b ->
                        askNextStep())
                .tooltip(Tooltip.create(Component.translatable("packai.screen.tooltip.next_step")))
                .bounds(this.sideLeft, sy, sw, btnH).build());
        sy += btnH + btnGap + 4;
        this.sideDividerY = sy;
        sy += 6;

        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.jump_latest"), b -> {
            this.stickToBottom = true;
            this.scrollOffset = maxScroll(chatLines());
        }).tooltip(Tooltip.create(Component.translatable("packai.screen.tooltip.jump_latest")))
                .bounds(this.sideLeft, sy, sw, btnH).build());
        sy += btnH + btnGap;

        this.addRenderableWidget(Button.builder(Component.translatable("packai.screen.settings"), b -> {
            rememberDraft();
            if (this.minecraft != null) {
                this.minecraft.setScreen(new PackAiSettingsScreen(this));
            }
        }).tooltip(Tooltip.create(Component.translatable("packai.screen.tooltip.settings")))
                .bounds(this.sideLeft, sy, sw, btnH).build());

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
        JeiTargetResolver.clearPin();
        this.lastAskFocus = ItemStack.EMPTY;
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
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
        // Rebuild from template in the *current* game language (lang change + regen).
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
            // Show first selected id on the user bubble when no JEI pin.
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
        // Keep sticky lastQuests across Asks — merge happens in setLastQuests on reply.
        ChatSession.setLastAsk(new ChatSession.LastAsk(
                question, selected, questOverride, templateKey, templateArg0, templateArg1));
        if (appendUser) {
            ChatSession.append(ChatMessage.user(question, itemLabel, itemId, held));
        }
        ChatSession.append(ChatMessage.assistant(
                Component.translatable("packai.status.waiting").getString()));
        this.stickToBottom = true;
        rememberDraft();
        this.rebuildWidgets();

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
        TokenUsage usage = result == null || result.tokenUsage() == null
                ? TokenUsage.NONE
                : result.tokenUsage();
        ChatSession.replaceLastAssistant(answer, items, cards, usage);
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

    /**
     * Stable strip/ask focus while assistant open: pin, pending/lastAskFocus (NBT), then draft id.
     * Never live JEI ingredient-list hover (that stuck「黑暗祭壇」over cursed_ingot ask).
     * Bare {@code mod:id} in draft must not wipe InvPick-pinned NBT for the same item;
     * stable wins only when there is no rich focus, or registry id differs (user retarget).
     * Sticky lastAskFocus is ignored when InvPick pending no longer contains it.
     */
    private ItemStack contextStack() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack pin = JeiTargetResolver.pinnedOrEmpty();
        if (pin != null && !pin.isEmpty()) {
            return pin;
        }
        ItemStack rich = pendingOrLastAskFocus();
        String draft = this.input == null ? this.draftInput : this.input.getValue();
        ItemStack stable = JeiTargetResolver.resolveStable(this.minecraft, draft == null ? "" : draft);
        if (!stable.isEmpty()) {
            if (rich.isEmpty()) {
                return stable;
            }
            String stableId = AskService.fromStack(stable).id();
            String richId = AskService.fromStack(rich).id();
            if (stableId != null && !stableId.isBlank()
                    && richId != null && stableId.equalsIgnoreCase(richId)) {
                return rich;
            }
            return stable;
        }
        return rich;
    }

    /** Pending InvPick sample (selectionKey-aware), else sticky lastAskFocus. */
    private ItemStack pendingOrLastAskFocus() {
        List<ItemRef> pending = ChatSession.pendingItems();
        if (!pending.isEmpty()) {
            if (!this.lastAskFocus.isEmpty()) {
                String want = AskService.selectionKey(AskService.fromStack(this.lastAskFocus));
                for (ItemRef ref : pending) {
                    if (ref.isPresent() && AskService.selectionKey(ref).equals(want)) {
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

    /** {@code name} or {@code a][b][c} so chat renders {@code [a][b][c]}. Cap = pending max. */
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
        ItemStack focus = contextStack();
        List<ItemRef> pending = ChatSession.pendingItems();
        String focusKey = AskService.selectionKey(AskService.fromStack(focus));
        boolean focusInPending = false;
        if (!focusKey.isEmpty()) {
            for (ItemRef ref : pending) {
                if (ref.isPresent() && focusKey.equals(AskService.selectionKey(ref))) {
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

    private record InlinePiece(String text, ItemStack item, boolean lineBreak, Runnable click) {
        static InlinePiece ofText(String t) {
            return new InlinePiece(t == null ? "" : t, ItemStack.EMPTY, false, null);
        }

        static InlinePiece ofLink(String t, Runnable open) {
            return new InlinePiece(t == null ? "" : t, ItemStack.EMPTY, false, open);
        }

        static InlinePiece ofItem(ItemStack stack) {
            return new InlinePiece("", stack == null ? ItemStack.EMPTY : stack, false, null);
        }

        static InlinePiece ofNewline() {
            return new InlinePiece("", ItemStack.EMPTY, true, null);
        }

        boolean isItem() {
            return item != null && !item.isEmpty();
        }

        boolean isLink() {
            return click != null && text != null && !text.isEmpty();
        }
    }

    private record ChatLine(
            FormattedCharSequence text,
            int color,
            ItemStack icon,
            List<ItemStack> iconRow,
            String subLabel,
            RecipeCard recipeCard,
            int extraPad,
            Runnable clickAction,
            List<InlinePiece> spans
    ) {
        ChatLine(FormattedCharSequence text, int color) {
            this(text, color, ItemStack.EMPTY, List.of(), "", null, 0, null, List.of());
        }

        ChatLine(FormattedCharSequence text, int color, ItemStack icon) {
            this(text, color, icon, List.of(), "", null, 0, null, List.of());
        }

        ChatLine(FormattedCharSequence text, int color, ItemStack icon, List<ItemStack> iconRow, String subLabel) {
            this(text, color, icon, iconRow, subLabel, null, 0, null, List.of());
        }

        static ChatLine recipe(RecipeCard card) {
            // Top pad = caption→card gap (catalysts live on caption row).
            return new ChatLine(
                    FormattedCharSequence.EMPTY, SUGGEST_COLOR, ItemStack.EMPTY, List.of(), "", card,
                    CAPTION_TO_CARD_GAP, null, List.of());
        }

        static ChatLine questLink(FormattedCharSequence text, Runnable open) {
            return new ChatLine(text, QUEST_LINK_COLOR, ItemStack.EMPTY, List.of(), "", null, 0, open, List.of());
        }

        static ChatLine rich(List<InlinePiece> spans, int color, int pad) {
            return new ChatLine(
                    FormattedCharSequence.EMPTY, color, ItemStack.EMPTY, List.of(), "", null, pad, null,
                    spans == null ? List.of() : List.copyOf(spans));
        }

        boolean hasSpans() {
            return spans != null && !spans.isEmpty();
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
                    Component.translatable("packai.screen.chat_empty").getVisualOrderText(), GuiShell.MUTED));
            return lines;
        }
        for (int i = 0; i < msgs.size(); i++) {
            ChatMessage msg = msgs.get(i);
            String label = msg.isUser()
                    ? Component.translatable("packai.screen.chat_you").getString()
                    : Component.translatable("packai.screen.chat_ai").getString();
            int color = msg.isUser() ? USER_COLOR : AI_COLOR;
            String body = Plainify.forMinecraftUi(msg.text());
            if (msg.isUser() && msg.hasHeldItem()) {
                // Order: You:/你: then icon then [label] body (not left ICON_COL before prefix).
                String tag = "[" + Plainify.stripMcFormat(msg.heldItemLabel()) + "] ";
                ItemStack icon = msg.iconOrEmpty();
                if (icon.isEmpty()) {
                    icon = ItemResolver.stackFromId(msg.heldItemId());
                }
                List<InlinePiece> atoms = new ArrayList<>();
                atoms.add(InlinePiece.ofText(label));
                if (!icon.isEmpty()) {
                    atoms.add(InlinePiece.ofItem(icon));
                }
                appendTextAtoms(atoms, tag + body);
                wrapInlineAtoms(lines, atoms, color);
            } else if (msg.isUser()) {
                String block = label + body;
                for (FormattedCharSequence part : this.font.split(Component.literal(block), this.panelWidth)) {
                    lines.add(new ChatLine(part, color));
                }
            } else {
                appendAssistantBody(lines, label, body, color, msg.recipeCards());
            }
            if (!msg.isUser()
                    && PackAiConfig.showTokenUsage()
                    && msg.hasTokenUsage()) {
                TokenUsage u = msg.tokenUsage();
                Component usageLine = Component.translatable(
                        "packai.screen.token_usage", u.formatIn(), u.formatOut());
                for (FormattedCharSequence part : this.font.split(usageLine, this.panelWidth)) {
                    lines.add(new ChatLine(part, 0x888888));
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
            lines.add(new ChatLine(FormattedCharSequence.EMPTY, color));
        }
        return lines;
    }

    /**
     * Interleave answer text with JEI recipe cards at {@code {{RECIPE}}} markers
     * (or after the first paragraph when the model omitted markers).
     */
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
            List<InlinePiece> atoms = new ArrayList<>();
            appendTextAtoms(atoms, label == null ? "" : label);
            appendTextAtoms(atoms, body == null ? "" : body);
            linkQuestTitlesInAtoms(atoms, cards);
            wrapInlineAtoms(lines, atoms, color);
            return;
        }
        boolean firstText = true;
        List<RecipeEmbed.Part> pending = new ArrayList<>();
        for (RecipeEmbed.Part part : parts) {
            if (part.isCard()) {
                flushInlineParts(lines, pending, color, firstText ? label : null, cards);
                if (!pending.isEmpty()) {
                    firstText = false;
                    pending.clear();
                } else if (firstText) {
                    // Label alone when reply starts with a card.
                    for (FormattedCharSequence p : this.font.split(Component.literal(label), this.panelWidth)) {
                        lines.add(new ChatLine(p, color));
                    }
                    firstText = false;
                }
                int idx = part.cardIndex();
                if (idx >= 0 && idx < cards.size()) {
                    RecipeCard card = cards.get(idx);
                    // Skip demoted scroll material strips if any linger on old sessions.
                    if (card != null && !card.isEmpty() && !card.isScrollMaterialStrip()) {
                        // Spacing: CAPTION_TO_CARD_GAP via ChatLine.recipe; modest blank after.
                        ensureChatBlankLine(lines, color);
                        appendRecipeCardCaption(lines, card);
                        lines.add(ChatLine.recipe(card));
                        ensureChatBlankLine(lines, color);
                    }
                }
                continue;
            }
            pending.add(part);
        }
        flushInlineParts(lines, pending, color, firstText ? label : null, cards);
    }

    /** Flush TEXT/ITEM parts as baseline-inline glyphs (not left ICON_COL column). */
    private void flushInlineParts(
            List<ChatLine> lines,
            List<RecipeEmbed.Part> parts,
            int color,
            String labelPrefix,
            List<RecipeCard> recipeCards
    ) {
        if (parts == null || parts.isEmpty()) {
            return;
        }
        List<InlinePiece> atoms = new ArrayList<>();
        boolean labeled = false;
        for (RecipeEmbed.Part p : parts) {
            if (p.isItem()) {
                ItemStack stack = ItemResolver.stackFromId(p.text());
                if (stack.isEmpty()) {
                    String id = ItemResolver.bareRegistryId(p.text());
                    String t = (!labeled && labelPrefix != null) ? labelPrefix + id : id;
                    labeled = true;
                    appendTextAtoms(atoms, t);
                    continue;
                }
                if (!labeled && labelPrefix != null) {
                    appendTextAtoms(atoms, labelPrefix);
                    labeled = true;
                }
                ItemStack copy = stack.copy();
                copy.setCount(Math.max(1, p.itemCount()));
                atoms.add(InlinePiece.ofItem(copy));
                continue;
            }
            String chunk = p.text() == null ? "" : p.text();
            if (chunk.isEmpty()) {
                continue;
            }
            if (!labeled && labelPrefix != null) {
                chunk = labelPrefix + chunk;
                labeled = true;
            }
            appendTextAtoms(atoms, chunk);
        }
        linkQuestTitlesInAtoms(atoms, recipeCards);
        wrapInlineAtoms(lines, atoms, color);
    }

    /**
     * Turn quest titles already present in AI text into blue clickable spans (same place as mention).
     * Skip titles already shown as a recipe-card category (prefer the card; open via caption).
     */
    private void linkQuestTitlesInAtoms(List<InlinePiece> atoms, List<RecipeCard> cards) {
        List<QuestGuide.Hit> quests = ChatSession.lastQuests();
        if (atoms == null || atoms.isEmpty() || quests == null || quests.isEmpty()) {
            return;
        }
        Set<String> covered = QuestGuide.questTitlesCoveredByCards(quests, cards);
        List<InlinePiece> out = new ArrayList<>(atoms.size());
        for (InlinePiece atom : atoms) {
            if (atom.isItem() || atom.lineBreak() || atom.isLink()) {
                out.add(atom);
                continue;
            }
            out.addAll(splitTextWithQuestLinks(atom.text(), quests, covered));
        }
        atoms.clear();
        atoms.addAll(out);
    }

    /** Split plain text on longest earliest lastQuests title matches. */
    private static List<InlinePiece> splitTextWithQuestLinks(
            String text, List<QuestGuide.Hit> quests, Set<String> skipTitles
    ) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<InlinePiece> out = new ArrayList<>();
        String rest = text;
        while (!rest.isEmpty()) {
            int bestAt = -1;
            int bestLen = 0;
            QuestGuide.Hit bestHit = null;
            for (QuestGuide.Hit hit : quests) {
                if (hit == null) {
                    continue;
                }
                String title = QuestGuide.displayTitle(hit);
                if (title == null || title.length() < 2) {
                    continue;
                }
                if (skipTitles != null) {
                    boolean skip = false;
                    for (String s : skipTitles) {
                        if (QuestGuide.sameQuestTitle(s, title)) {
                            skip = true;
                            break;
                        }
                    }
                    if (skip) {
                        continue;
                    }
                }
                int at = rest.indexOf(title);
                if (at < 0) {
                    continue;
                }
                // Prefer earlier mention; at same index prefer longer title.
                if (bestAt < 0 || at < bestAt || (at == bestAt && title.length() > bestLen)) {
                    bestAt = at;
                    bestLen = title.length();
                    bestHit = hit;
                }
            }
            if (bestHit == null || bestAt < 0) {
                out.add(InlinePiece.ofText(rest));
                break;
            }
            if (bestAt > 0) {
                out.add(InlinePiece.ofText(rest.substring(0, bestAt)));
            }
            String title = rest.substring(bestAt, bestAt + bestLen);
            QuestGuide.Hit openHit = bestHit;
            out.add(InlinePiece.ofLink(title, () -> QuestBookOpener.open(openHit)));
            rest = rest.substring(bestAt + bestLen);
        }
        return out;
    }

    private static void appendTextAtoms(List<InlinePiece> atoms, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String[] rawLines = text.split("\n", -1);
        for (int i = 0; i < rawLines.length; i++) {
            if (i > 0) {
                atoms.add(InlinePiece.ofNewline());
            }
            if (!rawLines[i].isEmpty()) {
                atoms.add(InlinePiece.ofText(rawLines[i]));
            }
        }
    }

    private void wrapInlineAtoms(List<ChatLine> lines, List<InlinePiece> atoms, int color) {
        if (atoms == null || atoms.isEmpty()) {
            return;
        }
        List<InlinePiece> row = new ArrayList<>();
        int used = 0;
        StringBuilder rowTextProbe = new StringBuilder();
        int pendingBreaks = 0;
        for (InlinePiece atom : atoms) {
            if (atom.lineBreak()) {
                flushInlineRow(lines, row, color, rowTextProbe.toString());
                row.clear();
                used = 0;
                rowTextProbe.setLength(0);
                pendingBreaks++;
                continue;
            }
            flushPendingParagraphBreaks(lines, color, pendingBreaks);
            pendingBreaks = 0;
            if (atom.isItem() || atom.isLink()) {
                int w = atom.isItem()
                        ? ICON_SIZE + 2
                        : this.font.width(atom.text());
                if (used + w > this.panelWidth && !row.isEmpty()) {
                    flushInlineRow(lines, row, color, rowTextProbe.toString());
                    row.clear();
                    used = 0;
                    rowTextProbe.setLength(0);
                }
                row.add(atom);
                used += w;
                if (atom.isLink()) {
                    rowTextProbe.append(atom.text());
                }
                continue;
            }
            String s = atom.text();
            int i = 0;
            while (i < s.length()) {
                int fit = 0;
                while (i + fit < s.length()) {
                    int tw = this.font.width(s.substring(i, i + fit + 1));
                    if (used + tw > this.panelWidth && (used > 0 || fit > 0)) {
                        break;
                    }
                    fit++;
                }
                if (fit == 0 && used > 0) {
                    flushInlineRow(lines, row, color, rowTextProbe.toString());
                    row.clear();
                    used = 0;
                    rowTextProbe.setLength(0);
                    continue;
                }
                if (fit == 0) {
                    fit = 1;
                }
                String piece = s.substring(i, i + fit);
                row.add(InlinePiece.ofText(piece));
                rowTextProbe.append(piece);
                used += this.font.width(piece);
                i += fit;
                if (i < s.length()) {
                    flushInlineRow(lines, row, color, rowTextProbe.toString());
                    row.clear();
                    used = 0;
                    rowTextProbe.setLength(0);
                }
            }
        }
        flushInlineRow(lines, row, color, rowTextProbe.toString());
        flushPendingParagraphBreaks(lines, color, pendingBreaks);
    }

    /**
     * Soft {@code \\n} = next content row (no empty ChatLine). Paragraph {@code \\n\\n+} = one blank.
     * Avoids double-blank air between numbered steps while keeping 【來源】 readable.
     */
    static int blankRowsForNewlines(int consecutiveNewlines) {
        return consecutiveNewlines >= 2 ? 1 : 0;
    }

    private static void flushPendingParagraphBreaks(List<ChatLine> lines, int color, int pendingBreaks) {
        int blanks = blankRowsForNewlines(pendingBreaks);
        for (int i = 0; i < blanks; i++) {
            ensureChatBlankLine(lines, color);
        }
    }

    private void flushInlineRow(List<ChatLine> lines, List<InlinePiece> row, int color, String probe) {
        if (row == null || row.isEmpty()) {
            return;
        }
        int lineColor = isSectionHeader(probe) ? SUGGEST_COLOR : color;
        int pad = looksLikeNumberedStep(probe) ? NUMBERED_STEP_PAD : (isSectionHeader(probe) ? 2 : 0);
        // Rewrite leading ## in first text span for display.
        List<InlinePiece> display = new ArrayList<>(row.size());
        boolean fixedHeader = false;
        for (InlinePiece p : row) {
            if (!fixedHeader && !p.isItem() && !p.lineBreak() && isSectionHeader(p.text())) {
                display.add(InlinePiece.ofText(displaySectionHeader(p.text())));
                fixedHeader = true;
            } else {
                display.add(p);
            }
        }
        lines.add(ChatLine.rich(display, lineColor, pad));
    }

    /** Wrap text; numbered steps (1. / 2.) get slight extra line pad; section headers stand out. */
    private void appendWrappedText(List<ChatLine> lines, String block, int color, ItemStack icon) {
        if (block == null || block.isEmpty()) {
            return;
        }
        String[] rawLines = block.split("\n", -1);
        boolean iconUsed = false;
        int pendingEmpty = 0;
        for (String rawLine : rawLines) {
            if (rawLine.isEmpty()) {
                pendingEmpty++;
                continue;
            }
            for (int b = 0; b < blankRowsForNewlines(pendingEmpty); b++) {
                ensureChatBlankLine(lines, color);
            }
            pendingEmpty = 0;
            String display = displaySectionHeader(rawLine);
            int lineColor = isSectionHeader(rawLine) ? SUGGEST_COLOR : color;
            int pad = looksLikeNumberedStep(rawLine) ? NUMBERED_STEP_PAD : (isSectionHeader(rawLine) ? 2 : 0);
            ItemStack lineIcon = (!iconUsed && icon != null && !icon.isEmpty()) ? icon : ItemStack.EMPTY;
            int wrap = Math.max(40, this.panelWidth - (lineIcon.isEmpty() ? 0 : ICON_COL));
            List<FormattedCharSequence> fps = this.font.split(Component.literal(display), wrap);
            boolean first = true;
            for (FormattedCharSequence fp : fps) {
                ItemStack ic = first ? lineIcon : ItemStack.EMPTY;
                lines.add(new ChatLine(fp, lineColor, ic, List.of(), "", null, pad, null, List.of()));
                first = false;
            }
            if (!lineIcon.isEmpty()) {
                iconUsed = true;
            }
        }
        for (int b = 0; b < blankRowsForNewlines(pendingEmpty); b++) {
            ensureChatBlankLine(lines, color);
        }
    }

    /** True for {@code ## How to get}, {@code 【機器】}, {@code [Machine]}, or lines starting with those. */
    private static boolean isSectionHeader(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        String t = s.trim();
        if (t.startsWith("## ")) {
            return true;
        }
        // Exact short headers, or 【來源】… / [Sources]… footers (pad so not crushed into prior line).
        if (t.matches("【[^】]{1,12}】") || t.matches("\\[[A-Za-z][^\\]]{0,14}\\]")) {
            return true;
        }
        return t.matches("【[^】]{1,12}】.*") || t.matches("\\[[A-Za-z][^\\]]{0,14}\\].*");
    }

    /** Strip leading {@code ## } so hashes don't show when chat has no Markdown renderer. */
    private static String displaySectionHeader(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.startsWith("## ")) {
            return t.substring(3).trim();
        }
        return s;
    }

    /** Insert blank chat line unless the last line is already blank. */
    private static void ensureChatBlankLine(List<ChatLine> lines, int color) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        ChatLine prev = lines.get(lines.size() - 1);
        boolean blank = prev.recipeCard() == null
                && prev.text() == FormattedCharSequence.EMPTY
                && (prev.spans() == null || prev.spans().isEmpty())
                && prev.clickAction() == null
                && prev.icon().isEmpty();
        if (!blank) {
            lines.add(new ChatLine(FormattedCharSequence.EMPTY, color));
        }
    }

    /**
     * Lead-in before a JEI recipe card when the answer has no prose before this card
     * (offline / marker-less fallback): category title + optional first catalyst icon.
     * When AI text already precedes the card, {@link #appendAssistantBody} skips this —
     * except quest cards ({@link RecipeCard#hasQuestOpen()} / Hit match), which stay clickable.
     */
    private void appendRecipeCardCaption(List<ChatLine> lines, RecipeCard card) {
        if (lines == null || card == null) {
            return;
        }
        String cat = Plainify.stripMcFormat(card.categoryTitle());
        if (cat == null || cat.isBlank()) {
            cat = "?";
        }
        ItemStack catIcon = firstCaptionCatalyst(card);
        Runnable open = questOpenAction(card, cat);
        if (open != null && !card.isScrollMaterialStrip()) {
            String key = card.captionLangKey();
            if (key == null) {
                key = "packai.screen.recipe";
            }
            String full = Component.translatable(key, cat).getString();
            List<InlinePiece> atoms = new ArrayList<>();
            if (!catIcon.isEmpty()) {
                atoms.add(InlinePiece.ofItem(catIcon));
            }
            int at = full.indexOf(cat);
            if (at >= 0) {
                if (at > 0) {
                    atoms.add(InlinePiece.ofText(full.substring(0, at)));
                }
                atoms.add(InlinePiece.ofLink(cat, open));
                if (at + cat.length() < full.length()) {
                    atoms.add(InlinePiece.ofText(full.substring(at + cat.length())));
                }
            } else {
                atoms.add(InlinePiece.ofLink(full, open));
            }
            wrapInlineAtoms(lines, atoms, SUGGEST_COLOR);
            return;
        }
        String capKey = card.captionLangKey();
        Component title = capKey == null
                ? Component.literal(cat)
                : Component.translatable(capKey, cat);
        int wrap = Math.max(40, this.panelWidth - (catIcon.isEmpty() ? 0 : ICON_COL));
        boolean iconUsed = false;
        for (FormattedCharSequence p : this.font.split(title, wrap)) {
            if (!iconUsed && !catIcon.isEmpty()) {
                lines.add(new ChatLine(p, SUGGEST_COLOR, catIcon));
                iconUsed = true;
            } else {
                lines.add(new ChatLine(p, SUGGEST_COLOR));
            }
        }
    }

    /**
     * Prefer sticky {@link ChatSession#lastQuests()} Hit; else card {@link RecipeCard#questOpenId()}.
     */
    private static Runnable questOpenAction(RecipeCard card, String categoryTitle) {
        if (card == null) {
            return null;
        }
        QuestGuide.Hit hit = QuestGuide.hitMatchingCardTitle(categoryTitle, ChatSession.lastQuests());
        if (hit == null && card.hasQuestOpen()) {
            hit = QuestGuide.hitMatchingCardTitle(card.categoryTitle(), ChatSession.lastQuests());
        }
        if (hit != null) {
            QuestGuide.Hit openHit = hit;
            return () -> QuestBookOpener.open(openHit);
        }
        if (card.hasQuestOpen()) {
            String id = card.questOpenId();
            return () -> QuestBookOpener.open("ftbquests", id);
        }
        return null;
    }

    /** First non-empty catalyst for caption row (Quests book, cooking pot, …). */
    private static ItemStack firstCaptionCatalyst(RecipeCard card) {
        if (card == null || card.layout() == RecipeCard.Layout.CRAFTING_3X3
                || card.catalysts() == null || card.catalysts().isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (ItemStack st : card.catalysts()) {
            if (st != null && !st.isEmpty()) {
                return st;
            }
        }
        return ItemStack.EMPTY;
    }

    /** True for lines like {@code 1. craft} / {@code 2) use}. */
    private static boolean looksLikeNumberedStep(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        int digits = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
            digits++;
        }
        if (digits == 0 || i >= s.length()) {
            return false;
        }
        char c = s.charAt(i);
        if (c != '.' && c != ')') {
            return false;
        }
        i++;
        return i < s.length() && Character.isWhitespace(s.charAt(i));
    }

    private int lineStride() {
        return this.font.lineHeight + 2;
    }

    private int strideOf(ChatLine line) {
        if (line.recipeCard() != null) {
            return recipeCardHeight(line.recipeCard()) + line.extraPad();
        }
        if (line.hasSpans()) {
            boolean anyItem = false;
            for (InlinePiece p : line.spans()) {
                if (p.isItem()) {
                    anyItem = true;
                    break;
                }
            }
            return Math.max(lineStride(), anyItem ? ICON_SIZE + 4 : lineStride()) + line.extraPad();
        }
        if (!line.icon().isEmpty() || !line.iconRow().isEmpty()) {
            return Math.max(lineStride(), ICON_SIZE + 4) + line.extraPad();
        }
        return lineStride() + line.extraPad();
    }

    private int recipeCardHeight(RecipeCard card) {
        // Category + catalysts live on the caption ChatLine above; card body is JEI only.
        // JEI layout drawable owns item slots for any Layout — height from drawable + soft/fluid footer.
        int notesH = reqNotesHeight(card);
        if (jeiDrawableFitsPanel(card)) {
            int body = shapedBoundsHeight(card);
            int tip = shapedNeedsPreviewTip(card) ? this.font.lineHeight + 2 : 0;
            int fluidFooter = card.hasPlacedFluids() ? 0 : card.fluidInputs().size() + card.fluidOutputs().size();
            int extras = fluidFooter + card.otherInputs().size() + card.otherOutputs().size();
            int extraRows = extras <= 0 ? 0 : 1 + (extras - 1) / Math.max(1, this.panelWidth / ICON_COL);
            return body + tip + extraRows * (ICON_SIZE + 4) + notesH + CARD_BODY_TAIL;
        }
        if (card.layout() == RecipeCard.Layout.CRAFTING_3X3) {
            return 3 * CRAFTING_SLOT_STRIDE + 6 + notesH + CARD_BODY_TAIL;
        }
        if (card.layout() == RecipeCard.Layout.SHAPED) {
            int shapedH = shapedBoundsHeight(card);
            boolean outsInPanel = shapedHasKind(card, RecipeCard.SlotKind.OUTPUT);
            // Harvest SHAPED (no JEI drawable) still footers fluids even if placedFluids set.
            int extras = card.fluidInputs().size() + card.fluidOutputs().size()
                    + card.otherInputs().size() + card.otherOutputs().size()
                    + (outsInPanel ? 0 : card.outputs().size());
            int extraRows = extras <= 0 ? 0 : 1 + (extras - 1) / Math.max(1, this.panelWidth / ICON_COL);
            int tip = shapedNeedsPreviewTip(card) ? this.font.lineHeight + 2 : 0;
            return shapedH + tip + extraRows * (ICON_SIZE + 4) + notesH + CARD_BODY_TAIL;
        }
        int slots = card.inputs().size()
                + card.outputs().size()
                + card.fluidInputs().size()
                + card.fluidOutputs().size()
                + card.otherInputs().size()
                + card.otherOutputs().size();
        if (slots == 0 && card.isScrollMaterialStrip()) {
            return this.font.lineHeight + 4 + notesH;
        }
        int rowBudget = Math.max(ICON_COL, this.panelWidth - 24);
        int rows = Math.max(1, (slots * ICON_COL + 24 + rowBudget - 1) / rowBudget);
        return rows * (ICON_SIZE + 4) + notesH + CARD_BODY_TAIL;
    }

    private int reqNotesHeight(RecipeCard card) {
        List<String> lines = FormatRequirements.footnoteLines(
                card == null ? List.of() : card.reqNotes(),
                card == null ? List.of() : card.unlockGates());
        return lines.isEmpty() ? 0 : lines.size() * this.font.lineHeight + 2;
    }

    private static final int MAX_SHAPED_CARD_H = 168; // Create 9×9 JEI ≈160px; chat can scroll

    /** JEI 1:1 drawable only when it fits; oversized ritual uses scaled harvest (no pose.scale). */
    private boolean jeiDrawableFitsPanel(RecipeCard card) {
        if (!JeiLayoutDraw.hasLayout(card)) {
            return false;
        }
        int maxW = Math.max(48, this.panelWidth - 28);
        return JeiLayoutDraw.width(card) <= maxW && JeiLayoutDraw.height(card) <= MAX_SHAPED_CARD_H;
    }

    private float shapedScale(RecipeCard card) {
        int maxW = Math.max(48, this.panelWidth - 28);
        if (jeiDrawableFitsPanel(card)) {
            // Always 1:1 for JEI drawable — pose.scale desyncs items vs slots (Create Sawing OUTPUT).
            // Chat scissor clips overflow; stride uses full getRect height.
            return 1.0f;
        }
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
        return Math.min(1.0f, Math.min(maxW / (float) bw, MAX_SHAPED_CARD_H / (float) bh));
    }

    private boolean shapedNeedsPreviewTip(RecipeCard card) {
        return shapedScale(card) < 0.999f
                || (card.categoryTitle() != null && card.categoryTitle().contains("grid truncated"));
    }

    private int shapedBoundsHeight(RecipeCard card) {
        float scale = shapedScale(card);
        if (jeiDrawableFitsPanel(card)) {
            // Chat stride uses JEI getRect (+ small overflow). Full OUTSIDE_DRAW_PAD in layoutFit*
            // is for scale/FBO fit — reserving all 14px here left Quests-sized dead air under cards.
            int bh = JeiLayoutDraw.height(card);
            return Math.max(ICON_SIZE + 4, Math.round(bh * scale) + CARD_OVERFLOW_PAD);
        }
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

    private int chatViewH() {
        return Math.max(1, this.chatBottom - this.chatTop);
    }

    private int chatScrollbarLeft() {
        return this.panelLeft + this.panelWidth + 2 - SCROLLBAR_W;
    }

    private int chatScrollbarRight() {
        return this.panelLeft + this.panelWidth + 2;
    }

    private boolean hitChatScrollbar(double mouseX, double mouseY) {
        return mouseX >= chatScrollbarLeft() && mouseX < chatScrollbarRight()
                && mouseY >= this.chatTop && mouseY <= this.chatBottom;
    }

    private int scrollbarThumbH(int max) {
        int view = chatViewH();
        int content = view + max;
        return Mth.clamp((int) ((long) view * view / content), 12, view);
    }

    private int scrollbarThumbTop(int max, int thumbH) {
        int travel = chatViewH() - thumbH;
        if (max <= 0 || travel <= 0) {
            return this.chatTop;
        }
        return this.chatTop + (int) Math.round(this.scrollOffset * travel / max);
    }

    private void jumpScrollToThumbCenter(double mouseY, int max) {
        int thumbH = scrollbarThumbH(max);
        int travel = Math.max(1, chatViewH() - thumbH);
        double rel = (mouseY - this.chatTop - thumbH / 2.0) / travel;
        this.scrollOffset = Mth.clamp(rel * max, 0, max);
        this.stickToBottom = this.scrollOffset >= max - 1;
    }

    private void applyScrollbarDrag(double mouseY, int max) {
        int thumbH = scrollbarThumbH(max);
        int travel = Math.max(1, chatViewH() - thumbH);
        int thumbTop = Mth.clamp((int) mouseY - this.scrollbarGrabY, this.chatTop, this.chatTop + travel);
        this.scrollOffset = (double) (thumbTop - this.chatTop) * max / travel;
        this.stickToBottom = this.scrollOffset >= max - 1;
    }

    private boolean beginScrollbarDrag(double mouseX, double mouseY) {
        int max = maxScroll(chatLines());
        if (max <= 0 || !hitChatScrollbar(mouseX, mouseY)) {
            return false;
        }
        int thumbH = scrollbarThumbH(max);
        int thumbTop = scrollbarThumbTop(max, thumbH);
        if (mouseY < thumbTop || mouseY >= thumbTop + thumbH) {
            jumpScrollToThumbCenter(mouseY, max);
            thumbTop = scrollbarThumbTop(max, thumbH);
        }
        this.draggingScrollbar = true;
        this.scrollbarGrabY = (int) mouseY - thumbTop;
        this.stickToBottom = this.scrollOffset >= max - 1;
        return true;
    }

    private void drawChatScrollbar(GuiGraphics graphics, int max) {
        if (max <= 0) {
            return;
        }
        int left = chatScrollbarLeft();
        int right = chatScrollbarRight();
        graphics.fill(left, this.chatTop, right, this.chatBottom, GuiShell.HAIRLINE);
        int thumbH = scrollbarThumbH(max);
        int thumbTop = scrollbarThumbTop(max, thumbH);
        int color = this.draggingScrollbar ? GuiShell.ACCENT : GuiShell.ACCENT_DIM;
        graphics.fill(left, thumbTop, right, thumbTop + thumbH, color);
    }

    private void renderRecipeCard(GuiGraphics graphics, RecipeCard card, int left, int top) {
        // Caption ChatLine owns category text + catalyst icons; JEI starts at top.
        int y = top;
        // Prefer JEI category drawable for every layout; harvest paint is fallback.
        if (tryRenderJeiRecipeLayout(graphics, card, left, y)) {
            return;
        }
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
            // Footer: fluids / soft / outputs not already in the JEI-positioned panel.
            // Machines stay on title row (Cooking Pot) — not re-drawn here.
            int x = left;
            int rowStart = left;
            int maxX = left + this.panelWidth - 4;
            int[] yy = {y};
            boolean outsInPanel = shapedHasKind(card, RecipeCard.SlotKind.OUTPUT);
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
            boolean needArrow = !outsInPanel
                    || !card.fluidOutputs().isEmpty()
                    || !card.otherOutputs().isEmpty();
            if (needArrow) {
                x = wrapFlowX(x, rowStart, maxX, yy);
                graphics.drawString(this.font, "->", x, yy[0] + 4, 0xA0A0A0, false);
                x += 14;
            }
            for (FluidStack fluid : card.fluidOutputs()) {
                x = wrapFlowX(x, rowStart, maxX, yy);
                drawFluidSlot(graphics, fluid, x, yy[0]);
                x += ICON_COL;
            }
            if (!outsInPanel) {
                for (ItemStack st : card.outputs()) {
                    x = wrapFlowX(x, rowStart, maxX, yy);
                    drawItemSlot(graphics, st, x, yy[0], 0x66000000);
                    x += ICON_COL;
                }
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
        if (card.isScrollMaterialStrip()
                && card.inputs().isEmpty()
                && card.otherInputs() != null
                && !card.otherInputs().isEmpty()) {
            RecipeExtra none = card.otherInputs().get(0);
            String label = none == null ? "" : Plainify.stripMcFormat(none.label());
            if (!label.isBlank()) {
                graphics.drawString(this.font, label, left, yy[0] + 4, 0xA0A0A0, false);
            }
            return;
        }
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
        boolean hasOutputs = !card.fluidOutputs().isEmpty()
                || !card.outputs().isEmpty()
                || !card.otherOutputs().isEmpty();
        if (hasOutputs) {
            x = wrapFlowX(x, rowStart, maxX, yy);
            graphics.drawString(this.font, "->", x, yy[0] + 4, 0xA0A0A0, false);
            x += 14;
        }
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

    /**
     * Category text + catalysts are drawn on the caption ChatLine above the card.
     * @return {@code top} unchanged (no on-card header row)
     */
    private int renderRecipeCardTitle(GuiGraphics graphics, RecipeCard card, int left, int top) {
        return top;
    }

    /**
     * Draw official JEI layout for any card layout. Soft/fluid footer only — items stay in drawable.
     * @return true if JEI painted (caller skips harvest UI)
     */
    private boolean tryRenderJeiRecipeLayout(GuiGraphics graphics, RecipeCard card, int left, int top) {
        if (!jeiDrawableFitsPanel(card)) {
            return false;
        }
        float scale = shapedScale(card);
        if (!JeiLayoutDraw.draw(graphics, card, left, top, scale, this.lastMouseX, this.lastMouseY)) {
            return false;
        }
        // Harvest path skipped — JEI drawRecipe owns items+fluids; Pack AI hover via slot rects.
        registerJeiLayoutItemHovers(card, left, top, scale);
        // Match chat stride body height (getRect + CARD_OVERFLOW_PAD), not full layoutFit pad.
        int y = top + shapedBoundsHeight(card);
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
        renderJeiSoftFluidFooter(graphics, card, left, y);
        int fluidFooter = card.hasPlacedFluids() ? 0 : card.fluidInputs().size() + card.fluidOutputs().size();
        int extras = fluidFooter + card.otherInputs().size() + card.otherOutputs().size();
        int extraRows = extras <= 0 ? 0 : 1 + (extras - 1) / Math.max(1, this.panelWidth / ICON_COL);
        renderReqNotesFootnote(graphics, card, left, y + extraRows * (ICON_SIZE + 4));
        return true;
    }

    private void renderReqNotesFootnote(GuiGraphics graphics, RecipeCard card, int left, int top) {
        List<String> lines = FormatRequirements.footnoteLines(
                card == null ? List.of() : card.reqNotes(),
                card == null ? List.of() : card.unlockGates());
        if (lines.isEmpty()) {
            return;
        }
        int y = top + 2;
        for (String line : lines) {
            graphics.drawString(this.font, line, left, y, 0xA0A0A0, false);
            y += this.font.lineHeight;
        }
    }

    /**
     * Tooltips for JEI-drawable cards: prefer JEI {@code getSlotUnderMouse} + slot getRect
     * (same coordinate space as JEI recipe screen). Fallback to placed/grid when API misses.
     */
    private void registerJeiLayoutItemHovers(RecipeCard card, int left, int top, float scale) {
        Optional<JeiLayoutDraw.LayoutHover> jeiHover = JeiLayoutDraw.layoutHoverUnderMouse(
                card, left, top, scale, this.lastMouseX, this.lastMouseY);
        if (jeiHover.isPresent()) {
            JeiLayoutDraw.LayoutHover h = jeiHover.get();
            if (!h.item().isEmpty()) {
                addItemHoverBounds(h.x0(), h.y0(), h.x1(), h.y1(), h.item());
            } else if (h.fluid() != null && !h.fluid().isEmpty()) {
                addFluidHover(h.x0(), h.y0(), h.x1() - h.x0(), h.y1() - h.y0(), h.fluid());
            }
            return;
        }
        // Fallback when JEI under-mouse empty / API miss — static placed / 3x3 grid.
        List<RecipeCard.PlacedItem> placed = card.placedInputs();
        if (placed != null) {
            for (RecipeCard.PlacedItem p : placed) {
                if (p == null || p.stack().isEmpty()) {
                    continue;
                }
                int sx = left + Math.round(p.x() * scale);
                int sy = top + Math.round(p.y() * scale);
                if (sx + ICON_SIZE > left + this.panelWidth) {
                    continue;
                }
                addItemHover(sx, sy, p.stack());
            }
        }
        List<RecipeCard.PlacedFluid> fluids = card.placedFluids();
        if (fluids != null) {
            float s = Math.max(0.001f, scale);
            for (RecipeCard.PlacedFluid pf : fluids) {
                if (pf == null || pf.fluid() == null || pf.fluid().isEmpty()) {
                    continue;
                }
                int sx = left + Math.round(pf.x() * s);
                int sy = top + Math.round(pf.y() * s);
                int sw = Math.max(4, Math.round(pf.width() * s));
                int sh = Math.max(4, Math.round(pf.height() * s));
                if (sx + sw > left + this.panelWidth) {
                    continue;
                }
                addFluidHover(sx, sy, sw, sh, pf.fluid());
            }
        }
        if (card.layout() == RecipeCard.Layout.CRAFTING_3X3) {
            int stride = Math.max(8, Math.round(CRAFTING_SLOT_STRIDE * scale));
            int icon = Math.max(8, Math.round(ICON_SIZE * scale));
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int idx = row * 3 + col;
                    ItemStack slot = card.grid().size() > idx ? card.grid().get(idx) : ItemStack.EMPTY;
                    if (slot.isEmpty()) {
                        continue;
                    }
                    int sx = left + col * stride;
                    int sy = top + row * stride;
                    addItemHoverBounds(sx, sy, sx + icon, sy + icon, slot);
                }
            }
            if (!card.outputs().isEmpty()) {
                int ox = left + 3 * stride + Math.max(6, Math.round(10 * scale));
                int oy = top + stride;
                addItemHoverBounds(ox, oy, ox + icon, oy + icon, card.outputs().get(0));
            }
        }
    }

    /** Soft / fluid rows under a JEI drawable (item+fluid slots already painted by JEI). */
    private void renderJeiSoftFluidFooter(GuiGraphics graphics, RecipeCard card, int left, int top) {
        boolean fluidsInLayout = card.hasPlacedFluids();
        if (card.otherInputs().isEmpty() && card.otherOutputs().isEmpty()
                && (fluidsInLayout || (card.fluidInputs().isEmpty() && card.fluidOutputs().isEmpty()))) {
            return;
        }
        int x = left;
        int rowStart = left;
        int maxX = left + this.panelWidth - 4;
        int[] yy = {top};
        for (RecipeExtra extra : card.otherInputs()) {
            x = wrapFlowX(x, rowStart, maxX, yy);
            drawExtraSlot(graphics, extra, x, yy[0]);
            x += ICON_COL;
        }
        if (!fluidsInLayout) {
            for (FluidStack fluid : card.fluidInputs()) {
                x = wrapFlowX(x, rowStart, maxX, yy);
                drawFluidSlot(graphics, fluid, x, yy[0]);
                x += ICON_COL;
            }
        }
        boolean needArrow = (!fluidsInLayout && !card.fluidOutputs().isEmpty()) || !card.otherOutputs().isEmpty();
        if (needArrow) {
            x = wrapFlowX(x, rowStart, maxX, yy);
            graphics.drawString(this.font, "->", x, yy[0] + 4, 0xA0A0A0, false);
            x += 14;
        }
        if (!fluidsInLayout) {
            for (FluidStack fluid : card.fluidOutputs()) {
                x = wrapFlowX(x, rowStart, maxX, yy);
                drawFluidSlot(graphics, fluid, x, yy[0]);
                x += ICON_COL;
            }
        }
        for (RecipeExtra extra : card.otherOutputs()) {
            x = wrapFlowX(x, rowStart, maxX, yy);
            drawExtraSlot(graphics, extra, x, yy[0]);
            x += ICON_COL;
        }
    }

    /** Draw JEI-shaped slots scaled to panel width; returns y below the shaped block. */
    private int renderShapedInputs(GuiGraphics graphics, RecipeCard card, int left, int top) {
        float scale = shapedScale(card);
        List<RecipeCard.PlacedItem> placed = card.placedInputs();
        if (placed == null || placed.isEmpty()) {
            return top;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (RecipeCard.PlacedItem p : placed) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
        }
        int bh = Math.max(ICON_SIZE, maxY - minY + ICON_SIZE);
        // When scale < 1, still draw ICON_SIZE icons at scaled positions (may overlap slightly — ok).
        int step = Math.max(10, Math.round(ICON_SIZE * scale));
        for (RecipeCard.PlacedItem p : placed) {
                int sx = left + Math.round((p.x() - minX) * scale);
                int sy = top + Math.round((p.y() - minY) * scale);
                if (scale >= 0.999f && sx + ICON_SIZE > left + this.panelWidth) {
                    continue;
                }
            int bg = shapedSlotBg(p.kind());
            graphics.fill(sx, sy, sx + Math.min(ICON_SIZE, step), sy + Math.min(ICON_SIZE, step), bg);
            if (!p.stack().isEmpty()) {
                graphics.renderItem(p.stack(), sx, sy);
                addItemHover(sx, sy, p.stack());
            }
        }
        return top + Math.round(bh * scale) + 4;
    }

    private static boolean shapedHasKind(RecipeCard card, RecipeCard.SlotKind kind) {
        if (card == null || card.placedInputs() == null || kind == null) {
            return false;
        }
        for (RecipeCard.PlacedItem p : card.placedInputs()) {
            if (p != null && p.kind() == kind) {
                return true;
            }
        }
        return false;
    }

    private static int shapedSlotBg(RecipeCard.SlotKind kind) {
        if (kind == RecipeCard.SlotKind.CATALYST) {
            return 0x44004466;
        }
        if (kind == RecipeCard.SlotKind.OUTPUT) {
            return 0x66442200;
        }
        if (kind == RecipeCard.SlotKind.RENDER) {
            return 0x44333333;
        }
        return 0x66000000;
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
            graphics.renderItemDecorations(this.font, stack, x, y);
            addItemHover(x, y, stack);
        }
    }

    private void drawFluidSlot(GuiGraphics graphics, FluidStack fluid, int x, int y) {
        drawFluidSlot(graphics, fluid, x, y, ICON_SIZE, ICON_SIZE);
    }

    private void drawFluidSlot(GuiGraphics graphics, FluidStack fluid, int x, int y, int w, int h) {
        int ww = Math.max(4, w);
        int hh = Math.max(4, h);
        graphics.fill(x, y, x + ww, y + hh, 0x66000000);
        if (fluid == null || fluid.isEmpty()) {
            return;
        }
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation still = ext.getStillTexture(fluid);
        int color = ext.getTintColor(fluid);
        int inset = Math.min(2, Math.min(ww, hh) / 4);
        if (still != null && this.minecraft != null) {
            var sprite = this.minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(still);
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            graphics.blit(x + inset, y + inset, 0, ww - inset * 2, hh - inset * 2, sprite, r, g, b, 1.0F);
        } else {
            graphics.fill(
                    x + inset, y + inset, x + ww - inset, y + hh - inset,
                    0xFF000000 | (color & 0xFFFFFF));
        }
        addFluidHover(x, y, ww, hh, fluid);
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
        addItemHoverBounds(x, y, x + ICON_SIZE, y + ICON_SIZE, stack);
    }

    private void addItemHoverBounds(int x0, int y0, int x1, int y1, ItemStack stack) {
        if (stack == null || stack.isEmpty() || x1 <= x0 || y1 <= y0) {
            return;
        }
        this.hoverHits.add(new HoverHit(x0, y0, x1, y1, stack.copy(), FluidStack.EMPTY, List.of()));
    }

    private void addFluidHover(int x, int y, FluidStack fluid) {
        addFluidHover(x, y, ICON_SIZE, ICON_SIZE, fluid);
    }

    private void addFluidHover(int x, int y, int w, int h, FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return;
        }
        this.hoverHits.add(new HoverHit(x, y, x + w, y + h, ItemStack.EMPTY, fluid.copy(), List.of()));
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
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.hoverHits.clear();
        this.questClickRects.clear();
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        int chatL = this.panelLeft - 4;
        int chatR = this.panelLeft + this.panelWidth + 4;
        int chatT = this.chatTop - 6;
        int chatB = this.chatBottom + 4;
        GuiShell.panel(graphics, chatL, chatT, chatR, chatB, GuiShell.FILL_PRIMARY, GuiShell.BORDER);
        GuiShell.accentBar(graphics, chatL, chatT, chatR);

        int sideL = this.sideLeft - 4;
        int sideR = this.sideLeft + this.sideWidth + 4;
        GuiShell.panel(graphics, sideL, chatT, sideR, chatB, GuiShell.FILL_SECONDARY, GuiShell.BORDER_SOFT);
        if (this.sideDividerY > this.chatTop && this.sideDividerY < this.chatBottom) {
            GuiShell.hairlineH(graphics, this.sideLeft + 2, this.sideDividerY, this.sideLeft + this.sideWidth - 2);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        GuiShell.title(graphics, this.font, this.title, this.width / 2, 8);

        List<ChatLine> lines = chatLines();
        int max = maxScroll(lines);
        if (this.stickToBottom) {
            this.scrollOffset = max;
        } else {
            this.scrollOffset = Mth.clamp(this.scrollOffset, 0, max);
        }

        graphics.enableScissor(this.panelLeft - 2, this.chatTop, this.panelLeft + this.panelWidth + 2, this.chatBottom);
        int y = this.chatTop - (int) this.scrollOffset;
        for (ChatLine line : lines) {
            int stride = strideOf(line);
            if (y + stride >= this.chatTop && y <= this.chatBottom) {
                if (line.recipeCard() != null) {
                    RecipeCard rc = line.recipeCard();
                    int cardTop = y + line.extraPad();
                    renderRecipeCard(graphics, rc, this.panelLeft, cardTop);
                    // FTB draws underlined quest title in top ~20px of JEI drawable — click opens book.
                    Runnable open = questOpenAction(rc, Plainify.stripMcFormat(rc.categoryTitle()));
                    if (open != null) {
                        int stripH = Math.min(20, Math.max(8, strideOf(line) - line.extraPad()));
                        int stripW = Math.max(8, Math.min(this.panelWidth, JeiLayoutDraw.width(rc)));
                        if (stripW <= 8) {
                            stripW = this.panelWidth;
                        }
                        this.questClickRects.add(new QuestClickRect(
                                this.panelLeft, cardTop,
                                this.panelLeft + stripW, cardTop + stripH,
                                open));
                    }
                } else if (line.hasSpans()) {
                    int textY = y + line.extraPad()
                            + Math.max(0, (stride - line.extraPad() - this.font.lineHeight) / 2);
                    int x = this.panelLeft;
                    for (InlinePiece piece : line.spans()) {
                        if (piece.isItem()) {
                            int iy = y + line.extraPad()
                                    + Math.max(0, (stride - line.extraPad() - ICON_SIZE) / 2);
                            graphics.renderItem(piece.item(), x, iy);
                            graphics.renderItemDecorations(this.font, piece.item(), x, iy);
                            addItemHover(x, iy, piece.item());
                            x += ICON_SIZE + 2;
                        } else if (!piece.text().isEmpty()) {
                            int drawColor = piece.isLink() ? QUEST_LINK_COLOR : line.color();
                            graphics.drawString(this.font, piece.text(), x, textY, drawColor, false);
                            int tw = this.font.width(piece.text());
                            if (piece.isLink()) {
                                int underlineY = textY + this.font.lineHeight;
                                graphics.fill(x, underlineY, x + tw, underlineY + 1, 0xFF6EC8FF);
                                this.questClickRects.add(new QuestClickRect(
                                        x, y, x + Math.max(tw, 8), y + stride, piece.click()));
                            }
                            x += tw;
                        }
                    }
                } else {
                    int textY = y + line.extraPad() + Math.max(0, (stride - line.extraPad() - this.font.lineHeight) / 2);
                    if (!line.icon().isEmpty()) {
                        graphics.renderItem(line.icon(), this.panelLeft, y + line.extraPad());
                        addItemHover(this.panelLeft, y + line.extraPad(), line.icon());
                        if (line.text() != FormattedCharSequence.EMPTY) {
                            graphics.drawString(this.font, line.text(), this.panelLeft + ICON_COL, textY,
                                    line.color(), false);
                        }
                    } else if (!line.iconRow().isEmpty()) {
                        int ix = this.panelLeft;
                        for (ItemStack st : line.iconRow()) {
                            graphics.renderItem(st, ix, y + line.extraPad());
                            addItemHover(ix, y + line.extraPad(), st);
                            ix += ICON_COL;
                        }
                    } else if (line.text() != FormattedCharSequence.EMPTY) {
                        graphics.drawString(this.font, line.text(), this.panelLeft, textY, line.color(), false);
                        if (line.clickAction() != null) {
                            int tw = this.font.width(line.text());
                            int underlineY = textY + this.font.lineHeight;
                            graphics.fill(
                                    this.panelLeft, underlineY,
                                    this.panelLeft + tw, underlineY + 1,
                                    0xFF6EC8FF);
                            this.questClickRects.add(new QuestClickRect(
                                    this.panelLeft, y,
                                    this.panelLeft + Math.max(tw, 8), y + stride,
                                    line.clickAction()));
                        }
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
        drawChatScrollbar(graphics, max);
        // After chat panel so icons + hover hits sit above fills / scrollbar.
        renderInputHeldStrip(graphics);
        renderHoverTooltip(graphics, mouseX, mouseY);
        // WidgetTooltipHolder uses focused=override; focused input steals tip away from jump/settings.
        preferMouseWidgetTooltip(mouseX, mouseY);
    }

    /** Prefer tip of the widget under the mouse (Forge WidgetCompat.renderHoveredTips parity). */
    private void preferMouseWidgetTooltip(int mouseX, int mouseY) {
        clearTooltipForNextRenderPass();
        for (var child : this.children()) {
            if (!(child instanceof AbstractWidget w) || !w.visible || !w.isMouseOver(mouseX, mouseY)) {
                continue;
            }
            Tooltip tip = w.getTooltip();
            if (tip != null) {
                setTooltipForNextRenderPass(tip, DefaultTooltipPositioner.INSTANCE, true);
                return;
            }
        }
    }

    private record QuestClickRect(int x0, int y0, int x1, int y1, Runnable action) {}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && beginScrollbarDrag(mouseX, mouseY)) {
            return true;
        }
        if (button == 0
                && mouseY >= this.chatTop && mouseY <= this.chatBottom
                && mouseX >= this.panelLeft - 2 && mouseX <= this.panelLeft + this.panelWidth + 2) {
            for (QuestClickRect hit : this.questClickRects) {
                if (mouseX >= hit.x0() && mouseX < hit.x1() && mouseY >= hit.y0() && mouseY < hit.y1()) {
                    if (hit.action() != null) {
                        hit.action().run();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.draggingScrollbar) {
            int max = maxScroll(chatLines());
            if (max <= 0) {
                this.draggingScrollbar = false;
                return true;
            }
            applyScrollbarDrag(mouseY, max);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.draggingScrollbar) {
            this.draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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

    @Override
    public void onClose() {
        // Drop pin on close; lastAskFocus lives only while this screen instance exists.
        JeiTargetResolver.clearPin();
        rememberDraft();
        super.onClose();
    }
}

package com.skps9.packai.client.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.chat.ChatMessage;
import com.skps9.packai.client.chat.ChatSession;
import com.skps9.packai.client.context.GameContextCollector;
import com.skps9.packai.client.context.SeasonContext;
import com.skps9.packai.client.context.TooltipCapture;
import com.skps9.packai.client.jei.JeiLookup;
import com.skps9.packai.client.jei.JeiRecipeCards;
import com.skps9.packai.client.jei.JeiTargetResolver;
import com.skps9.packai.client.knowledge.PackKnowledge;
import com.skps9.packai.client.patchouli.PatchouliGuideLookup;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.AskEngine;
import com.skps9.packai.logic.AskJeiHints;
import com.skps9.packai.logic.AskPurposeContext;
import com.skps9.packai.logic.AskResult;
import com.skps9.packai.logic.ContainedItems;
import com.skps9.packai.logic.ItemRef;
import com.skps9.packai.logic.ItemResolver;
import com.skps9.packai.logic.ItemVariantKeys;
import com.skps9.packai.logic.PackIndex;
import com.skps9.packai.logic.PsiHelper;
import com.skps9.packai.logic.RecipeCard;
import com.skps9.packai.logic.ReplyLang;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

/**
 * Client ask entry — capture item text on the game thread, then run AskEngine off-thread.
 */
public final class AskService {
    public static final AskService INSTANCE = new AskService();

    private AskService() {}

    public void askAsync(String question, Consumer<AskResult> onResult) {
        askAsync(question, List.of(), false, List.of(), ItemStack.EMPTY, onResult);
    }

    public void askAsync(
            String question,
            List<ItemRef> selectedItems,
            boolean questOverride,
            List<ChatMessage> history,
            Consumer<AskResult> onResult
    ) {
        askAsync(question, selectedItems, questOverride, history, ItemStack.EMPTY, onResult);
    }

    /**
     * @param stripFocus exact stack the assistant strip shows ({@code contextStack}); when non-empty,
     *                   do not re-resolve from the full question (id-in-question can diverge from draft/hover).
     */
    public void askAsync(
            String question,
            List<ItemRef> selectedItems,
            boolean questOverride,
            List<ChatMessage> history,
            ItemStack stripFocus,
            Consumer<AskResult> onResult
    ) {
        runAsk(question, selectedItems, questOverride, history, stripFocus, onResult);
    }

    private void runAsk(
            String question,
            List<ItemRef> selectedItems,
            boolean questOverride,
            List<ChatMessage> history,
            ItemStack stripFocus,
            Consumer<AskResult> onResult
    ) {
        Minecraft mc = Minecraft.getInstance();
        Path gameDir = mc.gameDirectory.toPath();
        List<String> modIds = loadedModIds();
        GameContextCollector.collect(false); // fingerprint / dim side effects only
        List<ItemRef> selected = normalizeSelected(selectedItems);

        ItemStack jeiTarget = resolveAskTarget(mc, question, stripFocus);
        JeiTargetResolver.clearPin();
        final ItemRef focusItem = resolveFocus(jeiTarget, selected);
        final List<ItemRef> extras = extrasFor(focusItem, selected);

        StringBuilder jeiBlock = new StringBuilder();
        final String replyLang = clientLanguageCode(mc);
        String season = mc.player == null
                ? ""
                : SeasonContext.summary(mc.player, modIds, question, focusItem.id(), replyLang);
        if (season != null && !season.isBlank()) {
            jeiBlock.append(season).append('\n');
        }
        String psi = PsiHelper.promptAddon(question, modIds, replyLang);
        if (!psi.isBlank()) {
            jeiBlock.append(psi).append('\n');
        }
        // Same stack for cards + JEI text — avoid empty summarize while cards resolve via focusItem.
        final ItemStack cardFocus = cardFocusStack(jeiTarget, focusItem);
        // Skip recipe cards / heavy JEI get-section for code/script/behavior asks (PURPOSE+index stay).
        final boolean attachCards = PackIndex.shouldAttachAskRecipeCards(question);
        final List<RecipeCard> recipeCards = PackKnowledge.shouldQueryJei() && attachCards
                ? collectAskRecipeCards(cardFocus, extras)
                : List.of();
        boolean hasCards = recipeCards != null && !recipeCards.isEmpty();
        String jeiSummary = PackKnowledge.shouldQueryJei() && attachCards
                ? JeiLookup.summarize(cardFocus)
                : null;
        String firstTitle = hasCards ? recipeCards.get(0).categoryTitle() : "";
        String chosen = PackKnowledge.shouldQueryJei() && attachCards
                ? AskJeiHints.chooseJeiSummaryText(replyLang, jeiSummary, hasCards, firstTitle)
                : null;
        if (chosen != null && !chosen.isBlank()) {
            if (!jeiBlock.isEmpty()) {
                jeiBlock.append('\n');
            }
            jeiBlock.append(chosen);
        } else if (attachCards && PackKnowledge.shouldQueryJei()
                && AskJeiHints.shouldAppendNoJeiRecipes(hasCards, focusItem.isPresent(), jeiTarget.isEmpty())) {
            jeiBlock.append(ReplyLang.jeiNoRecipes(replyLang));
        } else if (attachCards && PackKnowledge.shouldQueryJei()
                && AskJeiHints.shouldAppendJeiHintEmpty(hasCards, focusItem.isPresent(), jeiTarget.isEmpty())) {
            jeiBlock.append(ReplyLang.jeiHintEmpty(replyLang));
        } else if (attachCards && !PackKnowledge.shouldQueryJei() && focusItem.isPresent()) {
            String gap = PackKnowledge.recipeGetGapOrEmpty(replyLang);
            if (!gap.isBlank()) {
                if (!jeiBlock.isEmpty()) {
                    jeiBlock.append('\n');
                }
                jeiBlock.append(gap);
            }
        }
        if (PackKnowledge.shouldQueryJei() && attachCards) {
            appendExtrasJei(jeiBlock, extras, recipeCards, replyLang);
        }
        final String jei = jeiBlock.isEmpty() ? null : jeiBlock.toString().trim();
        final List<ChatMessage> prior = history == null ? List.of() : List.copyOf(history);
        final String purposeTooltip = mergeExtrasPurpose(
                purposeTooltipFor(jeiTarget, mc.player), extras, mc.player);
        final String purposeGuide = (jeiTarget != null && !jeiTarget.isEmpty())
                ? PatchouliGuideLookup.lookup(jeiTarget)
                : "";

        CompletableFuture.supplyAsync(() -> {
                    try {
                        return AskEngine.INSTANCE.ask(
                                question, gameDir, modIds, focusItem, extras, questOverride, jei, prior,
                                replyLang, purposeTooltip, purposeGuide);
                    } catch (Exception e) {
                        PackAiMod.LOGGER.error("AskEngine failed", e);
                        return AskResult.text(ReplyLang.queryFailed(replyLang, e.getMessage()));
                    }
                })
                .whenComplete((result, err) -> mc.execute(() -> {
                    if (err != null) {
                        PackAiMod.LOGGER.error("Ask failed", err);
                        onResult.accept(AskResult.text("Error: " + err.getMessage()));
                    } else if (result == null) {
                        onResult.accept(AskResult.text(""));
                    } else {
                        onResult.accept(result.withRecipeCards(recipeCards));
                    }
                }));
    }

    /** Prefer strip focus; else resolveStable(question) — no live JEI hover. */
    static ItemStack resolveAskTarget(Minecraft mc, String question, ItemStack stripFocus) {
        if (stripFocus != null && !stripFocus.isEmpty()) {
            return stripFocus.copy();
        }
        return JeiTargetResolver.resolveStable(mc, question);
    }

    /** Tooltip + furnace fuel / ToolActions for Ask {@code [PURPOSE]}; optional [CONTAINED]. */
    static String purposeTooltipFor(ItemStack stack, net.minecraft.client.player.LocalPlayer player) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String tip = TooltipCapture.capture(stack, player);
        String purpose = AskPurposeContext.withItemBehavior(tip, AskPurposeContext.itemBehaviorLines(stack));
        String variant = ItemVariantKeys.purposeLine(stack);
        if (variant != null && !variant.isBlank()) {
            purpose = purpose == null || purpose.isBlank() ? variant : variant + "\n" + purpose;
        }
        if (!PackAiConfig.unpackStoredItems()) {
            return purpose;
        }
        String contained = ContainedItems.summarize(stack);
        if (contained == null || contained.isBlank()) {
            return purpose;
        }
        if (purpose == null || purpose.isBlank()) {
            return contained;
        }
        return purpose + "\n" + contained;
    }

    /** Cap rich PURPOSE/JEI for multi-select extras (pending max → all non-focus). */
    static final int MAX_EXTRAS_CONTEXT = ChatSession.MAX_PENDING_ITEMS - 1;
    /** Per-extra JEI dump — was 400 (too short for Create / multi-page crafts). */
    static final int MAX_EXTRAS_JEI_CHARS_EACH = 1800;
    static final int MAX_EXTRAS_JEI_CHARS_TOTAL = MAX_EXTRAS_JEI_CHARS_EACH * MAX_EXTRAS_CONTEXT;

    /** Append PURPOSE briefs for also-selected items (fuel / tool actions / tooltip). */
    static String mergeExtrasPurpose(
            String focusPurpose,
            List<ItemRef> extras,
            net.minecraft.client.player.LocalPlayer player
    ) {
        String extrasBlock = extrasPurposeBlock(extras, player);
        if (extrasBlock.isBlank()) {
            return focusPurpose == null ? "" : focusPurpose;
        }
        if (focusPurpose == null || focusPurpose.isBlank()) {
            return extrasBlock;
        }
        return focusPurpose + "\n" + extrasBlock;
    }

    static String extrasPurposeBlock(List<ItemRef> extras, net.minecraft.client.player.LocalPlayer player) {
        if (extras == null || extras.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (ItemRef ref : extras) {
            if (n >= MAX_EXTRAS_CONTEXT) {
                break;
            }
            if (ref == null || !ref.isPresent()) {
                continue;
            }
            ItemStack stack = ItemResolver.stackFromRef(ref);
            if (stack.isEmpty()) {
                continue;
            }
            String tip = purposeTooltipFor(stack, player);
            if (tip == null || tip.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("--- alsoSelected: ").append(ref.id());
            String label = ref.label();
            if (label != null && !label.isBlank()) {
                sb.append(" (").append(label.trim()).append(')');
            }
            sb.append(" ---\n").append(tip.trim());
            n++;
        }
        return sb.toString();
    }

    /** Short JEI dumps for also-selected items (truncated). Ground absence when that item has cards. */
    static void appendExtrasJei(
            StringBuilder jeiBlock, List<ItemRef> extras, List<RecipeCard> recipeCards, String replyLang
    ) {
        if (jeiBlock == null || extras == null || extras.isEmpty()) {
            return;
        }
        int n = 0;
        int total = 0;
        for (ItemRef ref : extras) {
            if (n >= MAX_EXTRAS_CONTEXT || total >= MAX_EXTRAS_JEI_CHARS_TOTAL) {
                break;
            }
            if (ref == null || !ref.isPresent()) {
                continue;
            }
            ItemStack stack = ItemResolver.stackFromRef(ref);
            if (stack.isEmpty()) {
                continue;
            }
            String want = ref.id().toLowerCase(Locale.ROOT);
            boolean itemHasCards = false;
            String cardTitle = "";
            if (recipeCards != null) {
                for (RecipeCard c : recipeCards) {
                    if (c == null) {
                        continue;
                    }
                    String key = c.sectionKey();
                    if (key != null && want.equals(key.toLowerCase(Locale.ROOT))) {
                        itemHasCards = true;
                        if (cardTitle.isEmpty()) {
                            cardTitle = c.categoryTitle() == null ? "" : c.categoryTitle();
                        }
                    }
                }
            }
            String sum = JeiLookup.summarize(stack);
            String chosen = AskJeiHints.chooseJeiSummaryText(replyLang, sum, itemHasCards, cardTitle);
            if (chosen == null || chosen.isBlank()) {
                continue;
            }
            String clipped = chosen.length() > MAX_EXTRAS_JEI_CHARS_EACH
                    ? chosen.substring(0, MAX_EXTRAS_JEI_CHARS_EACH) + "…"
                    : chosen;
            if (!jeiBlock.isEmpty()) {
                jeiBlock.append('\n');
            }
            String header = "--- alsoSelected: " + ref.id() + " ---\n";
            jeiBlock.append(header).append(clipped);
            total += header.length() + clipped.length();
            n++;
        }
    }

    /**
     * Recipe cards for focus + also-selected. Per-item cap = {@link PackAiConfig#recipeCardsPerItem()};
     * single unique focus → 1 primary craft card (guide: step text + one JEI card).
     * Multi-select keeps configured per-item × itemCount budget.
     * Each item prefers Crafting/smelt cards ({@link JeiRecipeCards#forItem}) so Quests/Analyzer
     * cannot leave axes with zero craft grids.
     */
    static List<RecipeCard> collectAskRecipeCards(ItemStack focus, List<ItemRef> extras) {
        int configured = PackAiConfig.recipeCardsPerItem();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (focus != null && !focus.isEmpty()) {
            String fkey = selectionKey(fromStack(focus));
            if (!fkey.isEmpty()) {
                keys.add(fkey);
            }
        }
        if (extras != null) {
            for (ItemRef ref : extras) {
                if (ref == null || !ref.isPresent()) {
                    continue;
                }
                String key = selectionKey(ref);
                if (!key.isEmpty()) {
                    keys.add(key);
                }
            }
        }
        // Single focus: one primary R-card; multi-select still uses full per-item budget.
        int perItem = keys.size() <= 1 ? Math.min(configured, 1) : configured;
        List<RecipeCard> out = new ArrayList<>();
        LinkedHashSet<String> done = new LinkedHashSet<>();
        int items = 0;
        if (focus != null && !focus.isEmpty()) {
            items++;
            String fkey = selectionKey(fromStack(focus));
            if (!fkey.isEmpty()) {
                done.add(fkey);
            }
            out.addAll(JeiRecipeCards.forItem(focus, perItem));
        }
        if (extras != null) {
            for (ItemRef ref : extras) {
                if (ref == null || !ref.isPresent()) {
                    continue;
                }
                String key = selectionKey(ref);
                if (key.isEmpty() || !done.add(key)) {
                    continue;
                }
                ItemStack stack = ItemResolver.stackFromRef(ref);
                if (stack.isEmpty()) {
                    continue;
                }
                items++;
                out.addAll(JeiRecipeCards.forItem(stack, perItem));
            }
        }
        int budget = Math.max(1, items) * perItem;
        if (out.size() > budget) {
            return List.copyOf(out.subList(0, budget));
        }
        return List.copyOf(out);
    }

    /** When strip/JEI focus empty, still resolve first selected so it gets cards. */
    static ItemStack cardFocusStack(ItemStack jeiTarget, ItemRef focusItem) {
        if (jeiTarget != null && !jeiTarget.isEmpty()) {
            return jeiTarget;
        }
        if (focusItem != null && focusItem.isPresent()) {
            return ItemResolver.stackFromRef(focusItem);
        }
        return ItemStack.EMPTY;
    }

    public void warmupAsync() {
        CompletableFuture.runAsync(this::warmupBlocking);
    }

    public AskResult askBlocking(
            String question,
            List<ItemRef> selectedItems,
            boolean questOverride,
            List<ChatMessage> history
    ) {
        return askBlocking(question, selectedItems, questOverride, history, ItemStack.EMPTY);
    }

    /**
     * @param stripFocus exact stack the assistant strip shows ({@code contextStack}); when non-empty,
     *                   do not re-resolve from the full question (mirrors {@link #askAsync}).
     */
    public AskResult askBlocking(
            String question,
            List<ItemRef> selectedItems,
            boolean questOverride,
            List<ChatMessage> history,
            ItemStack stripFocus
    ) {
        Minecraft mc = Minecraft.getInstance();
        Path gameDir = mc.gameDirectory.toPath();
        List<String> modIds = loadedModIds();
        GameContextCollector.collect(false);
        List<ItemRef> selected = normalizeSelected(selectedItems);
        ItemStack jeiTarget = resolveAskTarget(mc, question, stripFocus);
        JeiTargetResolver.clearPin();
        ItemRef focusItem = resolveFocus(jeiTarget, selected);
        List<ItemRef> extras = extrasFor(focusItem, selected);
        final String replyLang = clientLanguageCode(mc);
        StringBuilder jeiBlock = new StringBuilder();
        String season = mc.player == null
                ? ""
                : SeasonContext.summary(mc.player, modIds, question, focusItem.id(), replyLang);
        if (season != null && !season.isBlank()) {
            jeiBlock.append(season).append('\n');
        }
        String psi = PsiHelper.promptAddon(question, modIds, replyLang);
        if (!psi.isBlank()) {
            jeiBlock.append(psi).append('\n');
        }
        ItemStack cardFocus = cardFocusStack(jeiTarget, focusItem);
        boolean attachCards = PackIndex.shouldAttachAskRecipeCards(question);
        List<RecipeCard> recipeCards = PackKnowledge.shouldQueryJei() && attachCards
                ? collectAskRecipeCards(cardFocus, extras)
                : List.of();
        boolean hasCards = recipeCards != null && !recipeCards.isEmpty();
        String jeiSummary = PackKnowledge.shouldQueryJei() && attachCards
                ? JeiLookup.summarize(cardFocus)
                : null;
        String firstTitle = hasCards ? recipeCards.get(0).categoryTitle() : "";
        String chosen = PackKnowledge.shouldQueryJei() && attachCards
                ? AskJeiHints.chooseJeiSummaryText(replyLang, jeiSummary, hasCards, firstTitle)
                : null;
        if (chosen != null && !chosen.isBlank()) {
            if (!jeiBlock.isEmpty()) {
                jeiBlock.append('\n');
            }
            jeiBlock.append(chosen);
        } else if (attachCards && PackKnowledge.shouldQueryJei()
                && AskJeiHints.shouldAppendNoJeiRecipes(hasCards, focusItem.isPresent(), jeiTarget.isEmpty())) {
            jeiBlock.append(ReplyLang.jeiNoRecipes(replyLang));
        } else if (attachCards && PackKnowledge.shouldQueryJei()
                && AskJeiHints.shouldAppendJeiHintEmpty(hasCards, focusItem.isPresent(), jeiTarget.isEmpty())) {
            jeiBlock.append(ReplyLang.jeiHintEmpty(replyLang));
        } else if (attachCards && !PackKnowledge.shouldQueryJei() && focusItem.isPresent()) {
            String gap = PackKnowledge.recipeGetGapOrEmpty(replyLang);
            if (!gap.isBlank()) {
                if (!jeiBlock.isEmpty()) {
                    jeiBlock.append('\n');
                }
                jeiBlock.append(gap);
            }
        }
        if (PackKnowledge.shouldQueryJei() && attachCards) {
            appendExtrasJei(jeiBlock, extras, recipeCards, replyLang);
        }
        final String jei = jeiBlock.isEmpty() ? null : jeiBlock.toString().trim();
        final String purposeTooltip = mergeExtrasPurpose(
                purposeTooltipFor(jeiTarget, mc.player), extras, mc.player);
        final String purposeGuide = (jeiTarget != null && !jeiTarget.isEmpty())
                ? PatchouliGuideLookup.lookup(jeiTarget)
                : "";
        try {
            AskResult result = AskEngine.INSTANCE.ask(
                    question, gameDir, modIds, focusItem, extras, questOverride, jei,
                    history == null ? List.of() : history,
                    replyLang, purposeTooltip, purposeGuide);
            return result.withRecipeCards(recipeCards);
        } catch (Exception e) {
            PackAiMod.LOGGER.error("AskEngine failed", e);
            return AskResult.text(ReplyLang.queryFailed(replyLang, e.getMessage()));
        }
    }

    /** JEI pin / id-in-question wins; else first selected; else none (no auto-held). */
    static ItemRef resolveFocus(ItemStack jeiTarget, List<ItemRef> selected) {
        if (jeiTarget != null && !jeiTarget.isEmpty()) {
            var key = Registry.ITEM.getKey(jeiTarget.getItem());
            if (key != null) {
                return new ItemRef(key.toString(), jeiTarget.getHoverName().getString(), jeiTarget);
            }
        }
        if (selected != null) {
            for (ItemRef ref : selected) {
                if (ref != null && ref.isPresent()) {
                    return ref;
                }
            }
        }
        return ItemRef.NONE;
    }

    static List<ItemRef> extrasFor(ItemRef focus, List<ItemRef> selected) {
        if (selected == null || selected.isEmpty()) {
            return List.of();
        }
        String focusKey = selectionKey(focus);
        List<ItemRef> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (ItemRef ref : selected) {
            if (ref == null || !ref.isPresent()) {
                continue;
            }
            String key = selectionKey(ref);
            if (key.equals(focusKey) || !seen.add(key)) {
                continue;
            }
            out.add(ref);
        }
        return out;
    }

    static List<ItemRef> normalizeSelected(List<ItemRef> selectedItems) {
        List<ItemRef> raw = selectedItems == null ? ChatSession.pendingItems() : selectedItems;
        List<ItemRef> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (ItemRef ref : raw) {
            if (ref == null || !ref.isPresent()) {
                continue;
            }
            if (!seen.add(selectionKey(ref))) {
                continue;
            }
            out.add(ref);
            if (out.size() >= ChatSession.MAX_PENDING_ITEMS) {
                break;
            }
        }
        return out;
    }

    /**
     * Multi-select dedupe key: registry id + schematic (or sample label) so Tetra
     * {@code scroll_rolled} NBT variants stay distinct.
     */
    public static String selectionKey(ItemRef ref) {
        if (ref == null || !ref.isPresent()) {
            return "";
        }
        String id = ref.id().toLowerCase(Locale.ROOT);
        List<String> schems = ItemVariantKeys.schematics(ref.sample());
        if (!schems.isEmpty()) {
            LinkedHashSet<String> norm = new LinkedHashSet<>();
            for (String s : schems) {
                if (s != null && !s.isBlank()) {
                    norm.add(s.trim().toLowerCase(Locale.ROOT));
                }
            }
            if (!norm.isEmpty()) {
                return id + "#" + String.join(",", norm);
            }
        }
        if (ref.hasSample()) {
            String label = ref.label();
            if (label != null && !label.isBlank()) {
                return id + "@" + label.trim().toLowerCase(Locale.ROOT);
            }
        }
        return id;
    }

    public static ItemRef fromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemRef.NONE;
        }
        var key = Registry.ITEM.getKey(stack.getItem());
        if (key == null) {
            return ItemRef.NONE;
        }
        return new ItemRef(key.toString(), stack.getHoverName().getString(), stack);
    }

    static String clientLanguageCode(Minecraft mc) {
        return ReplyLang.current();
    }

    private void warmupBlocking() {
        try {
            Minecraft mc = Minecraft.getInstance();
            GameContextCollector.resetFingerprintCache();
            AskEngine.INSTANCE.warmup(mc.gameDirectory.toPath(), loadedModIds());
            PackAiMod.LOGGER.info("Pack AI index warmup done");
        } catch (Exception e) {
            PackAiMod.LOGGER.debug("Pack AI warmup skipped: {}", e.toString());
        }
    }

    private static List<String> loadedModIds() {
        List<String> modIds = new ArrayList<>();
        for (IModInfo info : ModList.get().getMods()) {
            modIds.add(info.getModId());
        }
        modIds.sort(String::compareTo);
        return modIds;
    }
}

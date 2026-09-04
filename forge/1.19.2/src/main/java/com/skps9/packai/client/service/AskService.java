package com.skps9.packai.client.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.chat.ChatMessage;
import com.skps9.packai.client.chat.ChatSession;
import com.skps9.packai.client.context.GameContextCollector;
import com.skps9.packai.client.context.SeasonContext;
import com.skps9.packai.client.context.TooltipCapture;
import com.skps9.packai.client.jei.JeiInfoPages;
import com.skps9.packai.client.jei.JeiLookup;
import com.skps9.packai.client.jei.JeiRecipeCards;
import com.skps9.packai.client.jei.JeiTargetResolver;
import com.skps9.packai.client.jei.JeiTypedLookup;
import com.skps9.packai.client.knowledge.PackKnowledge;
import com.skps9.packai.client.patchouli.PatchouliGuideLookup;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.AskCardFallback;
import com.skps9.packai.logic.AskReplyScrub;
import com.skps9.packai.logic.AskEngine;
import com.skps9.packai.logic.AskLoopState;
import com.skps9.packai.logic.AskNameResolve;
import com.skps9.packai.logic.AskToolLoop;
import com.skps9.packai.logic.AskJeiHints;
import com.skps9.packai.logic.AskPurposeContext;
import com.skps9.packai.logic.AskResult;
import com.skps9.packai.logic.ContainedItems;
import com.skps9.packai.logic.FormatRequirements;
import com.skps9.packai.logic.ItemConsumeUseFacts;
import com.skps9.packai.logic.ItemRef;
import com.skps9.packai.logic.ItemResolver;
import com.skps9.packai.logic.ItemVariantKeys;
import com.skps9.packai.logic.ModularToolScan;
import com.skps9.packai.logic.TetraMaterialItems;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.PsiHelper;
import com.skps9.packai.logic.QuestGuide;
import com.skps9.packai.logic.RecipeCard;
import com.skps9.packai.logic.AskToolContext;
import com.skps9.packai.logic.RecipeCardsMode;
import com.skps9.packai.logic.RecipeGetMarks;
import com.skps9.packai.logic.RecipeExtra;
import com.skps9.packai.logic.RecipeIoSummary;
import com.skps9.packai.logic.ReplyLang;
import com.skps9.packai.logic.SummonRecipeLookup;
import com.skps9.packai.logic.TetraSchematicText;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
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
        final String jeiFocusItemId = cardFocusItemId(cardFocus);
        // Recipe-card attach mode (keywords / ai / always / never).
        final RecipeCardsMode cardsMode = RecipeCardsMode.current();
        final boolean attachCards = cardsMode.shouldCollect(question);
        final List<RecipeCard> recipeCards = PackKnowledge.shouldQueryJei() && attachCards
                ? collectAskRecipeCards(cardFocus, extras, question)
                : List.of();
        boolean hasCards = recipeCards != null && !recipeCards.isEmpty();
        if (attachCards) {
            String focusId = cardFocus == null || cardFocus.isEmpty()
                    ? "-"
                    : String.valueOf(Registry.ITEM.getKey(cardFocus.getItem()));
            if (hasCards) {
                RecipeCard first = recipeCards.get(0);
                PackAiMod.LOGGER.info(
                        "Pack AI recipe cards focus={} count={} firstLayout={} jeiDrawable={} cats={}",
                        focusId,
                        recipeCards.size(),
                        first.layout(),
                        com.skps9.packai.client.jei.JeiLayoutDraw.hasLayout(first),
                        cardCatTitles(recipeCards));
            } else {
                PackAiMod.LOGGER.info("Pack AI recipe cards focus={} count=0", focusId);
            }
        }
        // Plan B: intent-gated JEI text (SLIM vs OUTPUT); recipe cards stay local.
        final AskToolContext.JeiDumpLevel jeiLevel = AskToolContext.jeiDumpLevel(question);
        String jeiSummary = PackKnowledge.shouldQueryJei() && attachCards
                ? JeiLookup.summarize(cardFocus, jeiLevel)
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
            appendRecipeCardsCatalog(jeiBlock, recipeCards, replyLang);
            appendSummonFact(jeiBlock, question, recipeCards, cardFocus);
            appendRequirements(jeiBlock, recipeCards, replyLang);
        }
        // Machine brief is independent of recipe-card attach — any JEI-catalyst focus gets it.
        if (PackKnowledge.shouldQueryJei()) {
            appendJeiInfoPages(jeiBlock, cardFocus, replyLang);
            String machine = PackKnowledge.machineBriefSectionOrEmpty(cardFocus, question, replyLang);
            if (!machine.isBlank()) {
                if (!jeiBlock.isEmpty()) {
                    jeiBlock.append('\n');
                }
                jeiBlock.append(RecipeGetMarks.MACHINE_MARK).append(machine);
            }
        }
        final String jei = jeiBlock.isEmpty() ? null : jeiBlock.toString().trim();
        final List<ChatMessage> prior = history == null ? List.of() : List.copyOf(history);
        final String purposeTooltip = mergeExtrasPurpose(
                purposeTooltipFor(jeiTarget, mc.player), extras, mc.player);
        final ItemStack guideStack =
                (jeiTarget != null && !jeiTarget.isEmpty()) ? jeiTarget : ItemStack.EMPTY;
        // Craft cards only — scroll materials go inline in answer (not FLOW strip).
        final List<RecipeCard> cardsCollected = recipeCards == null ? List.of() : List.copyOf(recipeCards);
        final String askQuestion = question;
        final AskLoopState askLoop = beginAskLoop(question, focusItem, cardFocus, jeiLevel, jeiSummary);
        askLoop.setRecipeCardLines(catalogLines(cardsCollected, replyLang));
        PackAiMod.LOGGER.info("Pack AI Ask replyLang={} jeiLevel={}", replyLang, jeiLevel);
        final String askJeiFocusItemId = jeiFocusItemId;

        CompletableFuture.supplyAsync(() -> {
                    try {
                        // Lookup on worker so awaitReady can wait without client-thread deadlock.
                        String purposeGuide = PatchouliGuideLookup.lookup(guideStack, askQuestion);
                        return AskEngine.INSTANCE.ask(
                                question, gameDir, modIds, focusItem, extras, questOverride, jei, prior,
                                replyLang, purposeTooltip, purposeGuide, askJeiFocusItemId, askLoop);
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
                        String miss = ReplyLang.jeiHintEmpty(replyLang).trim();
                        if (miss.isBlank()) {
                            miss = ReplyLang.friendlyOffline(replyLang, askQuestion);
                        }
                        onResult.accept(AskResult.text(miss));
                    } else {
                        String scrubbed = AskReplyScrub.stripDuplicateSectionHeaders(result.answer());
                        PackAiMod.LOGGER.info(
                                "Pack AI ask reply before ensureCards: {}",
                                scrubbed.length() > 2000 ? scrubbed.substring(0, 2000) : scrubbed);
                        String patched = AskCardFallback.ensureCards(scrubbed, cardsCollected);
                        PackAiMod.LOGGER.info(
                                "Pack AI ask reply after ensureCards: {}",
                                patched.length() > 2000 ? patched.substring(0, 2000) : patched);
                        AskResult finalResult = patched.equals(result.answer()) ? result : result.withAnswer(patched);
                        Boolean marker = RecipeCardsMode.resolveGateMarker(finalResult.answer());
                        List<RecipeCard> cardsOut = cardsMode.resolveAttach(
                                cardsCollected, marker, askQuestion, finalResult.answer());
                        PackAiMod.LOGGER.info(
                                "Pack AI ask cardsOut count={} cats={}",
                                cardsOut == null ? 0 : cardsOut.size(),
                                cardCatTitles(cardsOut));
                        AskResult withCards = withScrollMaterialInline(finalResult, purposeTooltip, replyLang)
                                .withRecipeCards(cardsOut);
                        onResult.accept(dedupeQuestChatWhenCardShows(withCards));
                    }
                }));
    }

    /**
     * Wall clock starts at Ask click (includes client JEI). Shot-0 JEI fingerprint uses
     * live-stack variant keys so H3 live does not force a second identical lookup.
     */
    static AskLoopState beginAskLoop(
            String question,
            ItemRef focusItem,
            ItemStack cardFocus,
            AskToolContext.JeiDumpLevel jeiLevel,
            String jeiSummary
    ) {
        List<String> keys = ItemVariantKeys.schematics(cardFocus);
        String itemId = focusItem != null && focusItem.isPresent()
                ? focusItem.id()
                : cardFocusItemId(cardFocus);
        if (itemId == null) {
            itemId = "";
        }
        AskLoopState loop = AskLoopState.start(
                question, itemId, keys, System.currentTimeMillis() + AskToolLoop.WALL_MS);
        loop.setDumpLevel(jeiLevel == null ? "SLIM" : jeiLevel.name());
        String text = jeiSummary == null ? "" : jeiSummary;
        loop.noteShot0("jei_lookup", loop.dumpLevel(), keys, text);
        String catalog = AskEngine.recipeCardsCatalogSlim(text);
        if (catalog != null) {
            loop.setRecipeCatalog(catalog);
        }
        return loop;
    }

    /**
     * When a recipe card category title equals a related-quest title, drop redundant
     * 「另有相关任务」chat asides. Keep quests on the result for card-caption open_book.
     */
    static AskResult dedupeQuestChatWhenCardShows(AskResult result) {
        if (result == null) {
            return AskResult.text("");
        }
        Set<String> covered = QuestGuide.questTitlesCoveredByCards(result.quests(), result.recipeCards());
        if (covered.isEmpty()) {
            return result;
        }
        String scrubbed = QuestGuide.scrubCoveredRelatedQuestLines(result.answer(), covered);
        if (scrubbed.equals(result.answer())) {
            return result;
        }
        return result.withAnswer(scrubbed);
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
        List<String> behavior = new ArrayList<>(AskPurposeContext.itemBehaviorLines(stack));
        behavior.addAll(ItemConsumeUseFacts.purposeLinesFor(stack));
        String purpose = AskPurposeContext.withItemBehavior(tip, behavior);
        String toolBuild = ModularToolScan.purposeLines(stack);
        if (toolBuild != null && !toolBuild.isBlank()) {
            purpose = purpose == null || purpose.isBlank() ? toolBuild : toolBuild + "\n" + purpose;
        }
        String tetraUse = TetraMaterialItems.purposeLines(stack);
        if (tetraUse != null && !tetraUse.isBlank()) {
            purpose = purpose == null || purpose.isBlank() ? tetraUse : tetraUse + "\n" + purpose;
        }
        String variant = ItemVariantKeys.purposeLine(stack);
        if (variant != null && !variant.isBlank()) {
            purpose = purpose == null || purpose.isBlank() ? variant : variant + "\n" + purpose;
        }
        String scrollMech = ItemVariantKeys.scrollMechanicsPurposeLines(stack, tip);
        if (scrollMech != null && !scrollMech.isBlank()) {
            purpose = purpose == null || purpose.isBlank() ? scrollMech : purpose + "\n" + scrollMech;
        }
        String scrollFx = ItemVariantKeys.scrollEffectPurposeLines(stack);
        if (scrollFx != null && !scrollFx.isBlank()) {
            purpose = purpose == null || purpose.isBlank() ? scrollFx : purpose + "\n" + scrollFx;
        }
        String scrollSchem = ItemVariantKeys.scrollSchematicPurposeLines(stack);
        if (scrollSchem != null && !scrollSchem.isBlank()) {
            purpose = purpose == null || purpose.isBlank() ? scrollSchem : purpose + "\n" + scrollSchem;
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
            // Extras always SLIM — never full U encyclopedia per also-selected item.
            String sum = JeiLookup.summarize(stack, AskToolContext.JeiDumpLevel.SLIM);
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
     * Indexed card list for the LLM — category + IO names only (facts stay JEI).
     * Order matches UI card indices for {@code [[recipe_card:N]]}.
     */
    static List<String> catalogLines(List<RecipeCard> recipeCards, String replyLang) {
        List<String> out = new ArrayList<>();
        if (recipeCards == null) {
            return out;
        }
        for (int i = 0; i < recipeCards.size(); i++) {
            RecipeCard c = recipeCards.get(i);
            if (c == null || c.isEmpty()) {
                continue;
            }
            out.add(i + " | " + promptCardLine(c, replyLang));
        }
        return out;
    }

    static void appendJeiInfoPages(StringBuilder jeiBlock, ItemStack focus, String replyLang) {
        if (jeiBlock == null || focus == null || focus.isEmpty()) {
            return;
        }
        String dump = JeiInfoPages.dump(focus, replyLang);
        if (dump == null || dump.isBlank()) {
            return;
        }
        if (!jeiBlock.isEmpty()) {
            jeiBlock.append('\n');
        }
        jeiBlock.append(dump);
    }

    static void appendRecipeCardsCatalog(StringBuilder jeiBlock, List<RecipeCard> recipeCards, String replyLang) {
        if (jeiBlock == null || recipeCards == null || recipeCards.isEmpty()) {
            return;
        }
        if (!jeiBlock.isEmpty()) {
            jeiBlock.append('\n');
        }
        jeiBlock.append(ReplyLang.recipeCardsCatalogLead(replyLang));
        for (int i = 0; i < recipeCards.size(); i++) {
            RecipeCard c = recipeCards.get(i);
            if (c == null || c.isEmpty()) {
                continue;
            }
            jeiBlock.append(i).append(" | ").append(promptCardLine(c, replyLang)).append('\n');
        }
    }

    /**
     * D4=B — REQUIREMENTS from card reqNotes only.
     * Unlock gates (#1B/#1C) stay per-card (footnote + {@link #promptCardLine}); never merge
     * sibling cards' UNKNOWN into a focus-wide REQUIREMENTS block.
     */
    static void appendRequirements(StringBuilder jeiBlock, List<RecipeCard> recipeCards, String replyLang) {
        if (jeiBlock == null || recipeCards == null || recipeCards.isEmpty()) {
            return;
        }
        List<String> notes = new ArrayList<>();
        for (RecipeCard c : recipeCards) {
            if (c == null) {
                continue;
            }
            if (c.reqNotes() != null && !c.reqNotes().isEmpty()) {
                notes.addAll(c.reqNotes());
            }
        }
        // unlockGates intentionally omitted — see promptCardLine / card footnotes
        String block = FormatRequirements.askBlock(List.of(), notes, List.of(), replyLang);
        if (block.isEmpty()) {
            return;
        }
        if (!jeiBlock.isEmpty() && jeiBlock.charAt(jeiBlock.length() - 1) != '\n') {
            jeiBlock.append('\n');
        }
        jeiBlock.append(block);
    }

    /** Readable category + inputs → outputs for prompt (no invented steps). */
    static String promptCardLine(RecipeCard c, String replyLang) {
        if (c == null) {
            return "?";
        }
        String role = c.promptRole();
        String cat = Plainify.stripMcFormat(c.categoryTitle());
        if (cat == null || cat.isBlank()) {
            cat = "?";
        }
        String head = "role=" + role + " | " + cat;
        String ins = RecipeIoSummary.joinStackNames(cardInputStacks(c));
        String outs = RecipeIoSummary.joinOutputSide(
                c.outputs(), fluidDisplayNames(c.fluidOutputs()), c.otherOutputs());
        String body;
        if (ins.isEmpty() && outs.isEmpty()) {
            body = head;
        } else if (ins.isEmpty()) {
            body = head + " | → " + outs;
        } else if (outs.isEmpty()) {
            body = head + " | " + ins;
        } else {
            body = head + " | " + ins + " → " + outs;
        }
        return body + promptCardUnlockSuffix(c, replyLang);
    }

    static void appendSummonFact(
            StringBuilder jeiBlock, String question, List<RecipeCard> recipeCards, ItemStack focus
    ) {
        if (jeiBlock == null) {
            return;
        }
        String loot = "";
        try {
            if (focus != null && !focus.isEmpty()) {
                loot = Plainify.stripMcFormat(focus.getHoverName().getString());
            }
        } catch (Throwable ignored) {
            // headless
        }
        String fact = SummonRecipeLookup.factLine(question, extraOutputLabels(recipeCards), loot);
        if (fact.isEmpty()) {
            return;
        }
        if (!jeiBlock.isEmpty() && jeiBlock.charAt(jeiBlock.length() - 1) != '\n') {
            jeiBlock.append('\n');
        }
        jeiBlock.append(fact);
    }

    static List<String> extraOutputLabels(List<RecipeCard> cards) {
        List<String> labels = new ArrayList<>();
        if (cards == null) {
            return labels;
        }
        for (RecipeCard card : cards) {
            if (card == null || card.otherOutputs() == null) {
                continue;
            }
            for (RecipeExtra extra : card.otherOutputs()) {
                if (extra != null && extra.label() != null && !extra.label().isBlank()) {
                    labels.add(extra.label());
                }
            }
        }
        return labels;
    }

    private static List<String> fluidDisplayNames(List<FluidStack> fluids) {
        if (fluids == null || fluids.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (FluidStack fluid : fluids) {
            if (fluid == null || fluid.isEmpty()) {
                continue;
            }
            try {
                String n = Plainify.stripMcFormat(fluid.getDisplayName().getString());
                if (n != null && !n.isBlank()) {
                    names.add(n);
                }
            } catch (Throwable ignored) {
                // headless
            }
        }
        return names;
    }

    /** Per-card unlock only — never import sibling recipe gates. */
    static String promptCardUnlockSuffix(RecipeCard c, String replyLang) {
        if (c == null || c.unlockGates() == null || c.unlockGates().isEmpty()) {
            return "";
        }
        String prefix = ReplyLang.unlockPrefix(replyLang);
        StringBuilder sb = new StringBuilder();
        for (String g : c.unlockGates()) {
            if (g == null || g.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(prefix).append(g.trim());
        }
        return sb.length() == 0 ? "" : " | " + sb;
    }

    private static List<ItemStack> cardInputStacks(RecipeCard c) {
        if (c == null) {
            return List.of();
        }
        if (c.layout() == RecipeCard.Layout.CRAFTING_3X3 && c.grid() != null && !c.grid().isEmpty()) {
            return c.grid();
        }
        if (c.layout() == RecipeCard.Layout.SHAPED && c.placedInputs() != null && !c.placedInputs().isEmpty()) {
            List<ItemStack> out = new ArrayList<>();
            for (RecipeCard.PlacedItem p : c.placedInputs()) {
                if (p != null && p.kind() == RecipeCard.SlotKind.INPUT
                        && p.stack() != null && !p.stack().isEmpty()) {
                    out.add(p.stack());
                }
            }
            return out;
        }
        return c.inputs() == null ? List.of() : c.inputs();
    }

    /** Delegates to {@link RecipeIoSummary#joinStackNames}. */
    static String joinStackNames(List<ItemStack> stacks) {
        return RecipeIoSummary.joinStackNames(stacks);
    }

    static String cardCatTitles(List<RecipeCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (RecipeCard c : cards) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(c == null || c.categoryTitle() == null ? "?" : c.categoryTitle());
        }
        return sb.toString();
    }

    /**
     * Recipe cards for focus + also-selected. Per-item caps:
     * {@link PackAiConfig#recipeCardsPerItem()} OUTPUT (obtain) and
     * {@link PackAiConfig#recipeCardsPerItemUse()} INPUT (uses) — independent.
     * Total budget ≈ itemCount × (perOut + perUse).
     * Each item prefers Crafting/smelt cards ({@link JeiRecipeCards#forItem}) so Quests/Analyzer
     * cannot leave axes with zero craft grids.
     */
    static List<RecipeCard> collectAskRecipeCards(ItemStack focus, List<ItemRef> extras) {
        return collectAskRecipeCards(focus, extras, "");
    }

    static List<RecipeCard> collectAskRecipeCards(ItemStack focus, List<ItemRef> extras, String question) {
        int perOut = PackAiConfig.recipeCardsPerItem();
        int perUse = PackAiConfig.recipeCardsPerItemUse();
        List<RecipeCard> out = new ArrayList<>();
        LinkedHashSet<String> done = new LinkedHashSet<>();
        int items = 0;
        if (focus != null && !focus.isEmpty()) {
            items++;
            String fkey = selectionKey(fromStack(focus));
            if (!fkey.isEmpty()) {
                done.add(fkey);
            }
            out.addAll(JeiRecipeCards.forItem(focus, perOut, perUse));
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
                out.addAll(JeiRecipeCards.forItem(stack, perOut, perUse));
            }
        }
        if (out.isEmpty() || AskNameResolve.mergeTypedCards(question)) {
            List<RecipeCard> typed = JeiTypedLookup.cardsForQuestion(question);
            if (!typed.isEmpty()) {
                List<RecipeCard> merged = new ArrayList<>(typed.size() + out.size());
                merged.addAll(typed);
                merged.addAll(out);
                out = merged;
            }
        }
        int budget = Math.max(1, items) * (perOut + perUse);
        if (out.size() > budget) {
            return List.copyOf(out.subList(0, budget));
        }
        return List.copyOf(out);
    }

    /** Registry id from card/JEI focus stack (null when empty). */
    static String cardFocusItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        var key = Registry.ITEM.getKey(stack.getItem());
        return key == null ? null : key.toString();
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

    /**
     * Inject Tetra workbench materials as {@code {{item:id×N}}} into answer text.
     * No RecipeCard strip — avoids floating material row above prose.
     */
    static AskResult withScrollMaterialInline(AskResult result, String purpose, String replyLang) {
        if (result == null) {
            return AskResult.text("");
        }
        if (!TetraSchematicText.hasScrollMaterials(purpose)) {
            return result;
        }
        String title = screenLang("packai.screen.scroll_materials_title", "Workbench materials (pick one)");
        String noneLabel = screenLang("packai.screen.scroll_materials_none", "No material required");
        String injected = TetraSchematicText.injectInlineMaterials(result.answer(), purpose, title, noneLabel);
        if (injected.equals(result.answer())) {
            return result;
        }
        PackAiMod.LOGGER.info(
                "Pack AI scroll material inline none={} markers={}",
                TetraSchematicText.saysNoMaterials(purpose),
                injected.contains("{{item:"));
        return result.withAnswer(injected);
    }

    static List<RecipeCard> withScrollMaterialCards(
            List<RecipeCard> craftCards,
            String purpose,
            ItemStack focus,
            String replyLang
    ) {
        // ponytail: strip demoted — inline inject is primary; pass craft cards through.
        return craftCards == null ? List.of() : craftCards;
    }

    static RecipeCard scrollMaterialCardOrNull(String purpose, ItemStack focus, String replyLang) {
        // Demoted: no longer attached. Return null so callers/tests see strip off.
        return null;
    }

    private static String screenLang(String key, String fallback) {
        try {
            String t = net.minecraft.client.resources.language.I18n.get(key);
            if (t != null && !t.isBlank() && !t.equals(key)) {
                return t;
            }
        } catch (Throwable ignored) {
            // headless
        }
        return fallback;
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
        String jeiFocusItemId = cardFocusItemId(cardFocus);
        RecipeCardsMode cardsMode = RecipeCardsMode.current();
        boolean attachCards = cardsMode.shouldCollect(question);
        List<RecipeCard> recipeCards = PackKnowledge.shouldQueryJei() && attachCards
                ? collectAskRecipeCards(cardFocus, extras, question)
                : List.of();
        boolean hasCards = recipeCards != null && !recipeCards.isEmpty();
        AskToolContext.JeiDumpLevel jeiLevel = AskToolContext.jeiDumpLevel(question);
        String jeiSummary = PackKnowledge.shouldQueryJei() && attachCards
                ? JeiLookup.summarize(cardFocus, jeiLevel)
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
            appendRecipeCardsCatalog(jeiBlock, recipeCards, replyLang);
            appendSummonFact(jeiBlock, question, recipeCards, cardFocus);
            appendRequirements(jeiBlock, recipeCards, replyLang);
        }
        if (PackKnowledge.shouldQueryJei()) {
            appendJeiInfoPages(jeiBlock, cardFocus, replyLang);
            String machine = PackKnowledge.machineBriefSectionOrEmpty(cardFocus, question, replyLang);
            if (!machine.isBlank()) {
                if (!jeiBlock.isEmpty()) {
                    jeiBlock.append('\n');
                }
                jeiBlock.append(RecipeGetMarks.MACHINE_MARK).append(machine);
            }
        }
        final String jei = jeiBlock.isEmpty() ? null : jeiBlock.toString().trim();
        final String purposeTooltip = mergeExtrasPurpose(
                purposeTooltipFor(jeiTarget, mc.player), extras, mc.player);
        PackAiMod.LOGGER.info("Pack AI Ask replyLang={} jeiLevel={}", replyLang, jeiLevel);
        final AskLoopState askLoop = beginAskLoop(question, focusItem, cardFocus, jeiLevel, jeiSummary);
        askLoop.setRecipeCardLines(catalogLines(recipeCards == null ? List.of() : recipeCards, replyLang));
        final String purposeGuide = PatchouliGuideLookup.lookup(
                (jeiTarget != null && !jeiTarget.isEmpty()) ? jeiTarget : ItemStack.EMPTY,
                question);
        try {
            AskResult result = AskEngine.INSTANCE.ask(
                    question, gameDir, modIds, focusItem, extras, questOverride, jei,
                    history == null ? List.of() : history,
                    replyLang, purposeTooltip, purposeGuide, jeiFocusItemId, askLoop);
            List<RecipeCard> collected = recipeCards == null ? List.of() : recipeCards;
            String scrubbed = AskReplyScrub.stripDuplicateSectionHeaders(result.answer());
            String patched = AskCardFallback.ensureCards(scrubbed, collected);
            if (!patched.equals(result.answer())) {
                result = result.withAnswer(patched);
            }
            Boolean marker = RecipeCardsMode.resolveGateMarker(result.answer());
            List<RecipeCard> cardsOut = cardsMode.resolveAttach(
                    collected, marker, question, result.answer());
            return dedupeQuestChatWhenCardShows(withScrollMaterialInline(result, purposeTooltip, replyLang)
                    .withRecipeCards(cardsOut));
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
        return ReplyLang.resolveMcLanguageCode(mc);
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

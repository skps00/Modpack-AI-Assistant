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
import com.skps9.packai.client.guideme.GuideMeGuideLookup;
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
import com.skps9.packai.logic.EnchantHint;
import com.skps9.packai.logic.FormatRequirements;
import com.skps9.packai.logic.ItemConsumeUseFacts;
import com.skps9.packai.logic.ItemRef;
import com.skps9.packai.logic.ItemResolver;
import com.skps9.packai.logic.ItemVariantKeys;
import com.skps9.packai.logic.ModularToolScan;
import com.skps9.packai.logic.PackIndex;
import com.skps9.packai.logic.TetraMaterialItems;
import com.skps9.packai.logic.PatchouliEntryScan;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

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
        final PackIndex.MaintenanceIntent maintIntent = PackIndex.maintenanceIntent(question);
        final boolean attachCards = cardsMode.shouldCollect(question);
        List<RecipeCard> recipeCards = PackKnowledge.shouldQueryJei() && attachCards
                ? collectAskRecipeCards(cardFocus, extras, question)
                : List.of();
        recipeCards = filterRecipeCardsByIntent(recipeCards, maintIntent);
        final boolean hasCards = recipeCards != null && !recipeCards.isEmpty();
        if (attachCards) {
            String focusId = cardFocus == null || cardFocus.isEmpty()
                    ? "-"
                    : String.valueOf(BuiltInRegistries.ITEM.getKey(cardFocus.getItem()));
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
                ? JeiLookup.summarize(cardFocus, jeiLevel, maintIntent)
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
        final String jeiRaw = jeiBlock.isEmpty() ? null : jeiBlock.toString().trim();
        // Craft cards only — scroll materials go inline in answer (not FLOW strip).
        final List<RecipeCard> cardsCollected = recipeCards == null ? List.of() : List.copyOf(recipeCards);
        final List<String> catalogLineList = catalogLines(cardsCollected, replyLang);
        final String catalogText = String.join("\n", catalogLineList);
        final String capturedPurpose = purposeTooltipFor(jeiTarget, mc.player);
        // Wave 22: enchantHintText pre-injection removed; model calls enchant_lookup on demand.
        String claimHints = claimHintsText(question, capturedPurpose, jeiRaw, catalogText);
        final String jei;
        if (jeiRaw == null || jeiRaw.isBlank()) {
            jei = claimHints.isEmpty() ? null : claimHints.trim();
        } else {
            StringBuilder jb = new StringBuilder();
            if (!claimHints.isEmpty()) {
                jb.append(claimHints).append('\n');
            }
            jb.append(jeiRaw);
            jei = jb.toString().trim();
        }
        PackAiMod.LOGGER.info("Pack AI trace askJei len={} head={}",
                jei == null ? -1 : jei.length(),
                jei == null ? "NULL" : jei.substring(0, Math.min(140, jei.length())));
        final String purposeTooltip = mergeExtrasPurpose(capturedPurpose, extras, mc.player);
        final ItemStack guideStack = jeiTarget;
        final List<ChatMessage> prior = history == null ? List.of() : List.copyOf(history);
        final String askQuestion = question;
        final AskLoopState askLoop = beginAskLoop(question, focusItem, cardFocus, jeiLevel, jeiSummary);
        askLoop.setRecipeCardLines(catalogLineList);
        PackAiMod.LOGGER.info("Pack AI Ask replyLang={} jeiLevel={}", replyLang, jeiLevel);
        final String askJeiFocusItemId = jeiFocusItemId;

        CompletableFuture.supplyAsync(() -> {
                    try {
                        // Lookup on worker so awaitReady can wait without client-thread deadlock.
                        String purposeGuide = purposeGuideFor(guideStack, askQuestion);
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
                        int maintCount = 0;
                        int upgCount = 0;
                        if (cardsOut != null) {
                            for (RecipeCard c : cardsOut) {
                                if (c == null) {
                                    continue;
                                }
                                if (c.isMaintenance()) {
                                    maintCount++;
                                } else if (c.isUpgrade()) {
                                    upgCount++;
                                }
                            }
                        }
                        PackAiMod.LOGGER.info(
                                "Pack AI ask cardsOut count={} cats={} maint={} upg={}",
                                cardsOut == null ? 0 : cardsOut.size(),
                                cardCatTitles(cardsOut),
                                maintCount,
                                upgCount);
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

    /** Tooltip + furnace fuel / ItemAbilities for Ask {@code [PURPOSE]}; optional [CONTAINED]. */
    static String purposeTooltipFor(ItemStack stack, net.minecraft.client.player.LocalPlayer player) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String tip = TooltipCapture.capture(stack, player);
        tip = trimPurposeTooltip(tip);
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

    /**
     * Raw tooltip lines can carry long decorative lore / NBT / mod rows that, when echoed
     * into [PURPOSE], invite the LLM to copy-paste them back as the answer (smoke
     * 2026-09-05, maodlc:wuren). Keep the meaningful head (name / stats rows), drop the
     * decorative tail — but keep obtain/claim lines even past the 8-line cap so
     * Wave-6「据 tooltip」rules still see them. Structured behavior blocks are appended
     * separately and unaffected.
     */
    private static String trimPurposeTooltip(String tip) {
        if (tip == null || tip.isBlank()) {
            return tip;
        }
        String[] lines = tip.split("\\r?\\n", -1);
        StringBuilder sb = new StringBuilder(tip.length());
        int kept = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) {
                if (kept > 0) {
                    sb.append('\n');
                }
                continue;
            }
            String low = t.toLowerCase(Locale.ROOT);
            boolean claim = low.contains("获得") || low.contains("領取") || low.contains("领取")
                    || low.contains("兑换") || low.contains("兌換") || low.contains("解锁")
                    || low.contains("解鎖") || low.contains("取得") || low.contains("obtain")
                    || low.contains("claim") || low.contains("exchange") || low.contains("unlock")
                    || low.contains("loot");
            if (kept >= 8 && !claim) {
                break;
            }
            if (kept > 0) {
                sb.append('\n');
            }
            sb.append(line);
            kept++;
        }
        return sb.toString();
    }

    /** Deterministic obtain-claim hints: scan the texts already in context (item tooltip /
     *  JEI block / recipe-card catalog) for sentences that state how the item is obtained,
     *  and return them as an explicit low-confidence FACT so the model cites them
     *  consistently (smoke 2026-09-05: 武刃炮景礼包 claim cited 21:39, missed 21:29). */
    private static String claimHintsText(String question, String purposeTooltip, String jeiRaw,
                                         String catalogText) {
        if (question == null) {
            return "";
        }
        String q = question.toLowerCase(Locale.ROOT);
        boolean obtainish = q.contains("怎样来") || q.contains("怎麼來") || q.contains("怎样获得")
                || q.contains("怎麼獲得") || q.contains("怎么获得") || q.contains("哪里拿")
                || q.contains("哪裡拿") || q.contains("哪里") || q.contains("哪裡")
                || q.contains("获得") || q.contains("獲得") || q.contains("取得")
                || q.contains("获取") || q.contains("獲取") || q.contains("obtain")
                || q.contains("where") || q.contains("how to get") || q.contains("get it");
        if (!obtainish) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int[] cnt = {0};
        // tooltip/PURPOSE source
        appendClaimLines(sb, purposeTooltip, "tooltip", cnt);
        // JEI / card-description source
        appendClaimLines(sb, jeiRaw, "JEI", cnt);
        // recipe-card catalog descriptions (claim sentences often live in card descriptions,
        // e.g. 武刃 奥术铁砧 card "→ 武刃（右键大炮炮景和特殊领取小礼包获得...）")
        appendClaimLines(sb, catalogText, "JEI 卡描述", cnt);
        String out = sb.toString().trim();
        PackAiMod.LOGGER.info("Pack AI claimHints qLen={} obtainish={} src={}/{}/{} out={} {}",
                question == null ? -1 : question.trim().length(), obtainish,
                purposeTooltip == null ? -1 : purposeTooltip.length(),
                jeiRaw == null ? -1 : jeiRaw.length(),
                catalogText == null ? -1 : catalogText.length(),
                out.isEmpty() ? 0 : out.split("\\n", -1).length,
                out.isEmpty() ? "" : out.split("\\n")[0].length() > 200
                        ? out.split("\\n")[0].substring(0, 200) : out.split("\\n")[0]);
        return out.isEmpty() ? "" : "[TOOLTIP_HINT] 以下为低信心提示（可能并非完整或最新）：\n" + out;
    }

    private static void appendClaimLines(StringBuilder sb, String text, String source, int[] cnt) {
        if (text == null || text.isBlank()) {
            return;
        }
        Set<String> emitted = new LinkedHashSet<>();
        boolean tooltip = "tooltip".equals(source);
        String[] lines = text.split("\\r?\\n", -1);
        for (String line : lines) {
            if (cnt[0] >= 3) {
                break;
            }
            String t = line.trim();
            if (t.isEmpty() || t.length() < 6) {
                continue;
            }
            String low = t.toLowerCase(Locale.ROOT);
            // Skip meta/rule noise — markers checked on original AND lowercased.
            if (t.contains("role=") || t.contains("role\\u003d") || t.contains("\\u003d")
                    || t.contains("role\u003d")
                    || t.contains("[RECIPE_CARDS]") || t.contains("[AS_INGREDIENT]")
                    || t.contains("[JEI") || t.contains("【JEI") || t.contains("【来源")
                    || t.contains("【?源")
                    || t.contains("推荐") || t.contains("优先") || t.contains("禁止")
                    || t.contains("玩家偏好") || t.contains("切勿") || t.contains("一方法一卡")
                    || t.contains("（据 ") || t.contains("（据")
                    || t.contains("Tool actions") || t.contains("Attribute")
                    || low.contains("role=") || low.contains("role\\u003d") || low.contains("\\u003d")
                    || low.contains("role\u003d")
                    || low.contains("[recipe_cards]") || low.contains("[as_ingredient]")
                    || low.contains("[jei") || low.contains("【jei") || low.contains("【来源")
                    || low.contains("【?源")
                    || low.contains("推荐") || low.contains("优先") || low.contains("禁止")
                    || low.contains("玩家偏好") || low.contains("切勿") || low.contains("一方法一卡")
                    || low.contains("（据 ") || low.contains("（据")
                    || low.contains("tool actions") || low.contains("attribute")) {
                continue;
            }
            boolean strong = low.contains("礼包") || low.contains("禮包") || low.contains("领取")
                    || low.contains("領取") || low.contains("兑换") || low.contains("兌換")
                    || low.contains("obtain") || low.contains("claim") || low.contains("exchange")
                    || low.contains("unlock") || low.contains("loot");
            boolean weak = low.contains("获得") || low.contains("獲得") || low.contains("取得")
                    || low.contains("获取");
            boolean claim = strong || weak
                    || low.contains("解锁") || low.contains("解鎖");
            if (!claim) {
                continue;
            }
            // JEI / card text: weak-only lines need recipe/card row shape.
            if (!tooltip) {
                boolean looksRow = t.contains("→") || t.contains("机器") || t.contains("机台")
                        || t.contains("：") || t.contains(":");
                if (!looksRow && !strong) {
                    continue;
                }
            }
            String show = t.length() > 120 ? t.substring(0, 120) + "…" : t;
            String out = "- " + show + "  （据 " + source + "）";
            if (!emitted.add(out)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(out);
            cnt[0]++;
        }
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
            if (c == null || c.isTrailingOptional()) {
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
        boolean questLike = cat != null && (cat.contains("任务") || cat.contains("任務")
                || cat.toLowerCase(Locale.ROOT).contains("quest")
                || cat.toLowerCase(Locale.ROOT).contains("task"));
        String head = "role=" + role + " | " + cat;
        if (questLike && "input".equals(role)) {
            role = "quest_task";
            head = "role=quest_task | " + cat;
        }
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
        if ("quest_task".equals(role)) {
            body = body + "（任务：获得/持有目标物品后完成领奖，物品不消耗；达成方式以任务书为准）";
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
                String n = Plainify.stripMcFormat(fluid.getHoverName().getString());
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

    /**
     * Expose-layer filter before catalog/claimHints/attach: REPAIR keeps only maintenance,
     * UPGRADE only upgrade, BOTH keeps all, NONE drops trailing optional cards.
     */
    static List<RecipeCard> filterRecipeCardsByIntent(
            List<RecipeCard> cards, PackIndex.MaintenanceIntent intent
    ) {
        if (cards == null || cards.isEmpty()) {
            return cards == null ? List.of() : cards;
        }
        if (intent == PackIndex.MaintenanceIntent.BOTH) {
            return cards;
        }
        List<RecipeCard> out = new ArrayList<>(cards.size());
        for (RecipeCard c : cards) {
            if (c == null) {
                continue;
            }
            if (c.isTrailingOptional()) {
                if (intent == PackIndex.MaintenanceIntent.NONE) {
                    continue;
                }
                if (intent == PackIndex.MaintenanceIntent.REPAIR && !c.isMaintenance()) {
                    continue;
                }
                if (intent == PackIndex.MaintenanceIntent.UPGRADE && !c.isUpgrade()) {
                    continue;
                }
            }
            out.add(c);
        }
        return out;
    }

    static List<RecipeCard> collectAskRecipeCards(ItemStack focus, List<ItemRef> extras, String question) {
        int perOut = PackAiConfig.recipeCardsPerItem();
        int perUse = PackAiConfig.recipeCardsPerItemUse();
        List<RecipeCard> out = new ArrayList<>();
        List<RecipeCard> maint = new ArrayList<>();
        LinkedHashSet<String> done = new LinkedHashSet<>();
        int items = 0;
        if (focus != null && !focus.isEmpty()) {
            items++;
            String fkey = selectionKey(fromStack(focus));
            if (!fkey.isEmpty()) {
                done.add(fkey);
            }
            JeiRecipeCards.ItemParts parts = JeiRecipeCards.forItemParts(focus, perOut, perUse);
            out.addAll(parts.normal());
            maint.addAll(parts.maintenance());
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
                JeiRecipeCards.ItemParts parts = JeiRecipeCards.forItemParts(stack, perOut, perUse);
                out.addAll(parts.normal());
                maint.addAll(parts.maintenance());
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
            out = new ArrayList<>(out.subList(0, budget));
        }
        out.addAll(maint);
        return List.copyOf(out);
    }

    /** Registry id from card/JEI focus stack (null when empty). */
    static String cardFocusItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
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

    /** Patchouli + GuideME page text → bare body for {@code [GUIDE]} (capped). */
    static String purposeGuideFor(ItemStack stack) {
        return purposeGuideFor(stack, null);
    }

    static String purposeGuideFor(ItemStack stack, String question) {
        List<String> bodies = new ArrayList<>();
        try {
            String patchouli = PatchouliGuideLookup.lookup(
                    stack == null ? ItemStack.EMPTY : stack, question);
            if (patchouli != null && !patchouli.isBlank()) {
                bodies.add(patchouli.trim());
            }
        } catch (Throwable ignored) {
            // soft-fail
        }
        if (stack != null && !stack.isEmpty()) {
            try {
                String guideme = GuideMeGuideLookup.lookup(stack);
                if (guideme != null && !guideme.isBlank()) {
                    bodies.add(guideme.trim());
                }
            } catch (Throwable ignored) {
                // soft-fail
            }
        }
        return PatchouliEntryScan.joinCapped(
                bodies, PatchouliEntryScan.DEFAULT_MAX_ENTRIES, PatchouliEntryScan.DEFAULT_MAX_CHARS);
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
        PackIndex.MaintenanceIntent maintIntent = PackIndex.maintenanceIntent(question);
        boolean attachCards = cardsMode.shouldCollect(question);
        List<RecipeCard> recipeCards = PackKnowledge.shouldQueryJei() && attachCards
                ? collectAskRecipeCards(cardFocus, extras, question)
                : List.of();
        recipeCards = filterRecipeCardsByIntent(recipeCards, maintIntent);
        boolean hasCards = recipeCards != null && !recipeCards.isEmpty();
        AskToolContext.JeiDumpLevel jeiLevel = AskToolContext.jeiDumpLevel(question);
        String jeiSummary = PackKnowledge.shouldQueryJei() && attachCards
                ? JeiLookup.summarize(cardFocus, jeiLevel, maintIntent)
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
        final String jeiRaw = jeiBlock.isEmpty() ? null : jeiBlock.toString().trim();
        final List<String> catalogLineList = catalogLines(recipeCards == null ? List.of() : recipeCards, replyLang);
        final String catalogText = String.join("\n", catalogLineList);
        final String capturedPurpose = purposeTooltipFor(jeiTarget, mc.player);
        // Wave 22: enchantHintText pre-injection removed; model calls enchant_lookup on demand.
        String claimHints = claimHintsText(question, capturedPurpose, jeiRaw, catalogText);
        final String jei;
        if (jeiRaw == null || jeiRaw.isBlank()) {
            jei = claimHints.isEmpty() ? null : claimHints.trim();
        } else {
            StringBuilder jb = new StringBuilder();
            if (!claimHints.isEmpty()) {
                jb.append(claimHints).append('\n');
            }
            jb.append(jeiRaw);
            jei = jb.toString().trim();
        }
        final String purposeTooltip = mergeExtrasPurpose(capturedPurpose, extras, mc.player);
        PackAiMod.LOGGER.info("Pack AI Ask replyLang={} jeiLevel={}", replyLang, jeiLevel);
        final AskLoopState askLoop = beginAskLoop(question, focusItem, cardFocus, jeiLevel, jeiSummary);
        askLoop.setRecipeCardLines(catalogLineList);
        final String purposeGuide = purposeGuideFor(jeiTarget, question);
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
            var key = BuiltInRegistries.ITEM.getKey(jeiTarget.getItem());
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
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
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

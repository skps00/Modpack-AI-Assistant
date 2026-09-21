package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.chat.ChatMessage;
import com.skps9.packai.config.PackAiConfig;

import net.minecraft.world.item.ItemStack;

/**
 * In-mod ask engine — no external Python Bridge.
 */
public final class AskEngine {
    public static final AskEngine INSTANCE = new AskEngine();

    private final ConcurrentHashMap<String, PackIndex> indexes = new ConcurrentHashMap<>();
    private final LlmClient llm = new LlmClient();

    private static final java.util.concurrent.atomic.AtomicBoolean ASK_TOOLS_READY =
            new java.util.concurrent.atomic.AtomicBoolean();

    private static void registerAskTools() {
        if (!ASK_TOOLS_READY.compareAndSet(false, true)) {
            return;
        }
        AskToolLoop.INSTANCE.register(new JeiLookupAskTool());
        AskToolLoop.INSTANCE.register(new AcquireAskTool());
        AskToolLoop.INSTANCE.register(new GuideFetchAskTool());
        AskToolLoop.INSTANCE.register(new QuestFetchAskTool());
        AskToolLoop.INSTANCE.register(new ConsumeUseAskTool());
        AskToolLoop.INSTANCE.register(new ItemSearchAskTool());
        AskToolLoop.INSTANCE.register(new RenderRecipeCardsAskTool());
        AskToolLoop.INSTANCE.register(new PurposeLookupAskTool());
        AskToolLoop.INSTANCE.register(new EnchantLookupAskTool());
        AskToolLoop.INSTANCE.register(new RepairLookupAskTool());
        AskToolLoop.INSTANCE.register(new ToolBuildAskTool());
        AskToolLoop.INSTANCE.register(new TetraUseAskTool());
        AskToolLoop.INSTANCE.register(new WorldgenLookupAskTool());
        AskToolLoop.INSTANCE.register(new KnowledgeLookupAskTool());
    }

    private AskEngine() {}

    public void warmup(Path gameDir, List<String> modIds) {
        String key = cacheKey(gameDir, modIds);
        PackIndex idx = indexes.computeIfAbsent(key, k -> new PackIndex());
        PackAuthorAgents.reload(gameDir);
        synchronized (idx) {
            idx.build(gameDir, ModScanners.active(modIds));
        }
        if (PackAiConfig.kubejsMechanicScan()) {
            KubeJsApiBridge.ensureStart(gameDir);
            KubeJsMechanicScan.ensureStart(
                    gameDir,
                    PackAiConfig.mechanicScanMaxFiles(),
                    PackAiConfig.mechanicScanMaxBytes(),
                    PackAiConfig.mechanicScanMaxMs());
        }
        if (PackAiConfig.questMechanicFacts()) {
            QuestMechanicFacts.ensureStart(gameDir, PackAiConfig.questScanMaxFiles());
        }
        // Jar light scan deferred to first Ask (see ask() ensure) — not every warmup.
    }

    /** Drop cached PackIndex instances (e.g. after toggling show-hidden quests). */
    public void invalidateIndexes() {
        indexes.clear();
    }

    public AskResult ask(
            String question,
            Path gameDir,
            List<String> modIds,
            ItemRef heldItem,
            List<ItemRef> hotbarItems,
            boolean questOverrideFlag
    ) {
        return ask(question, gameDir, modIds, heldItem, hotbarItems, questOverrideFlag, null);
    }

    public AskResult ask(
            String question,
            Path gameDir,
            List<String> modIds,
            ItemRef heldItem,
            List<ItemRef> hotbarItems,
            boolean questOverrideFlag,
            String jeiSummary
    ) {
        return ask(question, gameDir, modIds, heldItem, hotbarItems, questOverrideFlag, jeiSummary, List.of());
    }

    public AskResult ask(
            String question,
            Path gameDir,
            List<String> modIds,
            ItemRef heldItem,
            List<ItemRef> hotbarItems,
            boolean questOverrideFlag,
            String jeiSummary,
            List<ChatMessage> history
    ) {
        return ask(question, gameDir, modIds, heldItem, hotbarItems, questOverrideFlag, jeiSummary, history, null);
    }

    public AskResult ask(
            String question,
            Path gameDir,
            List<String> modIds,
            ItemRef heldItem,
            List<ItemRef> hotbarItems,
            boolean questOverrideFlag,
            String jeiSummary,
            List<ChatMessage> history,
            String replyLang
    ) {
        return ask(question, gameDir, modIds, heldItem, hotbarItems, questOverrideFlag,
                jeiSummary, history, replyLang, null);
    }

    public AskResult ask(
            String question,
            Path gameDir,
            List<String> modIds,
            ItemRef heldItem,
            List<ItemRef> hotbarItems,
            boolean questOverrideFlag,
            String jeiSummary,
            List<ChatMessage> history,
            String replyLang,
            String purposeTooltip
    ) {
        return ask(question, gameDir, modIds, heldItem, hotbarItems, questOverrideFlag,
                jeiSummary, history, replyLang, purposeTooltip, null);
    }

    public AskResult ask(
            String question,
            Path gameDir,
            List<String> modIds,
            ItemRef heldItem,
            List<ItemRef> hotbarItems,
            boolean questOverrideFlag,
            String jeiSummary,
            List<ChatMessage> history,
            String replyLang,
            String purposeTooltip,
            String purposeGuide
    ) {
        return ask(question, gameDir, modIds, heldItem, hotbarItems, questOverrideFlag,
                jeiSummary, history, replyLang, purposeTooltip, purposeGuide, null);
    }

    public AskResult ask(
            String question,
            Path gameDir,
            List<String> modIds,
            ItemRef heldItem,
            List<ItemRef> hotbarItems,
            boolean questOverrideFlag,
            String jeiSummary,
            List<ChatMessage> history,
            String replyLang,
            String purposeTooltip,
            String purposeGuide,
            AskLoopState loop
    ) {
        return ask(question, gameDir, modIds, heldItem, hotbarItems, questOverrideFlag,
                jeiSummary, history, replyLang, purposeTooltip, purposeGuide, null, loop);
    }

    public AskResult ask(
            String question,
            Path gameDir,
            List<String> modIds,
            ItemRef heldItem,
            List<ItemRef> hotbarItems,
            boolean questOverrideFlag,
            String jeiSummary,
            List<ChatMessage> history,
            String replyLang,
            String purposeTooltip,
            String purposeGuide,
            String jeiFocusItemId,
            AskLoopState loop
    ) {
        return ask(question, gameDir, modIds, heldItem, hotbarItems, questOverrideFlag,
                jeiSummary, history, replyLang, purposeTooltip, purposeGuide, jeiFocusItemId, loop, null);
    }

    public AskResult ask(
            String question,
            Path gameDir,
            List<String> modIds,
            ItemRef heldItem,
            List<ItemRef> hotbarItems,
            boolean questOverrideFlag,
            String jeiSummary,
            List<ChatMessage> history,
            String replyLang,
            String purposeTooltip,
            String purposeGuide,
            String jeiFocusItemId,
            AskLoopState loop,
            String toolBuild
    ) {
        llm.resetUsageAccumulator();
        ItemRef held = heldItem == null ? ItemRef.NONE : heldItem;
        List<ItemRef> hotbarRefs = hotbarItems == null ? List.of() : hotbarItems;
        List<ChatMessage> prior = history == null ? List.of() : history;
        String heldItemId = held.isPresent() ? held.id() : null;
        List<String> variantTokens = ItemVariantKeys.disambiguators(held);
        List<String> hotbarIds = new ArrayList<>();
        List<String> hintTokens = new ArrayList<>(held.hintTokens());
        hintTokens.addAll(ItemVariantKeys.hintExtras(held));
        hintTokens.addAll(AskNameResolve.relatedHintIds(heldItemId));
        for (ItemRef ref : hotbarRefs) {
            if (ref.isPresent()) {
                hotbarIds.add(ref.id());
                hintTokens.addAll(ref.hintTokens());
                hintTokens.addAll(ItemVariantKeys.hintExtras(ref));
            }
        }
        // Quest scoring: opt-in hotbar (default off). Retrieve/LLM may still see hotbarIds.
        List<String> questExtras = PackAiConfig.questMatchHotbar() ? hotbarIds : List.of();
        boolean attachQuests = PackAiConfig.attachRelatedQuests();

        List<String> mods = modIds == null ? List.of() : modIds;
        List<String> scanners = ModScanners.active(mods);
        List<String> focus = ModScanners.focusMods(mods, heldItemId, question, hotbarIds);
        String key = cacheKey(gameDir, mods);
        PackIndex idx = indexes.computeIfAbsent(key, k -> new PackIndex());
        PackAuthorAgents.reload(gameDir);

        synchronized (idx) {
            if (idx.paths().isEmpty()) {
                idx.build(gameDir, scanners);
            }
            // Prevent cross-Ask spoiler edges (quest_submit titles) from surviving in graphFacts.
            idx.beginAskSession();
            if (PackAiConfig.scanModJars() && !JarLightIndex.INSTANCE.isReady()) {
                JarLightIndex.INSTANCE.ensure(gameDir);
            }

            String lang = replyLang == null || replyLang.isBlank() ? "zh_tw" : replyLang.trim();
            String mode = PackAiConfig.resolvedMode();
            boolean offline = "offline".equals(mode);
            boolean override = QuestGuide.isOverride(question, questOverrideFlag);

            List<QuestGuide.Hit> allQuests = List.of();
            QuestGuide.MatchResult questMatch = new QuestGuide.MatchResult(List.of(), 0);
            if (!override && attachQuests) {
                allQuests = QuestGuide.index(gameDir, scanners, lang);
                questMatch = offline
                        ? QuestGuide.matchForOfflineResult(allQuests, question, heldItemId, questExtras, variantTokens)
                        : QuestGuide.matchResult(allQuests, question, heldItemId, questExtras, variantTokens);
            }
            List<QuestGuide.Hit> questHits = questMatch.hits();

            PackIndex.RetrieveResult retrieved = idx.retrieve(question, heldItemId, focus, hotbarIds, hintTokens);
            boolean qConflict = QuestGuide.conflict(questHits, retrieved.removedItems());
            List<String> jarFacts = JarLightIndex.INSTANCE.factsForAsk(heldItemId, lang);

            // Offline only: quest hits short-circuit (no LLM). Online always calls LLM when possible.
            if (offline && !questHits.isEmpty() && !override) {
                String localPlain = null;
                if (qConflict) {
                    localPlain = Plainify.plainify(retrieved.snippets(), retrieved.sources());
                }
                String guide = QuestGuide.formatGuide(
                        questHits, qConflict, localPlain, questMatch.totalMatched(), true, lang);
                return AskResult.of(guide, questHits);
            }

            boolean packMayHaveOtherEdits = idx.touchesFocus(focus, heldItemId)
                    || !retrieved.snippets().isEmpty();
            PackIndex.AcquireFacts acquireBundle = idx.acquireFactsDetailed(heldItemId, lang, variantTokens);
            List<String> acquire = acquireBundle.lines();
            JeiInfoFacts.Split jeiInfo = JeiInfoFacts.splitFromDump(jeiSummary);
            acquire = JeiInfoFacts.mergeUnique(acquire, jeiInfo.acquire());
            Set<String> rankedAcquireEdges = acquireBundle.rankedSkipEdges();
            List<String> jarLines = jarFacts.isEmpty() ? List.of() : jarFacts;
            boolean heldLocallyTouched = isHeldLocallyTouched(heldItemId, retrieved, acquire);
            // Partial packs: only force local_only when THIS item/question looks pack-modified.
            String policy;
            if (heldLocallyTouched || qConflict) {
                policy = "local_only";
            } else if (packMayHaveOtherEdits) {
                policy = "mixed"; // other areas may be modded; this topic looks stock
            } else {
                policy = "online_ok";
            }

            String plain = Plainify.plainify(retrieved.snippets(), retrieved.sources());
            String machineSection = RecipeGetMarks.extractMachine(jeiSummary);
            String jeiPayload = RecipeGetMarks.stripMachine(jeiSummary);
            boolean emiPreview = RecipeGetMarks.isEmiPreview(jeiPayload);
            boolean noRecipeUi = RecipeGetMarks.isNoRecipeUi(jeiPayload);
            String recipeGetClean = RecipeGetMarks.strip(jeiPayload);
            boolean hasJei = recipeGetClean != null && !recipeGetClean.isBlank() && !emiPreview && !noRecipeUi
                    && !AskJeiHints.isJeiAbsenceSummary(recipeGetClean);
            boolean hasRecipeGet = recipeGetClean != null && !recipeGetClean.isBlank();
            boolean hasMachine = machineSection != null && !machineSection.isBlank();
            boolean hasSummonFact = hasRecipeGet && recipeGetClean.contains(SummonRecipeLookup.PREFIX);

            boolean purpose = PackIndex.isPurposeQuestion(question)
                    || PackIndex.isCodeOrBehaviorQuestion(question);
            boolean craftQ = PackIndex.isCraftOrientedQuestion(question);
            boolean obtainQ = PackIndex.isAcquireOrientedQuestion(question);
            if (loop == null) {
                loop = AskLoopState.start(
                        question,
                        heldItemId == null ? "" : heldItemId,
                        variantTokens,
                        System.currentTimeMillis() + AskToolLoop.WALL_MS);
            }
            loop.setQuestion(question);
            loop.setLang(lang);
            loop.setGameDir(gameDir);
            loop.setScanners(scanners);
            loop.setDumpLevel(AskToolContext.jeiDumpLevel(question).name());
            if (loop.variantKeys().isEmpty() && variantTokens != null && !variantTokens.isEmpty()) {
                loop.setVariantKeys(variantTokens);
            }
            loop.setIntent(purpose ? AskLoopState.Intent.PURPOSE
                    : craftQ ? AskLoopState.Intent.CRAFT
                    : obtainQ ? AskLoopState.Intent.OBTAIN
                    : AskLoopState.Intent.PURPOSE);
            final AskLoopState traceLoop = loop;
            AskTrace.event("check.intent", o -> {
                o.addProperty("loopIntent", traceLoop.intent() == null ? "" : traceLoop.intent().name());
                o.addProperty("purpose", purpose);
                o.addProperty("craftQ", craftQ);
                o.addProperty("obtainQ", obtainQ);
            });
            String acqShot = acquire.isEmpty() ? "" : String.join("\n", AskToolContext.clipAcquireLines(acquire, question));
            loop.noteShot0("jei_lookup", loop.dumpLevel(), loop.variantKeys(), hasJei ? recipeGetClean : "");
            loop.noteShot0("acquire", "FULL", loop.variantKeys(), acqShot);
            loop.noteShot0("guide_fetch", "", List.of(), purposeGuide == null ? "" : purposeGuide);
            // Plan F: STANDARD blank-frame → do not pin acquire-miss; MODIFIED → keep pin; UNKNOWN fail-open.
            final ModularFrameStandard.Match frameMatch = ModularFrameStandard.classifyDetailedInstalled(toolBuild);
            final ModularFrameStandard.Kind frameKind = frameMatch.kind();
            // Plan F fix2: diagnose classify path (log-only; no behavior change).
            PackAiMod.LOGGER.info(
                    "Pack AI frame-classify: kind={} recipeIndex={} parts={}",
                    frameKind,
                    frameMatch.recipeIndex(),
                    ModularFrameStandard.partsFromToolBuildText(toolBuild));
            boolean missPin = HonestMiss.shouldPinAcquireMiss(
                    acquire, hasObtainRecipes(hasRecipeGet, jeiSummary), question, heldItemId)
                    && !JeiInfoFacts.hasAny(jeiSummary)
                    && jeiInfo.isEmpty();
            if (frameKind == ModularFrameStandard.Kind.STANDARD) {
                missPin = false;
            }
            loop.setMissPin(missPin);
            if (!offline) {
                registerAskTools();
                if (loop.intent() != AskLoopState.Intent.PURPOSE) {
                AskToolLoop.bindEnv(bindAskToolEnv(loop, held.sample(), idx, gameDir, scanners, held));
                try {
                    AskToolLoop.INSTANCE.drainBeforeFirstLlm(loop);
                } finally {
                    AskToolLoop.clearEnv(loop);
                }
                if (loop.skipLlm()
                        && !hasJei
                        && !hasMachine
                        && jeiInfo.isEmpty()
                        && !(retrieved.highConfidence() && retrieved.snippets() != null && !retrieved.snippets().isEmpty())) {
                    String missBody;
                    if (SummonRecipeLookup.isSummonQuestion(question)) {
                        missBody = String.join("\n", HonestMiss.summonMissFactsPlayer(lang, List.of()));
                    } else {
                        List<String> acqMiss = HonestMiss.acquireMissFactsPlayer(heldItemId, lang);
                        missBody = acqMiss.isEmpty()
                                ? ReplyLang.askMissAcquirePlayer(lang)
                                : String.join("\n", acqMiss);
                    }
                    if (missBody.isBlank()) {
                        missBody = ReplyLang.askMissAcquirePlayer(lang).trim();
                    }
                    if (missBody.isBlank()) {
                        missBody = ReplyLang.friendlyOffline(lang, question);
                    }
                    return AskResult.text(missBody);
                }
                if (!AskLoopState.isEmptyOrMiss(loop.jeiText())) {
                    recipeGetClean = loop.jeiText();
                    hasJei = true;
                    hasRecipeGet = true;
                } else if (loop.intent() == AskLoopState.Intent.CRAFT) {
                    recipeGetClean = loop.jeiText();
                    hasJei = false;
                    hasRecipeGet = recipeGetClean != null && !recipeGetClean.isBlank();
                }
                if (!AskLoopState.isEmptyOrMiss(loop.acquireText())) {
                    acquire = List.of(loop.acquireText().split("\n"));
                }
                PackAiMod.LOGGER.info("Pack AI trace engineBody jeiLen={} bodyLen={}",
                        jeiSummary == null ? -1 : jeiSummary.length(),
                        (jeiSummary == null ? "" : jeiSummary).length() + loop.jeiText().length());
                jeiInfo = JeiInfoFacts.splitFromDump(
                        (jeiSummary == null ? "" : jeiSummary) + "\n" + loop.jeiText());
                acquire = JeiInfoFacts.mergeUnique(acquire, jeiInfo.acquire());
                if (!AskLoopState.isEmptyOrMiss(loop.guideText())) {
                    purposeGuide = loop.guideText();
                }
                }
            }

            if (plain != null && retrieved.highConfidence() && questHits.isEmpty() && !hasRecipeGet && !hasMachine) {
                // Local script match only when JEI has nothing better.
                return withSideQuests(plain, allQuests, question, heldItemId, questExtras, variantTokens, offline, override, replyLang);
            }

            String llmAnswer = null;
            TokenUsage llmUsage = TokenUsage.NONE;
            List<String> replySources = List.of();
            List<String> factMarkerSources = List.of();
            List<String> playerFacts = List.of();
            if (!offline) {
                List<String> facts = new ArrayList<>();
                int factCap = PackAiConfig.maxFacts();
                String prefer = PackAiConfig.preferObtain();
                // JEI craft or non-quest local acquire (loot/interact/…) → quest body is optional.
                boolean demoteQuestNarrative = demoteQuestNarrative(
                        hasNonQuestObtainPath(hasRecipeGet, jeiSummary, acquire, lang), prefer, override);
                Set<String> cardCats = recipeCardCategoryTitlesFromJei(jeiSummary);
                List<String> questFactLines = new ArrayList<>();
                for (QuestGuide.Hit h : questHits) {
                    // Soft matchResult keeps sibling titles for sidebar buttons; LLM facts stay strict.
                    if (!variantTokens.isEmpty()
                            && !QuestGuide.mentionsFocusItem(h, heldItemId, variantTokens)) {
                        continue;
                    }
                    String title = QuestGuide.displayTitle(h);
                    if (demoteQuestNarrative) {
                        // Recipe card already titled with this quest — skip 「另有相关任务」fact.
                        if (QuestGuide.titleCoveredByCardCategories(title, cardCats)) {
                            continue;
                        }
                        questFactLines.add(ReplyLang.questOptionalRewardNote(lang, title));
                    } else {
                        String desc = QuestGuide.refinePlayerText(h.description() == null ? "" : h.description());
                        questFactLines.add(ReplyLang.questFactLine(lang, title, desc));
                    }
                }
                if (!AskLoopState.isEmptyOrMiss(loop.questText()) && questFactLines.isEmpty()) {
                    questFactLines.add(loop.questText());
                }
                List<String> acquireLines;
                if (!acquire.isEmpty()) {
                    // Plan B: purpose/default → top ranked edges only; 配方/取得 → full budget.
                    List<String> clippedAcquire = AskToolContext.clipAcquireLines(acquire, question);
                    acquireLines = clippedAcquire.isEmpty()
                            ? List.of()
                            : List.of(String.join("\n", clippedAcquire));
                } else if (loop.missPin()
                        && frameKind != ModularFrameStandard.Kind.STANDARD
                        && HonestMiss.shouldPinAcquireMiss(
                                acquire, hasObtainRecipes(hasRecipeGet, jeiSummary, loop), question, heldItemId)) {
                    acquireLines = List.of(String.join("\n", HonestMiss.acquireMissFacts(heldItemId, lang)));
                } else {
                    acquireLines = List.of();
                }
                String questStatusFact = AskJeiHints.questStatusFactBlock(acquire, lang);
                List<String> questStatusLines = questStatusFact.isBlank()
                        ? List.of()
                        : List.of(questStatusFact);
                List<String> jarFactLines = jarLines.isEmpty() ? List.of() : List.of(String.join("\n", jarLines));
                List<String> purposeLines = new ArrayList<>();
                List<String> graphLines = new ArrayList<>();
                boolean fullAcquire = AskToolContext.wantsFullAcquire(question);
                Map<String, Set<String>> recipeNeeds = idx.recipeNeedsIndex();
                for (String gf : retrieved.graphFacts()) {
                    if (AskPurposeContext.isPurposeGraphFact(gf)) {
                        purposeLines.add(formatInteractOrAcquireFact(gf, lang));
                        continue;
                    }
                    if (gf.contains("-[drops]->")
                            || gf.contains("-[loot]->") || gf.contains("-[fish]->") || gf.contains("-[trade]->")
                            || gf.contains("-[reward_stack]->") || gf.contains("-[reward_loot]->")
                            || gf.contains("-[removed]->")) {
                        // Plan B: skip loot encyclopedia overflow unless craft/acquire ask.
                        if (!fullAcquire) {
                            continue;
                        }
                        // Focus fish/loot/trade/removed only when that raw edge made the ~12 ranked
                        // acquire list — overflow still humanizes into graphLines.
                        if (coveredByRankedAcquire(gf, rankedAcquireEdges)) {
                            continue;
                        }
                        graphLines.add(formatInteractOrAcquireFact(gf, lang));
                        continue;
                    }
                    if (gf.contains("-[recipe_needs]->")) {
                        int sep = gf.indexOf(" -[recipe_needs]-> item:");
                        if (sep > 5 && gf.startsWith("item:")) {
                            String outId = gf.substring(5, sep);
                            String needId = gf.substring(sep + " -[recipe_needs]-> item:".length());
                            if (PackIndex.isCompactCycle(outId, needId, recipeNeeds)) {
                                continue;
                            }
                        }
                        graphLines.add(Plainify.humanizeText(gf.replace("-[", " → ").replace("]->", " ")));
                    }
                }
                for (String useLine : jeiInfo.use()) {
                    if (useLine != null && !useLine.isBlank() && !purposeLines.contains(useLine)) {
                        purposeLines.add(useLine);
                    }
                }
                // Item-linked quest → how-to-use; strict when variant tokens present (no soft fallback).
                // Skip when demoted: reward-quest body (e.g. unlock machines) is not focus how-to-use.
                List<QuestGuide.Hit> purposeQuests = new ArrayList<>();
                if (!demoteQuestNarrative) {
                    for (QuestGuide.Hit h : questHits) {
                        if (QuestGuide.mentionsFocusItem(h, heldItemId, variantTokens)) {
                            purposeQuests.add(h);
                        }
                    }
                }
                LinkedHashSet<String> purposeEmbeddedQuestLines = new LinkedHashSet<>();
                for (QuestGuide.Hit h : purposeQuests) {
                    String qDesc = QuestGuide.refinePlayerText(
                            h.description() == null ? "" : h.description());
                    if (qDesc.isBlank()) {
                        continue;
                    }
                    String line = ReplyLang.questFactLine(lang, QuestGuide.displayTitle(h), qDesc);
                    purposeLines.add(line);
                    purposeEmbeddedQuestLines.add(line);
                }
                if (!purposeEmbeddedQuestLines.isEmpty()) {
                    questFactLines.removeIf(purposeEmbeddedQuestLines::contains);
                }
                // Guide vs quest: overlap → drop guide side, keep quest
                String questBlobForGuide = "";
                if (!purposeQuests.isEmpty() || (questHits != null && !questHits.isEmpty())) {
                    StringBuilder qb = new StringBuilder();
                    for (QuestGuide.Hit h : purposeQuests) {
                        qb.append(QuestGuide.displayTitle(h)).append('\n')
                                .append(h.description() == null ? "" : h.description()).append('\n');
                    }
                    for (QuestGuide.Hit h : questHits) {
                        qb.append(QuestGuide.displayTitle(h)).append('\n')
                                .append(h.description() == null ? "" : h.description()).append('\n');
                    }
                    questBlobForGuide = qb.toString();
                }
                String guideForPurpose = GuidebookPins.dedupeAgainstQuest(purposeGuide, questBlobForGuide);
                String purposeBlock = AskPurposeContext.buildPurposeBlock(
                        purposeTooltip, purposeLines, guideForPurpose);
                if (!AskLoopState.isEmptyOrMiss(loop.consumeText())
                        && (purposeBlock == null || !purposeBlock.contains(loop.consumeText()))) {
                    purposeBlock = purposeBlock == null || purposeBlock.isBlank()
                            ? loop.consumeText()
                            : purposeBlock + "\n" + loop.consumeText();
                }
                List<String> purposeFactLines = purposeBlock.isBlank()
                        ? List.of()
                        : List.of(ReplyLang.sectionHowToUse(lang) + "\n" + purposeBlock);
                // Purpose questions: peel JEI-U out so PURPOSE/GUIDE/CONSUME_USE precedes as-ingredient.
                boolean machineAsk = PackIndex.isMachineQuestion(question);
                AskToolContext.JeiDumpLevel jeiLevel = AskToolContext.jeiDumpLevel(question);
                List<String> asIngredientLines = List.of();
                List<String> jeiLines;
                if (hasRecipeGet) {
                    String getBody = recipeGetClean;
                    if (purpose) {
                        String[] parts = AskPurposeContext.splitGetAndAsIngredient(
                                recipeGetClean, ReplyLang.jeiSectionCatalyst(lang));
                        getBody = parts[0];
                        if (parts[1] != null && !parts[1].isBlank()) {
                            // Plan B: as-ingredient never full U encyclopedia.
                            String uses = AskToolContext.clipChars(parts[1], AskToolContext.MAX_JEI_USES_CHARS);
                            if (!uses.isBlank()) {
                                asIngredientLines = List.of(uses);
                            }
                        }
                    }
                    // Cap OUTPUT body by intent (slim purpose vs craft/acquire).
                    if (!getBody.isBlank()) {
                        getBody = AskToolContext.clipChars(getBody, jeiLevel.outputBudget());
                    }
                    // Purpose asks: leftover JEI header / INPUT-only cards ≠ obtain FACT.
                    boolean obtainBody = !purpose || AskPurposeContext.hasObtainRecipeBody(getBody);
                    if (obtainBody && !getBody.isBlank() && !variantTokens.isEmpty()) {
                        getBody = ReplyLang.jeiVariantCaution(lang) + "\n" + getBody;
                    }
                    jeiLines = (obtainBody && !getBody.isBlank())
                            ? List.of(ReplyLang.sectionHowToGet(lang) + "\n" + getBody)
                            : List.of();
                } else {
                    jeiLines = List.of();
                }
                jeiLines = withToolBuildHowToGet(jeiLines, toolBuild, lang);
                List<String> machineLines = hasMachine ? List.of(machineSection) : List.of();

                // Order blocks by player's preferred obtain pathway.
                // Purpose questions: askPurposeOrder (purpose_first default | ingredient_first).
                List<List<String>> blocks = new ArrayList<>();
                // When Machine brief exists, keep it early (before JEI dump) for LLM I/O context;
                // player-visible section is still force-appended post-LLM (Markdown ban strips ##).
                // Ease-first: local acquire (loot/fish/…) before JEI — Quests-only JEI must not
                // bury chestloot as "其二" when pack index already ranked loot above quest.
                if (purpose) {
                    boolean ingredientFirst = "ingredient_first".equals(PackAiConfig.askPurposeOrder());
                    if (ingredientFirst) {
                        // Older style: as-ingredient / get may lead before how-to-use.
                        blocks.add(asIngredientLines);
                        blocks.add(acquireLines);
                        blocks.add(jeiLines);
                        blocks.add(purposeFactLines);
                        blocks.add(machineLines);
                    } else {
                        // purpose_first: PURPOSE/GUIDE/CONSUME_USE → AS_INGREDIENT → obtain.
                        blocks.add(purposeFactLines);
                        blocks.add(asIngredientLines);
                        blocks.add(machineLines);
                        blocks.add(acquireLines);
                        blocks.add(jeiLines);
                    }
                    blocks.add(questStatusLines);
                    blocks.add(jarFactLines);
                    blocks.add(graphLines);
                    blocks.add(questFactLines); // quest last
                } else if (machineAsk || hasMachine) {
                    blocks.add(purposeFactLines);
                    blocks.add(machineLines);
                    blocks.add(acquireLines);
                    blocks.add(jeiLines);
                    blocks.add(questStatusLines);
                    blocks.add(jarFactLines);
                    blocks.add(graphLines);
                    blocks.add(questFactLines);
                } else {
                switch (prefer) {
                    case "quest" -> {
                        blocks.add(questStatusLines);
                        blocks.add(questFactLines);
                        blocks.add(purposeFactLines);
                        blocks.add(jeiLines);
                        blocks.add(machineLines);
                        blocks.add(acquireLines);
                        blocks.add(jarFactLines);
                        blocks.add(graphLines);
                    }
                    case "loot" -> {
                        blocks.add(acquireLines);
                        blocks.add(questStatusLines);
                        blocks.add(jarFactLines);
                        blocks.add(purposeFactLines);
                        blocks.add(machineLines);
                        blocks.add(graphLines);
                        blocks.add(jeiLines);
                        blocks.add(questFactLines);
                    }
                    case "balanced" -> {
                        blocks.add(purposeFactLines);
                        blocks.add(acquireLines);
                        blocks.add(jeiLines);
                        blocks.add(machineLines);
                        blocks.add(questStatusLines);
                        blocks.add(jarFactLines);
                        blocks.add(graphLines);
                        blocks.add(questFactLines);
                    }
                    default -> { // craft
                        blocks.add(purposeFactLines);
                        blocks.add(acquireLines);
                        blocks.add(jeiLines);
                        blocks.add(machineLines);
                        blocks.add(questStatusLines);
                        blocks.add(jarFactLines);
                        blocks.add(graphLines);
                        blocks.add(questFactLines); // quest last
                    }
                }
                }
                for (List<String> block : blocks) {
                    appendCapped(facts, block, factCap);
                }
                if (WorldgenFacts.looksLikeQuery(question)) {
                    appendCapped(facts, WorldgenIndex.lookup(question, gameDir, lang), factCap);
                }

                boolean allowWeb = PackAiConfig.webSearchEnabled();
                boolean webUsed = false;
                boolean skipWebForPurpose = purpose && (hasJei || (guideForPurpose != null && !guideForPurpose.isBlank()));
                boolean skipWebForSummon = SummonRecipeLookup.isSummonQuestion(question)
                        || HonestMiss.shouldPinSummonMiss(hasJei, hasSummonFact, question);
                if (allowWeb && !skipWebForPurpose && !skipWebForSummon) {
                    List<WebSearch.Hit> webHits = WebSearch.search(question, focus, held);
                    // local_only still may search; LLM rules say local wins on conflict.
                    String webBlock = WebSearch.formatForLlm(webHits, policy, lang);
                    if (!webBlock.isBlank() && facts.size() < factCap) {
                        facts.add(webBlock);
                        webUsed = true;
                    }
                }
                boolean localScripts = !retrieved.sources().isEmpty()
                        || (retrieved.graphFacts() != null && !retrieved.graphFacts().isEmpty());
                boolean acquireUsed = !acquire.isEmpty();
                boolean jarUsed = !jarLines.isEmpty();
                boolean purposeUsed = !purposeBlock.isBlank();
                boolean guideUsed = guideForPurpose != null && !guideForPurpose.isBlank();
                replySources = ReplySources.build(
                        hasJei,
                        emiPreview,
                        purposeUsed,
                        guideUsed,
                        !questHits.isEmpty(),
                        localScripts,
                        acquireUsed,
                        webUsed,
                        jarUsed,
                        lang);
                if (!variantTokens.isEmpty() && hasJei) {
                    replySources = ReplySources.softenJeiForVariant(replySources);
                }
                factMarkerSources = List.copyOf(facts);
                playerFacts = AskReplyScrub.playerSafeFacts(purposeFactLines, acquire);
                final AskLoopState loopState = loop;
                final List<String> factsLive = facts;
                final List<String> factsFull = facts;   // fallback/400 path always gets the full wall
                final int factCapLive = factCap;
                final String purposeForLlm = purposeBlock.isBlank() ? null : purposeBlock;
                final boolean hasJeiForLlm = hasJei;
                final String recipeGetCleanForLlm = recipeGetClean;
                final String jeiFocusId = jeiFocusItemId == null || jeiFocusItemId.isBlank()
                        ? null
                        : jeiFocusItemId.trim();
                AskToolLoop.LlmBridge bridge = new AskToolLoop.LlmBridge() {
                    private void pushExtras() {
                        for (String extra : loopState.extraFactLines()) {
                            if (!extra.isBlank() && !factsLive.contains(extra) && factsLive.size() < factCapLive) {
                                factsLive.add(extra);
                            }
                        }
                    }

                    private String jeiForLlm() {
                        return AskLoopState.isEmptyOrMiss(loopState.jeiText())
                                ? (hasJeiForLlm ? recipeGetCleanForLlm : null)
                                : loopState.jeiText();
                    }

                    /** Catalog for LLM: recipeCardLines preferred, else slim fallbacks. */
                    private String recipeCatalogForLlm() {
                        // Prefer the ACTUAL collected cards (AskService.setRecipeCardLines from
                        // catalogLines(cardsCollected)) — UI index order, real roles. The lead
                        // already contains the "[RECIPE_CARDS]" header + role semantics — do NOT
                        // strip it as a duplicate.
                        List<String> cardLines = loopState.recipeCardLines();
                        String catalog;
                        if (cardLines != null && !cardLines.isEmpty()) {
                            StringBuilder sb = new StringBuilder(ReplyLang.recipeCardsCatalogLead(lang));
                            for (String line : cardLines) {
                                sb.append('\n').append(line);
                            }
                            catalog = sb.toString();
                        } else {
                            catalog = recipeCardsCatalogSlim(loopState.recipeCatalog());
                            if (catalog == null || catalog.isBlank()) {
                                catalog = recipeCardsCatalogSlim(loopState.jeiText());
                            }
                            if (catalog != null && catalog.isBlank()) {
                                catalog = null;
                            }
                        }
                        return catalog == null || catalog.isBlank() ? null : catalog;
                    }

                    /** Tool-capable decision shared by slim jei / slim purpose / completeWithTools. */
                    private boolean capableForTools() {
                        return LlmClient.toolsOffered(llm.lastBase());
                    }

                    private String jeiForLlmSlim() {
                        if (!capableForTools()) {
                            return jeiForLlm();
                        }
                        String catalog = recipeCatalogForLlm();
                        String pre = tooltipHintBlock(recipeGetCleanForLlm);
                        if (!pre.isEmpty()) {
                            return catalog == null || catalog.isBlank() ? pre : pre + "\n" + catalog;
                        }
                        return catalog;
                    }

                    /** Full/no-tools path: recipeCatalogForLlm() ⊕ jeiForLlm() dump (catalog deduped). */
                    private String jeiForLlmFull() {
                        return mergeJeiCatalogFull(jeiForLlm(), recipeCatalogForLlm());
                    }

                    private String purposeForLlmSlim() {
                        return capableForTools() ? null : purposeForLlm;
                    }

                    @Override
                    public String askNoTools() {
                        pushExtras();
                        LlmRound r = llm.completeRound(
                                question, held, hotbarRefs, focus, factsFull, retrieved.sources(),
                                policy, override, qConflict, jeiForLlmFull(), prior, lang, purposeForLlm,
                                jeiFocusId, null, loopState.httpTimeout(), loopState.toolTurns());
                        return r == null ? null : r.content();
                    }

                    @Override
                    public LlmRound completeWithTools(List<String> toolNames) {
                        pushExtras();
                        boolean capable = capableForTools();
                        List<String> promptFacts = capable ? List.of() : factsLive;
                        return llm.completeRound(
                                question, held, hotbarRefs, focus, promptFacts, retrieved.sources(),
                                policy, override, qConflict, jeiForLlmSlim(), prior, lang, purposeForLlmSlim(),
                                jeiFocusId, toolNames, loopState.httpTimeout(), loopState.toolTurns());
                    }

                    @Override
                    public void rememberNoNativeTools() {
                        // LlmClient already records the URL on HTTP 400 + tools
                    }

                    @Override
                    public boolean noNativeTools() {
                        if (PackAiConfig.askNativeToolsOff()) {
                            return true;
                        }
                        if (PackAiConfig.askNativeToolsForce()) {
                            return false;
                        }
                        return llm.urlLacksNativeTools();
                    }

                    @Override
                    public String nativeToolsMode() {
                        return PackAiConfig.askNativeToolsMode();
                    }
                };
                AskToolEnv toolEnv = bindAskToolEnv(loopState, held.sample(), idx, gameDir, scanners, held);
                toolEnv.purposeTooltip = purposeTooltip == null ? "" : purposeTooltip;
                toolEnv.recipeCardLines = loopState.recipeCardLines();
                toolEnv.catalogCards = loopState.catalogCards();
                AskToolLoop.bindEnv(toolEnv);
                try {
                    llmAnswer = AskToolLoop.INSTANCE.firstAsk(loopState, bridge);
                    if (llmAnswer != null && !llmAnswer.isBlank() && !ReplyLang.isLlmSetupError(llmAnswer)) {
                        loopState.countSuccessfulLlm();
                        if (loopState.intent() != AskLoopState.Intent.PURPOSE) {
                            llmAnswer = AskToolLoop.INSTANCE.continueAfterAsk(loopState, llmAnswer, bridge);
                        }
                    }
                    final String finalRaw = llmAnswer;
                    AskTrace.event("model.reply.final", o -> o.addProperty("content", finalRaw == null ? "" : finalRaw));
                } finally {
                    AskToolLoop.clearEnv(loop);
                }
                llmUsage = llm.cumulativeUsage();
            }
            if (llmAnswer != null && !llmAnswer.isBlank() && ReplyLang.isLlmSetupError(llmAnswer)) {
                return AskResult.text(llmAnswer).withTokenUsage(llmUsage);
            }
            // Refs from raw before scrub strips <!--packai:items=…--> (L1).
            List<String> markerRefs = ItemResolver.extractIds(llmAnswer);
            String proseScrubbed = AskReplyScrub.scrubPromptEcho(llmAnswer);
            final String scrubBefore = llmAnswer;
            AskTrace.event("check.scrub", o -> {
                o.addProperty("before", scrubBefore == null ? "" : scrubBefore);
                o.addProperty("after", proseScrubbed);
                o.addProperty("rules", "scrubPromptEcho");
            });
            String blankFallback = SummonRecipeLookup.isSummonQuestion(question)
                    ? String.join("\n", HonestMiss.summonMissFactsPlayer(lang, List.of()))
                    : ReplyLang.askBodyUnavailable(lang).trim();
            boolean llmSetupErr = llmAnswer != null && ReplyLang.isLlmSetupError(llmAnswer);
            boolean llmEmpty = llmAnswer == null || llmAnswer.isBlank()
                    || AskReplyScrub.isVisiblyEmpty(proseScrubbed);
            boolean miss = !llmSetupErr && AskMissFallback.isMissAnswer(proseScrubbed);
            String displaySrc;
            String visibleAnswer;
            if (miss) {
                String jeiProbe = !AskLoopState.isEmptyOrMiss(loop.jeiText())
                        ? loop.jeiText()
                        : (recipeGetClean == null ? "" : recipeGetClean);
                List<String> jeiLines = AskMissFallback.extractJeiPlayerLines(jeiProbe, 3);
                String reason = AskMissFallback.reasonCode(
                        lang,
                        loop.hadNonEmptyJeiDump() || !jeiLines.isEmpty(),
                        llmEmpty,
                        false);
                AskMissFallback.Result composed = AskMissFallback.compose(
                        lang, jeiLines, playerFacts, blankFallback, reason);
                visibleAnswer = composed.body();
                displaySrc = composed.displaySrc();
                String bucket = AskMissFallback.fallbackReasonBucket(false, llmEmpty);
                int dsml = Math.max(loop.dsmlRecovered(), AskTrace.dsmlRecovered());
                PackAiMod.LOGGER.info(
                        "Pack AI ask fallback reason={} codes={} rounds={} dsmlRecovered={}",
                        bucket,
                        loop.missToolCodes(),
                        loop.llmRounds(),
                        dsml);
            } else {
                displaySrc = !AskReplyScrub.isVisiblyEmpty(proseScrubbed)
                        ? "prose"
                        : (playerFacts != null && !playerFacts.isEmpty() ? "playerfacts" : "langfallback");
                visibleAnswer = AskReplyScrub.proseOrFacts(llmAnswer, playerFacts, blankFallback);
            }
            if (!visibleAnswer.isBlank()) {
                String body = override
                        ? ReplyLang.questOverrideNotice(lang) + visibleAnswer
                        : visibleAnswer;
                // Post-LLM: fixed Machine section must survive (llm_style bans Markdown # headers).
                body = RecipeGetMarks.ensureVisibleInReply(body, machineSection, lang);
                // Post-LLM: canonical quest status (allowlist) — authoritative over LLM paraphrase.
                body = AskJeiHints.ensureQuestStatusVisible(body, acquire, lang);
                // Plan F: MODIFIED blank-frame mismatch → force player miss line (S10: skip when acquire non-empty).
                boolean forceHonestMiss = frameKind == ModularFrameStandard.Kind.MODIFIED
                        && (acquire == null || acquire.isEmpty());
                // Plan F fix2: diagnose miss branch (log-only; no behavior change).
                PackAiMod.LOGGER.info(
                        "Pack AI frame-miss: branch kind={} acquireEmpty={}",
                        frameKind,
                        acquire == null || acquire.isEmpty());
                String bodyBeforeMiss = body;
                body = HonestMiss.ensureAskMissAcquirePlayerVisible(body, lang, forceHonestMiss);
                if (forceHonestMiss && body != null && !body.equals(bodyBeforeMiss)) {
                    PackAiMod.LOGGER.info("Pack AI honest-miss: askMissAcquirePlayer inserted (MODIFIED frame)");
                }
                String obtainFill = acquire.isEmpty()
                        ? ""
                        : String.join("\n", AskReplyScrub.playerSafeFacts(acquire));
                if (looksLikeAcquireMissPin(obtainFill, lang)) {
                    obtainFill = "";
                }
                if (loop.intent() != AskLoopState.Intent.PURPOSE) {
                    body = AskReplyScrub.ensureHowToGetBody(
                            body,
                            obtainFill,
                            hasObtainRecipes(hasRecipeGet, jeiSummary, loop),
                            ReplyLang.obtainUnknown(lang));
                }
                boolean hasLocalFact = (acquire != null && !acquire.isEmpty())
                        || !jeiInfo.isEmpty()
                        || (questHits != null && !questHits.isEmpty());
                if (hasLocalFact) {
                    body = JeiInfoFacts.stripUnspecifiedMiss(body);
                }
                body = ReplySources.ensure(body, replySources, lang);
                // Post-LLM: FACT-grounded marker re-attach (after scrub path in AskResult; before RecipeEmbed UI).
                body = AskMarkerRepair.repair(
                        body, AskMarkerRepair.collectAllowed(factMarkerSources, List.of(), List.of()));
                // Plan F fix3: STANDARD craft line LAST — after ensureHowToGetBody / sources / marker
                // (fix1 early insert was overwritten by obtain-unknown rewrite of how-to-get).
                if (frameKind == ModularFrameStandard.Kind.STANDARD && frameMatch.recipeIndex() != null) {
                    ModularFrameStandard.FrameRecipe stdRecipe = ModularFrameStandard.recipeAt(frameMatch.recipeIndex());
                    PackAiMod.LOGGER.info(
                            "Pack AI frame-standard: branch entered recipe={}",
                            stdRecipe == null
                                    ? "null"
                                    : ("in=" + stdRecipe.ingredientItemIds()
                                            + " out=" + stdRecipe.resultItemId()));
                    String bodyBeforeStd = body;
                    String line = HonestMiss.frameStandardRecipeLine(lang, stdRecipe);
                    if (line != null && !line.isBlank()) {
                        String replaced = AskReplyScrub.replaceHowToGetBody(body, line);
                        if (replaced != null && !replaced.equals(body)) {
                            body = replaced;
                            PackAiMod.LOGGER.info(
                                    "Pack AI frame-standard: how-to-get replaced (STANDARD frame)");
                        } else {
                            body = HonestMiss.ensureFrameStandardRecipeVisible(body, lang, stdRecipe);
                            PackAiMod.LOGGER.info(
                                    "Pack AI frame-standard: how-to-get heading not found -> appended");
                        }
                    } else {
                        body = HonestMiss.ensureFrameStandardRecipeVisible(body, lang, stdRecipe);
                    }
                    boolean inserted = body != null && !body.equals(bodyBeforeStd);
                    if (inserted) {
                        PackAiMod.LOGGER.info("Pack AI frame-standard: recipe line inserted (STANDARD frame)");
                    }
                    boolean present = stdRecipe != null
                            && HonestMiss.bodyContainsAllLabels(
                                    body, stdRecipe.ingredientItemIds(), stdRecipe.resultItemId());
                    PackAiMod.LOGGER.info(
                            "Pack AI frame-standard: final check present={} inserted={}", present, inserted);
                    if (present || inserted) {
                        String bodyBeforeStrip = body;
                        body = AskReplyScrub.stripLangMissLine(body, lang);
                        PackAiMod.LOGGER.info(
                                "Pack AI frame-standard: miss line stripped={} present={}",
                                bodyBeforeStrip != null && !bodyBeforeStrip.equals(body),
                                present);
                    }
                }
                body = AskJeiHints.ensureToolBuildPartsLine(body, frameKind, toolBuild, lang);
                body = InfoCompleteness.append(body, infoGapLines(heldItemId, lang, gameDir), lang);
                if (override) {
                    return AskResult.text(body).withTokenUsage(llmUsage).withDisplaySrc(displaySrc)
                            .withSuggestedItemIds(markerRefs);
                }
                if (!questHits.isEmpty()) {
                    return AskResult.of(body, questHits).withTokenUsage(llmUsage).withDisplaySrc(displaySrc)
                            .withSuggestedItemIds(markerRefs);
                }
                return withSideQuests(body, allQuests, question, heldItemId, questExtras, variantTokens, offline, false, lang)
                        .withTokenUsage(llmUsage)
                        .withDisplaySrc(displaySrc)
                        .withSuggestedItemIds(markerRefs);
            }

            if (!questHits.isEmpty() && !override) {
                String localPlain = qConflict
                        ? Plainify.plainify(retrieved.snippets(), retrieved.sources())
                        : null;
                return AskResult.of(
                        QuestGuide.formatGuide(
                                questHits, qConflict, localPlain, questMatch.totalMatched(), offline, lang)
                                + (offline ? "" : ReplyLang.tipQuestSummaryNoAi(lang)),
                        questHits
                );
            }

            if (plain != null) {
                return withSideQuests(plain, allQuests, question, heldItemId, questExtras, variantTokens, offline, override, lang);
            }

            List<String> acquireOffline = idx.acquireFactsFor(heldItemId, lang, variantTokens);
            boolean obtainRecipes = hasObtainRecipes(hasRecipeGet, jeiSummary, loop);
            if (hasJei || hasMachine) {
                // Raw JEI dump only when LLM unavailable — tip so it doesn't look like "full AI".
                String tip = offline ? "" : ReplyLang.tipNeedLlm(lang);
                StringBuilder offlineBody = new StringBuilder();
                if (obtainRecipes && hasJei) {
                    offlineBody.append(ReplyLang.sectionHowToGet(lang)).append('\n').append(recipeGetClean);
                } else if (!acquireOffline.isEmpty()) {
                    offlineBody.append(ReplyLang.sectionHowToGet(lang)).append('\n')
                            .append(String.join("\n", acquireOffline));
                } else {
                    offlineBody.append(ReplyLang.sectionHowToGet(lang)).append('\n')
                            .append(ReplyLang.obtainUnknown(lang));
                }
                String getWithBuild = assembleHowToGet(List.of(offlineBody.toString()), toolBuild, lang);
                offlineBody.setLength(0);
                offlineBody.append(getWithBuild);
                if (hasMachine) {
                    if (offlineBody.length() > 0) {
                        offlineBody.append("\n\n");
                    }
                    offlineBody.append(machineSection);
                }
                String visibleOffline = playerSafeVisibleText(offlineBody.toString(), lang, question);
                return withSideQuests(
                        visibleOffline + "\n\n" + ReplyLang.sourceHeader(lang) + "JEI" + tip,
                        allQuests, question, heldItemId, questExtras, variantTokens, offline, override, lang);
            }

            if (!acquireOffline.isEmpty()) {
                String visibleAcquire = playerSafeVisibleText(
                        assembleHowToGet(List.of(String.join("\n", acquireOffline)), toolBuild, lang),
                        lang, question);
                return withSideQuests(
                        visibleAcquire + "\n\n"
                                + ReplyLang.sourceHeader(lang)
                                + ReplyLang.labelAcquireOffline(lang),
                        allQuests, question, heldItemId, questExtras, variantTokens, offline, override, lang);
            }
            if (frameKind != ModularFrameStandard.Kind.STANDARD
                    && HonestMiss.shouldPinAcquireMiss(acquireOffline, obtainRecipes, question, heldItemId)) {
                return withSideQuests(
                        assembleHowToGet(
                                List.of(String.join("\n", HonestMiss.acquireMissFactsPlayer(heldItemId, lang))),
                                toolBuild, lang) + "\n\n"
                                + ReplyLang.sourceHeader(lang)
                                + ReplyLang.labelNone(lang),
                        allQuests, question, heldItemId, questExtras, variantTokens, offline, override, lang);
            }

            if (offline && !override && !allQuests.isEmpty()) {
                QuestGuide.MatchResult side = QuestGuide.matchForOfflineResult(
                        allQuests, question, heldItemId, questExtras, variantTokens);
                if (!side.hits().isEmpty()) {
                    return AskResult.of(
                            QuestGuide.formatGuide(side.hits(), false, null, side.totalMatched(), true, lang)
                                    + ReplyLang.tipOfflineQuest(lang),
                            side.hits()
                    );
                }
            }

            String tip = offline
                    ? ReplyLang.tipOfflineEmpty(lang)
                    : ReplyLang.tipNeedLlm(lang);
            return AskResult.text(ReplyLang.friendlyOffline(lang, question) + tip
                    + "\n\n" + ReplyLang.sourceHeader(lang) + ReplyLang.labelNone(lang));
        }
    }

    /**
     * True when local pack data looks like it overrides this held item (not merely that
     * the pack has scripts for unrelated mods).
     */
    static boolean isHeldLocallyTouched(
            String heldItemId,
            PackIndex.RetrieveResult retrieved,
            List<String> acquireFacts
    ) {
        if (heldItemId == null || heldItemId.isBlank()) {
            return false;
        }
        String id = heldItemId.toLowerCase(Locale.ROOT);
        if (retrieved.removedItems() != null && retrieved.removedItems().contains(id)) {
            return true;
        }
        if (retrieved.graphFacts() != null) {
            String needle = "item:" + id;
            for (String f : retrieved.graphFacts()) {
                if (f == null || !f.contains(needle)) {
                    continue;
                }
                if (f.contains("-[removed]->")) {
                    return true;
                }
                if (f.contains("-[desc]->") || f.contains("-[score]->") || f.contains("-[triggers]->")
                        || f.contains("-[on:")) {
                    return true;
                }
                if (f.contains("-[recipe_needs]->") && f.startsWith(needle + " ")) {
                    return true;
                }
                if (f.contains("-[right_click") && f.contains(id)) {
                    return true;
                }
            }
        }
        if (retrieved.snippets() != null) {
            for (String snip : retrieved.snippets()) {
                if (snip != null && snip.toLowerCase(Locale.ROOT).contains(id)) {
                    return true;
                }
            }
        }
        if (acquireFacts != null) {
            for (String line : acquireFacts) {
                if (ReplyLang.isScriptNeedsLine(line) || ReplyLang.isScriptRemovedLine(line)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * When JEI craft/get or easier local acquire (loot/interact/trade/…) exists and the
     * player prefers craft/loot/balanced (not quest), do not feed full quest descriptions
     * as primary obtain/use facts.
     * Quest-only RECIPE_CARDS ({@code role=quest}) are not craft — do not demote.
     */
    static boolean demoteQuestNarrative(boolean hasBetterNonQuestObtain, String preferObtain, boolean questOverride) {
        if (!hasBetterNonQuestObtain || questOverride) {
            return false;
        }
        String prefer = preferObtain == null ? "craft" : preferObtain.trim().toLowerCase(Locale.ROOT);
        return !"quest".equals(prefer);
    }

    /**
     * True when a non-quest obtain path exists: loot/trade/script acquire, or a real
     * {@code role=output} craft card. Quest-reward cards in [RECIPE_CARDS] do not count.
     */
    static boolean hasNonQuestObtainPath(
            boolean hasRecipeGet, String jeiSummary, List<String> acquire, String replyLang
    ) {
        if (hasNonQuestAcquirePath(acquire, replyLang)) {
            return true;
        }
        if (!hasRecipeGet) {
            return false;
        }
        if (hasRecipeCardCatalog(jeiSummary)) {
            return hasNonQuestCraftObtain(jeiSummary);
        }
        return true;
    }

    static boolean hasRecipeCardCatalog(String jeiSummary) {
        if (jeiSummary == null || jeiSummary.isBlank()) {
            return false;
        }
        return Pattern.compile("^\\d+\\s*\\|\\s*role=", Pattern.MULTILINE).matcher(jeiSummary).find();
    }

    /**
     * JEI how-to-get (R / role=output). Catalog of INPUT-only uses must not count —
     * those belong under 作为材料, not 怎么来. After the tool loop, prefer {@code loop.jeiText()}
     * so a stale AskService catalog cannot hide a later obtain dump.
     */
    static boolean hasObtainRecipes(boolean hasRecipeGet, String jeiSummary) {
        if (hasRecipeCardCatalog(jeiSummary)) {
            return hasNonQuestCraftObtain(jeiSummary);
        }
        return hasRecipeGet;
    }

    static boolean hasObtainRecipes(boolean hasRecipeGet, String jeiSummary, AskLoopState loop) {
        String probe = loop != null && !AskLoopState.isEmptyOrMiss(loop.jeiText())
                ? loop.jeiText()
                : jeiSummary;
        return hasObtainRecipes(hasRecipeGet, probe);
    }

    /** Offline/player dump: keep player-safe lines only; empty → honest miss. */
    static String playerSafeVisibleText(String assembled, String lang, String question) {
        List<String> kept = assembled == null || assembled.isBlank()
                ? List.of()
                : AskReplyScrub.playerSafeFacts(List.of(assembled));
        if (!kept.isEmpty()) {
            return String.join("\n", kept);
        }
        if (SummonRecipeLookup.isSummonQuestion(question)) {
            return ReplyLang.askMissSummonPlayer(lang);
        }
        return ReplyLang.askMissAcquirePlayer(lang);
    }

    static boolean looksLikeAcquireMissPin(String text, String lang) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String miss = ReplyLang.acquireIndexMiss(lang);
        return miss != null && !miss.isBlank() && text.contains(miss);
    }

    /** Catalog has a craft/smelt obtain card (not quest reward, not input-use). */
    static boolean hasNonQuestCraftObtain(String jeiSummary) {
        if (jeiSummary == null || jeiSummary.isBlank()) {
            return false;
        }
        return Pattern.compile("^\\d+\\s*\\|\\s*role=output\\s*\\|", Pattern.MULTILINE)
                .matcher(jeiSummary)
                .find();
    }

    /**
     * Category titles from AskService {@code N | role=… | Title} catalog lines in jeiSummary.
     * Used to skip demoted related-quest notes when a JEI card already shows that quest title.
     */
    static Set<String> recipeCardCategoryTitlesFromJei(String jeiSummary) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (jeiSummary == null || jeiSummary.isBlank()) {
            return out;
        }
        Pattern p = Pattern.compile("^\\d+\\s*\\|\\s*role=(?:input|output|quest)\\s*\\|\\s*([^|\\n]+)", Pattern.MULTILINE);
        Matcher m = p.matcher(jeiSummary);
        while (m.find()) {
            String cat = QuestGuide.normQuestTitle(m.group(1));
            if (!cat.isEmpty() && !"?".equals(cat)) {
                out.add(cat);
            }
        }
        return out;
    }

    /** True when local acquire list has a non-quest path (loot / fish / interact / trade / script). */
    static boolean hasNonQuestAcquirePath(List<String> acquire, String replyLang) {
        if (acquire == null || acquire.size() <= 1) {
            return false;
        }
        String lang = replyLang == null || replyLang.isBlank() ? "zh_tw" : replyLang.trim();
        String submit = ReplyLang.questSubmit(lang);
        String obtain = ReplyLang.questObtain(lang);
        for (int i = 1; i < acquire.size(); i++) {
            String line = acquire.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            if (line.startsWith(submit) || line.startsWith(obtain)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static AskResult withSideQuests(
            String body,
            List<QuestGuide.Hit> allQuests,
            String question,
            String heldItemId,
            List<String> hotbar,
            List<String> variantTokens,
            boolean offline,
            boolean override,
            String replyLang
    ) {
        if (override || allQuests.isEmpty()) {
            return AskResult.text(ReplySources.ensure(body, List.of(), replyLang));
        }
        QuestGuide.MatchResult side = offline
                ? QuestGuide.matchForOfflineResult(allQuests, question, heldItemId, hotbar, variantTokens)
                : QuestGuide.matchResult(allQuests, question, heldItemId, hotbar, variantTokens);
        if (side.hits().isEmpty()) {
            return AskResult.text(body);
        }
        return AskResult.of(
                body + "\n\n" + QuestGuide.formatGuide(side.hits(), false, null, side.totalMatched(), offline, replyLang),
                side.hits()
        );
    }

    private static void appendCapped(List<String> facts, List<String> extra, int factCap) {
        if (extra == null || extra.isEmpty()) {
            return;
        }
        for (String line : extra) {
            if (facts.size() >= factCap) {
                return;
            }
            if (line != null && !line.isBlank()) {
                facts.add(line);
            }
        }
    }

    /**
     * True when {@code gf} is a raw graph edge that PackIndex already ranked into acquireLines.
     * Cap ~12 — overflow edges are absent from {@code rankedSkipEdges} and must stay in graphLines.
     */
    static boolean coveredByRankedAcquire(String gf, Set<String> rankedSkipEdges) {
        return gf != null
                && !gf.isBlank()
                && rankedSkipEdges != null
                && rankedSkipEdges.contains(gf);
    }

    /** Turn interact / loot / description graph edges into short readable lines for the LLM. */
    private static String formatInteractOrAcquireFact(String gf, String lang) {
        if (gf == null || gf.isBlank()) {
            return "";
        }
        int desc = gf.indexOf(" -[desc]-> ");
        if (desc > 5 && gf.startsWith("item:")) {
            return ReplyLang.itemDesc(lang, gf.substring(desc + " -[desc]-> ".length()).trim());
        }
        int score = gf.indexOf(" -[score]-> ");
        if (score > 5 && gf.startsWith("item:")) {
            return ReplyLang.itemScore(lang, gf.substring(score + " -[score]-> ".length()).trim());
        }
        int trig = gf.indexOf(" -[triggers]-> ");
        if (trig > 5 && gf.startsWith("item:")) {
            return ReplyLang.itemTriggers(lang, gf.substring(trig + " -[triggers]-> ".length()).trim());
        }
        int on = gf.indexOf(" -[on:");
        if (on > 5 && gf.startsWith("item:")) {
            int arrow = gf.indexOf("]-> ", on);
            if (arrow > on) {
                String event = gf.substring(on + " -[on:".length(), arrow).trim();
                String rest = gf.substring(arrow + "]-> ".length()).trim();
                if (rest.startsWith("gives:")) {
                    return ReplyLang.itemOnGives(lang, event, Plainify.displayName(rest.substring(6)));
                }
                if (rest.startsWith("effect:")) {
                    return ReplyLang.itemOnEffect(lang, event, Plainify.displayName(rest.substring(7)));
                }
                if (rest.startsWith("becomes:")) {
                    return ReplyLang.itemOnBecomes(lang, event, Plainify.displayName(rest.substring(8)));
                }
            }
        }
        int drops = gf.indexOf(" -[drops]-> ");
        if (drops > 5 && gf.startsWith("item:")) {
            String item = gf.substring(5, drops);
            return ReplyLang.itemDropsRandom(lang, Plainify.displayName(item))
                    + interactCondSuffix(gf, lang);
        }
        int rc = gf.indexOf(" -[right_click]-> ");
        if (rc > 5 && gf.startsWith("item:")) {
            String result = gf.substring(5, rc);
            String rest = gf.substring(rc + " -[right_click]-> ".length());
            String held = sliceAfter(rest, "held:");
            String target = interactTargetFromRest(rest);
            String via = sliceAfter(rest, "via:");
            if (target != null) {
                return ReplyLang.interactGet(
                        lang,
                        held == null || "_".equals(held) ? null : Plainify.displayName(held),
                        Plainify.displayName(target),
                        via)
                        + " → " + Plainify.displayName(result)
                        + interactCondSuffix(gf, lang);
            }
        }
        int use = gf.indexOf(" -[right_click_use]-> ");
        if (use > 5 && gf.startsWith("item:")) {
            String held = gf.substring(5, use);
            String rest = gf.substring(use + " -[right_click_use]-> ".length());
            String target = interactTargetFromRest(rest);
            String gets = sliceAfter(rest, "gets:");
            String via = sliceAfter(rest, "via:");
            String getsLabel = ReplyLang.getsResultLabel(lang, gets, null);
            if (gets != null && target != null) {
                return ReplyLang.interactUse(
                        lang, Plainify.displayName(target), getsLabel, via)
                        + "（" + Plainify.displayName(held) + "）"
                        + interactCondSuffix(gf, lang);
            }
            if (gets != null) {
                return ReplyLang.interactUseSelf(lang, getsLabel, via)
                        + "（" + Plainify.displayName(held) + "）"
                        + interactCondSuffix(gf, lang);
            }
        }
        int scriptUse = gf.indexOf(" -[script_use]-> ");
        if (scriptUse > 5 && gf.startsWith("item:")) {
            String rest = gf.substring(scriptUse + " -[script_use]-> ".length());
            if (rest.contains("via:jei_info")) {
                String note = JeiInfoFacts.textFromFact(gf);
                if (!note.isBlank()) {
                    return note;
                }
            }
            String gets = sliceAfter(rest, "gets:");
            String via = sliceAfter(rest, "via:");
            String call = sliceAfter(rest, "call:");
            return ReplyLang.interactUseSelf(
                            lang,
                            ReplyLang.getsResultLabel(lang, gets, call),
                            via == null ? "use" : via)
                    + interactCondSuffix(gf, lang);
        }
        int asBlock = gf.indexOf(" -[right_click_as_block]-> ");
        if (asBlock > 5 && gf.startsWith("item:")) {
            String target = gf.substring(5, asBlock);
            String rest = gf.substring(asBlock + " -[right_click_as_block]-> ".length());
            String held = sliceAfter(rest, "held:");
            String gets = sliceAfter(rest, "gets:");
            String via = sliceAfter(rest, "via:");
            if (gets != null) {
                return ReplyLang.interactAsTarget(
                        lang,
                        held == null || "_".equals(held) ? null : Plainify.displayName(held),
                        ReplyLang.getsResultLabel(lang, gets, null),
                        via)
                        + "（" + Plainify.displayName(target) + "）"
                        + interactCondSuffix(gf, lang);
            }
        }
        return Plainify.humanizeGraphFact(gf);
    }

    private static String interactCondSuffix(String gf, String lang) {
        if (gf == null || !gf.contains("if:")) {
            return "";
        }
        List<String> bits = new ArrayList<>();
        if (gf.contains("if:thunder")) {
            bits.add(ReplyLang.itemIfThunder(lang));
        }
        int i = 0;
        while ((i = gf.indexOf("if:stage:", i)) >= 0) {
            int start = i + "if:stage:".length();
            int end = start;
            while (end < gf.length()) {
                char c = gf.charAt(end);
                if (c == ' ' || c == '+') {
                    break;
                }
                end++;
            }
            bits.add(ReplyLang.itemIfStage(lang, gf.substring(start, end)));
            i = end;
        }
        return bits.isEmpty() ? "" : "（" + String.join("；", bits) + "）";
    }

    private static String interactTargetFromRest(String rest) {
        String t = sliceAfter(rest, "block:");
        if (t == null || "_".equals(t)) {
            t = sliceAfter(rest, "entity:");
        }
        return t == null || "_".equals(t) ? null : t;
    }

    private static String sliceAfter(String rest, String key) {
        if (rest == null || key == null) {
            return null;
        }
        int i = rest.indexOf(key);
        if (i < 0) {
            return null;
        }
        int start = i + key.length();
        int end = start;
        while (end < rest.length()) {
            char c = rest.charAt(end);
            if (c == ' ' || c == '+') {
                break;
            }
            end++;
        }
        String id = rest.substring(start, end).trim();
        return id.isEmpty() ? null : id;
    }

    /** Keep AskService [TOOLTIP_HINT] through slim native-tools path (Wave 22:
     *  [ENCHANT_TABLE] pre-injection removed; on-demand enchant_lookup instead). */
    private static String tooltipHintBlock(String jeiText) {
        if (jeiText == null || jeiText.isBlank()) {
            return "";
        }
        int start = jeiText.indexOf("[TOOLTIP_HINT]");
        if (start < 0) {
            return "";
        }
        int cards = jeiText.indexOf("[RECIPE_CARDS]", start);
        int end = cards >= 0 ? cards : jeiText.length();
        return jeiText.substring(start, end).trim();
    }

    /** Capable tool rounds: keep indexed [RECIPE_CARDS] catalog only (no JEI summary / machine noise). */
    public static String recipeCardsCatalogSlim(String jeiText) {
        if (jeiText == null || jeiText.isBlank()) {
            return null;
        }
        int idx = jeiText.indexOf("[RECIPE_CARDS]");
        if (idx < 0) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        boolean sawEntry = false;
        for (String line : jeiText.substring(idx).split("\n", -1)) {
            if (out.isEmpty()) {
                if (!line.contains("[RECIPE_CARDS]")) {
                    continue;
                }
                out.append(line);
            } else if (isRecipeCatalogEntryLine(line)) {
                out.append('\n').append(line);
                sawEntry = true;
            } else {
                break;
            }
        }
        return sawEntry ? out.toString() : null;
    }

    /** Remove every [RECIPE_CARDS] lead line and every catalog entry line from a JEI dump —
     *  any position, any number of blocks, blank lines between lead and entries included.
     *  Package-private so {@link AskToolLoopCheck} can pin the behaviour. */
    static String stripRecipeCardsBlock(String jeiText) {
        if (jeiText == null || jeiText.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String raw : jeiText.split("\n", -1)) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (line.contains("[RECIPE_CARDS]") || isRecipeCatalogEntryLine(line)) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(line);
        }
        return out.toString().trim();
    }

    /** askNoTools (no-tools / HTTP-400 fallback) JEI payload: the real UI catalog first, then the
     *  JEI dump with any duplicated catalog block removed. Package-private for {@link AskToolLoopCheck}. */
    static String mergeJeiCatalogFull(String fullDump, String catalog) {
        if (catalog == null || catalog.isBlank()) {
            return fullDump;
        }
        if (fullDump == null || fullDump.isBlank()) {
            return catalog;
        }
        String rest = stripRecipeCardsBlock(fullDump);
        return rest.isBlank() ? catalog : catalog + "\n" + rest;
    }

    private static boolean isRecipeCatalogEntryLine(String line) {
        if (line == null) {
            return false;
        }
        String t = line.trim();
        if (t.isEmpty()) {
            return false;
        }
        int bar = t.indexOf(" | ");
        if (bar <= 0) {
            return false;
        }
        for (int i = 0; i < bar; i++) {
            if (!Character.isDigit(t.charAt(i))) {
                return false;
            }
        }
        return t.substring(bar + 3).contains("role=");
    }

    /**
     * Offline how-to-get exit: same {@code [TOOL_BUILD]} splice as the LLM path.
     * Empty / missing marker → {@code getBlocks} joined unchanged.
     */
    static String assembleHowToGet(List<String> getBlocks, String toolBuild, String lang) {
        return String.join("\n", withToolBuildHowToGet(getBlocks, toolBuild, lang));
    }

    /**
     * Pin {@code [TOOL_BUILD]} under how-to-get, before JEI getBody.
     * Re-uses the how-to-get heading when a block already starts with it
     * (trailing space / CRLF on the heading line still counts as one heading);
     * otherwise builds one ({@link ReplyLang#sectionHowToGet}) and prefixes.
     * Empty / missing marker → {@code jeiLines} unchanged.
     */
    static List<String> withToolBuildHowToGet(List<String> jeiLines, String toolBuild, String lang) {
        if (toolBuild == null || toolBuild.isBlank() || !toolBuild.contains("[TOOL_BUILD]")) {
            return jeiLines == null ? List.of() : jeiLines;
        }
        String heading = ReplyLang.sectionHowToGet(lang);
        String build = toolBuild.trim();
        if (jeiLines == null || jeiLines.isEmpty()) {
            return List.of(heading + "\n" + build);
        }
        List<String> out = new ArrayList<>(jeiLines.size());
        boolean placed = false;
        for (String block : jeiLines) {
            if (!placed && block != null && block.startsWith(heading)) {
                int i = heading.length();
                while (i < block.length()) {
                    char c = block.charAt(i);
                    if (c == ' ' || c == '\t') {
                        i++;
                    } else {
                        break;
                    }
                }
                if (i < block.length() && block.charAt(i) == '\r') {
                    i++;
                }
                if (i < block.length() && block.charAt(i) == '\n') {
                    i++;
                }
                String rest = block.substring(i);
                out.add(rest.isBlank() ? heading + "\n" + build : heading + "\n" + build + "\n" + rest);
                placed = true;
            } else {
                out.add(block);
            }
        }
        if (!placed) {
            List<String> prefixed = new ArrayList<>(out.size() + 1);
            prefixed.add(heading + "\n" + build);
            prefixed.addAll(out);
            return prefixed;
        }
        return out;
    }

    /**
     * B11: both drain + LLM binds share dropId／loop wiring (per-bind refId still resets).
     * Caller may still set purposeTooltip／catalogCards after return.
     */
    static AskToolEnv bindAskToolEnv(
            AskLoopState loop,
            ItemStack sample,
            PackIndex idx,
            Path gameDir,
            List<String> scanners,
            ItemRef held
    ) {
        AskToolEnv env = new AskToolEnv(sample, idx, gameDir, scanners, held);
        if (loop != null) {
            env.loop = loop;
            String drop = loop.modularFrameDropId();
            env.modularFrameDropId = drop == null ? "" : drop;
            String keepOut = loop.frameStandardKeepOutputId();
            env.frameStandardKeepOutputId = keepOut == null ? "" : keepOut;
            java.util.List<String> keepIn = loop.frameStandardKeepInputIds();
            env.frameStandardKeepInputIds = keepIn == null ? java.util.List.of() : keepIn;
        }
        return env;
    }

    /**
     * 合併後總行數 ≤3（連『另有 N 項』行）；唔再逐 class 硬 cap。
     */
    private static List<String> infoGapLines(String itemId, String lang, Path gameDir) {
        List<String> gaps = new ArrayList<>();
        if (itemId == null || itemId.isBlank()) {
            return gaps;
        }
        List<String> lootLines = new ArrayList<>();
        List<String> useLines = new ArrayList<>();
        List<String> otherLines = new ArrayList<>();
        for (String code : JarLightIndex.INSTANCE.routeLinesForItem(itemId)) {
            if (code == null || code.length() < 3 || code.charAt(1) != '|' || AcquireAskTool.droppedJarRoute(code)) {
                continue;
            }
            char kind = code.charAt(0);
            if (kind == 'R') {
                AskTrace.event("check.info_gap_skip", o -> {
                    o.addProperty("code", code);
                    o.addProperty("reason", "as_ingredient");
                });
                continue;
            }
            String line;
            List<String> bucket;
            if (kind == 'L') {
                String table = code.substring(2);
                line = Plainify.lootLine(lang, itemId, table);
                bucket = lootLines;
            } else if (kind == 'U') {
                String rest = code.substring(2);
                int sep = rest.indexOf('|');
                String resultId = sep < 0 ? rest : rest.substring(sep + 1);
                String name = OfficialDisplay.officialName(resultId);
                if (name == null || name.isBlank()) {
                    name = Plainify.displayName(resultId);
                }
                line = ReplyLang.infoGapRelated(lang, name);
                bucket = useLines;
            } else {
                line = JarLightIndex.formatFact(code, lang);
                bucket = otherLines;
            }
            if (line == null || line.isBlank()) {
                continue;
            }
            if (!AskReplyScrub.isPlayerSafeLine(line)) {
                AskTrace.event("check.info_gap_drop", o -> {
                    o.addProperty("code", code);
                    o.addProperty("reason", "unsafe");
                });
                continue;
            }
            bucket.add(line);
        }
        otherLines.addAll(AcquireAskTool.humanWorldgenRoutes(lang, WorldgenIndex.routesForItem(itemId, gameDir)));
        int lCount = lootLines.size();
        int uCount = useLines.size();
        int otherCount = otherLines.size();
        gaps = gapPanelLines(lootLines, useLines, otherLines, lang);
        int merged = lCount + uCount + otherCount;
        int extra = merged > 3 ? merged - 2 : 0;
        int total = gaps.size();
        int extraTrace = extra;
        AskTrace.event("check.info_gap", o -> {
            o.addProperty("l|", lCount);
            o.addProperty("u|", uCount);
            o.addProperty("other", otherCount);
            o.addProperty("extra", extraTrace);
            o.addProperty("total", total);
        });
        return gaps;
    }

    /** 面板行最終裁切：合併後總行數（連「另有 N 項」行）永遠 ≤ CAP。 */
    static List<String> gapPanelLines(
            List<String> loot, List<String> use, List<String> other, String lang) {
        List<String> merged = new ArrayList<>();
        if (loot != null) {
            merged.addAll(loot);
        }
        if (use != null) {
            merged.addAll(use);
        }
        if (other != null) {
            merged.addAll(other);
        }
        int cap = 3;
        List<String> out = new ArrayList<>();
        int extra = 0;
        if (merged.size() > cap) {
            out.addAll(merged.subList(0, cap - 1));
            extra = merged.size() - (cap - 1);
        } else {
            out.addAll(merged);
        }
        if (extra > 0) {
            out.add(ReplyLang.infoGapMore(lang, String.valueOf(extra)));
        }
        return out;
    }

    private static String cacheKey(Path gameDir, List<String> modIds) {
        String dir = gameDir == null ? "" : gameDir.toAbsolutePath().toString();
        return dir + "|" + String.join(",", modIds);
    }
}

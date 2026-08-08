package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.skps9.packai.client.chat.ChatMessage;
import com.skps9.packai.config.PackAiConfig;

/**
 * In-mod ask engine — no external Python Bridge.
 */
public final class AskEngine {
    public static final AskEngine INSTANCE = new AskEngine();

    private final ConcurrentHashMap<String, PackIndex> indexes = new ConcurrentHashMap<>();
    private final LlmClient llm = new LlmClient();

    private AskEngine() {}

    public void warmup(Path gameDir, List<String> modIds) {
        String key = cacheKey(gameDir, modIds);
        PackIndex idx = indexes.computeIfAbsent(key, k -> new PackIndex());
        PackAuthorAgents.reload(gameDir);
        synchronized (idx) {
            idx.build(gameDir, ModScanners.active(modIds));
        }
        // Jar light scan deferred to first Ask (see ask() ensure) — not every warmup.
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
        ItemRef held = heldItem == null ? ItemRef.NONE : heldItem;
        List<ItemRef> hotbarRefs = hotbarItems == null ? List.of() : hotbarItems;
        List<ChatMessage> prior = history == null ? List.of() : history;
        String heldItemId = held.isPresent() ? held.id() : null;
        List<String> variantTokens = ItemVariantKeys.disambiguators(held);
        List<String> hotbarIds = new ArrayList<>();
        List<String> hintTokens = new ArrayList<>(held.hintTokens());
        hintTokens.addAll(ItemVariantKeys.hintExtras(held));
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
            List<String> acquire = idx.acquireFactsFor(heldItemId, lang);
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
            boolean emiPreview = RecipeGetMarks.isEmiPreview(jeiSummary);
            boolean noRecipeUi = RecipeGetMarks.isNoRecipeUi(jeiSummary);
            String recipeGetClean = RecipeGetMarks.strip(jeiSummary);
            boolean hasJei = recipeGetClean != null && !recipeGetClean.isBlank() && !emiPreview && !noRecipeUi;
            boolean hasRecipeGet = recipeGetClean != null && !recipeGetClean.isBlank();
            if (plain != null && retrieved.highConfidence() && questHits.isEmpty() && !hasRecipeGet) {
                // Local script match only when JEI has nothing better.
                return withSideQuests(plain, allQuests, question, heldItemId, questExtras, variantTokens, offline, override, replyLang);
            }

            String llmAnswer = null;
            List<String> replySources = List.of();
            if (!offline) {
                List<String> facts = new ArrayList<>();
                int factCap = PackAiConfig.maxFacts();
                String prefer = PackAiConfig.preferObtain();
                List<String> questFactLines = new ArrayList<>();
                for (QuestGuide.Hit h : questHits) {
                    String title = QuestGuide.displayTitle(h);
                    String desc = QuestGuide.refinePlayerText(h.description() == null ? "" : h.description());
                    questFactLines.add(ReplyLang.questFactLine(lang, title, desc));
                }
                List<String> acquireLines = acquire.isEmpty() ? List.of() : List.of(String.join("\n", acquire));
                List<String> jarFactLines = jarLines.isEmpty() ? List.of() : List.of(String.join("\n", jarLines));
                List<String> purposeLines = new ArrayList<>();
                List<String> graphLines = new ArrayList<>();
                Map<String, Set<String>> recipeNeeds = idx.recipeNeedsIndex();
                for (String gf : retrieved.graphFacts()) {
                    if (AskPurposeContext.isPurposeGraphFact(gf)) {
                        purposeLines.add(formatInteractOrAcquireFact(gf, lang));
                        continue;
                    }
                    if (gf.contains("-[drops]->")
                            || gf.contains("-[loot]->") || gf.contains("-[fish]->") || gf.contains("-[trade]->")
                            || gf.contains("-[removed]->")) {
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
                // Item-linked quest → how-to-use; soft-prefer variant tokens (same as quest match).
                List<QuestGuide.Hit> purposeQuests = new ArrayList<>();
                for (QuestGuide.Hit h : questHits) {
                    if (QuestGuide.mentionsFocusItem(h, heldItemId)) {
                        purposeQuests.add(h);
                    }
                }
                purposeQuests = ItemVariantKeys.preferMentioning(
                        purposeQuests, variantTokens, h -> QuestGuide.hitMentionsVariant(h, variantTokens));
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
                String purposeBlock = AskPurposeContext.buildPurposeBlock(
                        purposeTooltip, purposeLines, purposeGuide);
                List<String> purposeFactLines = purposeBlock.isBlank()
                        ? List.of()
                        : List.of(ReplyLang.sectionHowToUse(lang) + "\n" + purposeBlock);
                List<String> jeiLines;
                if (hasRecipeGet) {
                    String getBody = recipeGetClean;
                    if (!variantTokens.isEmpty()) {
                        getBody = ReplyLang.jeiVariantCaution(lang) + "\n" + recipeGetClean;
                    }
                    jeiLines = List.of(ReplyLang.sectionHowToGet(lang) + "\n" + getBody);
                } else {
                    jeiLines = List.of();
                }

                // Order blocks by player's preferred obtain pathway.
                // Purpose questions: purpose (tooltip/interact) first — never JEI-U as 用途.
                boolean purpose = PackIndex.isPurposeQuestion(question)
                        || PackIndex.isCodeOrBehaviorQuestion(question);
                List<List<String>> blocks = new ArrayList<>();
                if (purpose) {
                    blocks.add(purposeFactLines);
                    blocks.add(questFactLines);
                    blocks.add(acquireLines);
                    blocks.add(jarFactLines);
                    blocks.add(graphLines);
                    blocks.add(jeiLines);
                } else {
                switch (prefer) {
                    case "quest" -> {
                        blocks.add(questFactLines);
                        blocks.add(purposeFactLines);
                        blocks.add(jeiLines);
                        blocks.add(acquireLines);
                        blocks.add(jarFactLines);
                        blocks.add(graphLines);
                    }
                    case "loot" -> {
                        blocks.add(acquireLines);
                        blocks.add(jarFactLines);
                        blocks.add(purposeFactLines);
                        blocks.add(graphLines);
                        blocks.add(jeiLines);
                        blocks.add(questFactLines);
                    }
                    case "balanced" -> {
                        blocks.add(purposeFactLines);
                        blocks.add(jeiLines);
                        blocks.add(acquireLines);
                        blocks.add(jarFactLines);
                        blocks.add(graphLines);
                        blocks.add(questFactLines);
                    }
                    default -> { // craft
                        blocks.add(purposeFactLines);
                        blocks.add(jeiLines);
                        blocks.add(acquireLines);
                        blocks.add(jarFactLines);
                        blocks.add(graphLines);
                        blocks.add(questFactLines); // quest last
                    }
                }
                }
                for (List<String> block : blocks) {
                    appendCapped(facts, block, factCap);
                }

                boolean allowWeb = PackAiConfig.webSearchEnabled();
                boolean webUsed = false;
                if (allowWeb) {
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
                boolean guideUsed = purposeGuide != null && !purposeGuide.isBlank();
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
                llmAnswer = llm.ask(
                        question,
                        held,
                        hotbarRefs,
                        focus,
                        facts,
                        retrieved.sources(),
                        policy,
                        override,
                        qConflict,
                        hasJei ? recipeGetClean : null,
                        prior,
                        lang,
                        purposeBlock.isBlank() ? null : purposeBlock
                );
            }
            if (llmAnswer != null && !llmAnswer.isBlank() && ReplyLang.isLlmSetupError(llmAnswer)) {
                return AskResult.text(llmAnswer);
            }
            if (llmAnswer != null && !llmAnswer.isBlank()) {
                String body = override
                        ? ReplyLang.questOverrideNotice(lang) + llmAnswer
                        : llmAnswer;
                body = ReplySources.ensure(body, replySources, lang);
                if (override) {
                    return AskResult.text(body);
                }
                if (!questHits.isEmpty()) {
                    return AskResult.of(body, questHits);
                }
                return withSideQuests(body, allQuests, question, heldItemId, questExtras, variantTokens, offline, false, lang);
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

            if (hasJei) {
                // Raw JEI dump only when LLM unavailable — tip so it doesn't look like "full AI".
                String tip = offline ? "" : ReplyLang.tipNeedLlm(lang);
                return withSideQuests(
                        ReplyLang.sectionHowToGet(lang) + "\n" + recipeGetClean
                                + "\n\n" + ReplyLang.sourceHeader(lang) + "JEI" + tip,
                        allQuests, question, heldItemId, questExtras, variantTokens, offline, override, lang);
            }

            List<String> acquireOffline = idx.acquireFactsFor(heldItemId, lang);
            if (!acquireOffline.isEmpty()) {
                return withSideQuests(
                        String.join("\n", acquireOffline) + "\n\n"
                                + ReplyLang.sourceHeader(lang)
                                + ReplyLang.labelAcquireOffline(lang),
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
            if (gets != null && target != null) {
                return ReplyLang.interactUse(
                        lang, Plainify.displayName(target), Plainify.displayName(gets), via)
                        + "（" + Plainify.displayName(held) + "）"
                        + interactCondSuffix(gf, lang);
            }
            if (gets != null) {
                return ReplyLang.interactUseSelf(lang, Plainify.displayName(gets), via)
                        + "（" + Plainify.displayName(held) + "）"
                        + interactCondSuffix(gf, lang);
            }
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
                        Plainify.displayName(gets),
                        via)
                        + "（" + Plainify.displayName(target) + "）"
                        + interactCondSuffix(gf, lang);
            }
        }
        return Plainify.humanizeText(gf.replace("-[", " → ").replace("]->", " "));
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

    private static String cacheKey(Path gameDir, List<String> modIds) {
        String dir = gameDir == null ? "" : gameDir.toAbsolutePath().toString();
        return dir + "|" + String.join(",", modIds);
    }
}

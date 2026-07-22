package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.ArrayList;
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
        synchronized (idx) {
            idx.build(gameDir, ModScanners.active(modIds));
        }
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
        ItemRef held = heldItem == null ? ItemRef.NONE : heldItem;
        List<ItemRef> hotbarRefs = hotbarItems == null ? List.of() : hotbarItems;
        List<ChatMessage> prior = history == null ? List.of() : history;
        String heldItemId = held.isPresent() ? held.id() : null;
        List<String> hotbarIds = new ArrayList<>();
        List<String> hintTokens = new ArrayList<>(held.hintTokens());
        for (ItemRef ref : hotbarRefs) {
            if (ref.isPresent()) {
                hotbarIds.add(ref.id());
                hintTokens.addAll(ref.hintTokens());
            }
        }

        List<String> mods = modIds == null ? List.of() : modIds;
        List<String> scanners = ModScanners.active(mods);
        List<String> focus = ModScanners.focusMods(mods, heldItemId, question, hotbarIds);
        String key = cacheKey(gameDir, mods);
        PackIndex idx = indexes.computeIfAbsent(key, k -> new PackIndex());

        synchronized (idx) {
            if (idx.paths().isEmpty()) {
                idx.build(gameDir, scanners);
            }

            String lang = replyLang == null || replyLang.isBlank() ? "zh_tw" : replyLang.trim();
            String mode = PackAiConfig.resolvedMode();
            boolean offline = "offline".equals(mode);
            boolean override = QuestGuide.isOverride(question, questOverrideFlag);

            List<QuestGuide.Hit> allQuests = List.of();
            QuestGuide.MatchResult questMatch = new QuestGuide.MatchResult(List.of(), 0);
            if (!override) {
                allQuests = QuestGuide.index(gameDir, scanners, lang);
                questMatch = offline
                        ? QuestGuide.matchForOfflineResult(allQuests, question, heldItemId, hotbarIds)
                        : QuestGuide.matchResult(allQuests, question, heldItemId, hotbarIds);
            }
            List<QuestGuide.Hit> questHits = questMatch.hits();

            PackIndex.RetrieveResult retrieved = idx.retrieve(question, heldItemId, focus, hotbarIds, hintTokens);
            boolean qConflict = QuestGuide.conflict(questHits, retrieved.removedItems());

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
            boolean hasJei = jeiSummary != null && !jeiSummary.isBlank();
            if (plain != null && retrieved.highConfidence() && questHits.isEmpty() && !hasJei) {
                // Local script match only when JEI has nothing better.
                return withSideQuests(plain, allQuests, question, heldItemId, hotbarIds, offline, override, replyLang);
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
                List<String> graphLines = new ArrayList<>();
                Map<String, Set<String>> recipeNeeds = idx.recipeNeedsIndex();
                for (String gf : retrieved.graphFacts()) {
                    if (gf.contains("-[loot]->") || gf.contains("-[fish]->") || gf.contains("-[trade]->")
                            || gf.contains("-[removed]->")) {
                        graphLines.add(Plainify.humanizeText(gf.replace("-[", " → ").replace("]->", " ")));
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
                List<String> jeiLines = (jeiSummary != null && !jeiSummary.isBlank())
                        ? List.of(jeiSummary)
                        : List.of();

                // Order blocks by player's preferred obtain pathway.
                List<List<String>> blocks = new ArrayList<>();
                switch (prefer) {
                    case "quest" -> {
                        blocks.add(questFactLines);
                        blocks.add(jeiLines);
                        blocks.add(acquireLines);
                        blocks.add(graphLines);
                    }
                    case "loot" -> {
                        blocks.add(acquireLines);
                        blocks.add(graphLines);
                        blocks.add(jeiLines);
                        blocks.add(questFactLines);
                    }
                    case "balanced" -> {
                        blocks.add(jeiLines);
                        blocks.add(acquireLines);
                        blocks.add(graphLines);
                        blocks.add(questFactLines);
                    }
                    default -> { // craft
                        blocks.add(jeiLines);
                        blocks.add(acquireLines);
                        blocks.add(graphLines);
                        blocks.add(questFactLines); // quest last
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
                replySources = ReplySources.build(
                        hasJei, !questHits.isEmpty(), localScripts, acquireUsed, webUsed, lang);
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
                        jeiSummary,
                        prior,
                        lang
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
                return withSideQuests(body, allQuests, question, heldItemId, hotbarIds, offline, false, lang);
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
                return withSideQuests(plain, allQuests, question, heldItemId, hotbarIds, offline, override, lang);
            }

            if (hasJei) {
                return withSideQuests(
                        jeiSummary + "\n\n" + ReplyLang.sourceHeader(lang) + "JEI",
                        allQuests, question, heldItemId, hotbarIds, offline, override, lang);
            }

            List<String> acquireOffline = idx.acquireFactsFor(heldItemId, lang);
            if (!acquireOffline.isEmpty()) {
                return withSideQuests(
                        String.join("\n", acquireOffline) + "\n\n"
                                + ReplyLang.sourceHeader(lang)
                                + ReplyLang.labelAcquireOffline(lang),
                        allQuests, question, heldItemId, hotbarIds, offline, override, lang);
            }

            if (offline && !override && !allQuests.isEmpty()) {
                QuestGuide.MatchResult side = QuestGuide.matchForOfflineResult(
                        allQuests, question, heldItemId, hotbarIds);
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
                if (f.contains("-[recipe_needs]->") && f.startsWith(needle + " ")) {
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
            boolean offline,
            boolean override,
            String replyLang
    ) {
        if (override || allQuests.isEmpty()) {
            return AskResult.text(ReplySources.ensure(body, List.of(), replyLang));
        }
        QuestGuide.MatchResult side = offline
                ? QuestGuide.matchForOfflineResult(allQuests, question, heldItemId, hotbar)
                : QuestGuide.matchResult(allQuests, question, heldItemId, hotbar);
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

    private static String cacheKey(Path gameDir, List<String> modIds) {
        String dir = gameDir == null ? "" : gameDir.toAbsolutePath().toString();
        return dir + "|" + String.join(",", modIds);
    }
}

package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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
        ItemRef held = heldItem == null ? ItemRef.NONE : heldItem;
        List<ItemRef> hotbarRefs = hotbarItems == null ? List.of() : hotbarItems;
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

            String mode = PackAiConfig.resolvedMode();
            boolean offline = "offline".equals(mode);
            boolean override = QuestGuide.isOverride(question, questOverrideFlag);

            List<QuestGuide.Hit> allQuests = List.of();
            QuestGuide.MatchResult questMatch = new QuestGuide.MatchResult(List.of(), 0);
            if (!override) {
                allQuests = QuestGuide.index(gameDir, scanners);
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
                        questHits, qConflict, localPlain, questMatch.totalMatched(), true);
                return AskResult.of(guide, questHits);
            }

            boolean packTouched = idx.touchesFocus(focus, heldItemId) || !retrieved.snippets().isEmpty();
            String policy = packTouched ? "local_only" : "online_ok";

            String plain = Plainify.plainify(retrieved.snippets(), retrieved.sources());
            boolean hasJei = jeiSummary != null && !jeiSummary.isBlank();
            if (plain != null && retrieved.highConfidence() && questHits.isEmpty() && !hasJei) {
                // Local script match only when JEI has nothing better.
                return withSideQuests(plain, allQuests, question, heldItemId, hotbarIds, offline, override);
            }

            String llmAnswer = null;
            if (!offline) {
                List<String> facts = new ArrayList<>();
                if (jeiSummary != null && !jeiSummary.isBlank()) {
                    facts.add(jeiSummary);
                }
                facts.addAll(retrieved.graphFacts());
                for (QuestGuide.Hit h : questHits) {
                    if (facts.size() >= 24) {
                        break;
                    }
                    String title = Plainify.humanizeText(h.title() == null ? "" : h.title());
                    String desc = Plainify.humanizeText(h.description() == null ? "" : h.description());
                    facts.add("任務書：「" + title + "」" + (desc.isBlank() ? "" : " — " + desc));
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
                        jeiSummary
                );
            }
            if (llmAnswer != null && !llmAnswer.isBlank()) {
                String body = override
                        ? "【注意：已略過任務書導引（你表示任務可能有誤）】\n" + llmAnswer
                        : llmAnswer;
                if (override) {
                    return AskResult.text(body);
                }
                if (!questHits.isEmpty()) {
                    return AskResult.of(body, questHits);
                }
                return withSideQuests(body, allQuests, question, heldItemId, hotbarIds, offline, false);
            }

            if (!questHits.isEmpty() && !override) {
                String localPlain = qConflict
                        ? Plainify.plainify(retrieved.snippets(), retrieved.sources())
                        : null;
                return AskResult.of(
                        QuestGuide.formatGuide(
                                questHits, qConflict, localPlain, questMatch.totalMatched(), offline)
                                + (offline ? "" : "\n\n提示：AI 未回覆，以上為任務書摘要。"),
                        questHits
                );
            }

            if (plain != null) {
                return withSideQuests(plain, allQuests, question, heldItemId, hotbarIds, offline, override);
            }

            if (hasJei) {
                return withSideQuests(
                        jeiSummary + "\n\n【來源】JEI",
                        allQuests, question, heldItemId, hotbarIds, offline, override);
            }

            if (offline && !override && !allQuests.isEmpty()) {
                QuestGuide.MatchResult side = QuestGuide.matchForOfflineResult(
                        allQuests, question, heldItemId, hotbarIds);
                if (!side.hits().isEmpty()) {
                    return AskResult.of(
                            QuestGuide.formatGuide(side.hits(), false, null, side.totalMatched(), true)
                                    + "\n\n提示：目前為 offline 模式，以上為任務書內容（未呼叫 LLM）。",
                            side.hits()
                    );
                }
            }

            String tip = offline
                    ? "\n\n提示：目前為 offline 模式（不呼叫 LLM）。未找到可顯示的任務內容；可改 llm.mode 或確認已安裝 FTB Quests／Heracles。"
                    : "\n\n提示：在 Mods → Packai 設定 llm.mode 與 API key，或安裝並啟動 Ollama。";
            return AskResult.text(Plainify.friendlyOffline(retrieved.sources(), question) + tip);
        }
    }

    private static AskResult withSideQuests(
            String body,
            List<QuestGuide.Hit> allQuests,
            String question,
            String heldItemId,
            List<String> hotbar,
            boolean offline,
            boolean override
    ) {
        if (override || allQuests.isEmpty()) {
            return AskResult.text(body);
        }
        QuestGuide.MatchResult side = offline
                ? QuestGuide.matchForOfflineResult(allQuests, question, heldItemId, hotbar)
                : QuestGuide.matchResult(allQuests, question, heldItemId, hotbar);
        if (side.hits().isEmpty()) {
            return AskResult.text(body);
        }
        return AskResult.of(
                body + "\n\n" + QuestGuide.formatGuide(side.hits(), false, null, side.totalMatched(), offline),
                side.hits()
        );
    }

    private static String cacheKey(Path gameDir, List<String> modIds) {
        String dir = gameDir == null ? "" : gameDir.toAbsolutePath().toString();
        return dir + "|" + String.join(",", modIds);
    }
}

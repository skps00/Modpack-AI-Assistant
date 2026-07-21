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
            String heldItemId,
            List<String> hotbarItemIds,
            boolean questOverrideFlag
    ) {
        List<String> mods = modIds == null ? List.of() : modIds;
        List<String> hotbar = hotbarItemIds == null ? List.of() : hotbarItemIds;
        List<String> scanners = ModScanners.active(mods);
        List<String> focus = ModScanners.focusMods(mods, heldItemId, question, hotbar);
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
                        ? QuestGuide.matchForOfflineResult(allQuests, question, heldItemId, hotbar)
                        : QuestGuide.matchResult(allQuests, question, heldItemId, hotbar);
            }
            List<QuestGuide.Hit> questHits = questMatch.hits();

            PackIndex.RetrieveResult retrieved = idx.retrieve(question, heldItemId, focus, hotbar);
            boolean qConflict = QuestGuide.conflict(questHits, retrieved.removedItems());

            if (!questHits.isEmpty() && !override) {
                String localPlain = null;
                if (qConflict) {
                    localPlain = Plainify.plainify(retrieved.snippets(), retrieved.sources());
                }
                String guide = QuestGuide.formatGuide(
                        questHits, qConflict, localPlain, questMatch.totalMatched(), offline);
                return AskResult.of(guide, questHits);
            }

            boolean packTouched = idx.touchesFocus(focus, heldItemId) || !retrieved.snippets().isEmpty();
            String policy = packTouched ? "local_only" : "online_ok";

            String plain = Plainify.plainify(retrieved.snippets(), retrieved.sources());
            if (plain != null && retrieved.highConfidence()) {
                return withSideQuests(plain, allQuests, question, heldItemId, hotbar, offline, override);
            }

            String llmAnswer = null;
            if (!offline) {
                List<String> facts = new ArrayList<>(retrieved.graphFacts());
                llmAnswer = llm.ask(
                        question,
                        heldItemId,
                        focus,
                        facts,
                        retrieved.sources(),
                        policy,
                        override,
                        qConflict
                );
            }
            if (llmAnswer != null && !llmAnswer.isBlank()) {
                String body = override
                        ? "【注意：已略過任務書導引（你表示任務可能有誤）】\n" + llmAnswer
                        : llmAnswer;
                if (override) {
                    return AskResult.text(body);
                }
                return withSideQuests(body, allQuests, question, heldItemId, hotbar, offline, false);
            }

            if (plain != null) {
                return withSideQuests(plain, allQuests, question, heldItemId, hotbar, offline, override);
            }

            if (offline && !override && !allQuests.isEmpty()) {
                QuestGuide.MatchResult side = QuestGuide.matchForOfflineResult(
                        allQuests, question, heldItemId, hotbar);
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

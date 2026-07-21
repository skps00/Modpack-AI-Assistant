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
        idx.build(gameDir, ModScanners.active(modIds));
    }

    public AskResult ask(
            String question,
            Path gameDir,
            List<String> modIds,
            String heldItemId,
            boolean questOverrideFlag
    ) {
        List<String> mods = modIds == null ? List.of() : modIds;
        List<String> scanners = ModScanners.active(mods);
        List<String> focus = ModScanners.focusMods(mods, heldItemId, question);
        String key = cacheKey(gameDir, mods);
        PackIndex idx = indexes.computeIfAbsent(key, k -> new PackIndex());
        if (idx.paths().isEmpty()) {
            idx.build(gameDir, scanners);
        }

        String mode = PackAiConfig.resolvedMode();
        boolean offline = "offline".equals(mode);
        boolean override = QuestGuide.isOverride(question, questOverrideFlag);

        List<QuestGuide.Hit> allQuests = List.of();
        List<QuestGuide.Hit> questHits = List.of();
        if (!override) {
            allQuests = QuestGuide.index(gameDir, scanners);
            questHits = offline
                    ? QuestGuide.matchForOffline(allQuests, question, heldItemId)
                    : QuestGuide.match(allQuests, question, heldItemId);
        }

        PackIndex.RetrieveResult retrieved = idx.retrieve(question, heldItemId, focus);
        boolean qConflict = QuestGuide.conflict(questHits, retrieved.removedItems());

        if (!questHits.isEmpty() && !override) {
            String localPlain = null;
            if (qConflict) {
                localPlain = Plainify.plainify(retrieved.snippets(), retrieved.sources());
            }
            String guide = QuestGuide.formatGuide(questHits, qConflict, localPlain, questHits.size(), offline);
            return AskResult.of(guide, questHits);
        }

        boolean packTouched = idx.touchesFocus(focus, heldItemId) || !retrieved.snippets().isEmpty();
        String policy = packTouched ? "local_only" : "online_ok";

        String plain = Plainify.plainify(retrieved.snippets(), retrieved.sources());
        if (plain != null && retrieved.highConfidence()) {
            if (!allQuests.isEmpty()) {
                List<QuestGuide.Hit> side = offline
                        ? QuestGuide.matchForOffline(allQuests, question, heldItemId)
                        : QuestGuide.match(allQuests, question, heldItemId);
                if (!side.isEmpty()) {
                    return AskResult.of(
                            plain + "\n\n" + QuestGuide.formatGuide(side, false, null, side.size(), true),
                            side
                    );
                }
            }
            return AskResult.text(plain);
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
            // Still attach matching quests for click-open when not override
            if (!override && !allQuests.isEmpty()) {
                List<QuestGuide.Hit> side = QuestGuide.match(allQuests, question, heldItemId);
                if (!side.isEmpty()) {
                    return AskResult.of(body + "\n\n" + QuestGuide.formatGuide(side, false, null, side.size(), false), side);
                }
            }
            return AskResult.text(body);
        }

        if (plain != null) {
            if (!allQuests.isEmpty()) {
                List<QuestGuide.Hit> side = QuestGuide.matchForOffline(allQuests, question, heldItemId);
                if (!side.isEmpty()) {
                    return AskResult.of(
                            plain + "\n\n" + QuestGuide.formatGuide(side, false, null, side.size(), true),
                            side
                    );
                }
            }
            return AskResult.text(plain);
        }

        if (offline && !override && !allQuests.isEmpty()) {
            List<QuestGuide.Hit> side = QuestGuide.matchForOffline(allQuests, question, heldItemId);
            if (!side.isEmpty()) {
                return AskResult.of(
                        QuestGuide.formatGuide(side, false, null, side.size(), true)
                                + "\n\n提示：目前為 offline 模式，以上為任務書內容（未呼叫 LLM）。",
                        side
                );
            }
        }

        String tip = offline
                ? "\n\n提示：目前為 offline 模式（不呼叫 LLM）。未找到可顯示的任務內容；可改 llm.mode 或確認已安裝 FTB Quests／Heracles。"
                : "\n\n提示：在 Mods → Packai 設定 llm.mode 與 API key，或安裝並啟動 Ollama。";
        return AskResult.text(Plainify.friendlyOffline(retrieved.sources(), question) + tip);
    }

    private static String cacheKey(Path gameDir, List<String> modIds) {
        String dir = gameDir == null ? "" : gameDir.toAbsolutePath().toString();
        return dir + "|" + String.join(",", modIds);
    }
}

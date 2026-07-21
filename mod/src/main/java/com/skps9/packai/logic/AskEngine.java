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

    public String ask(
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

        boolean override = QuestGuide.isOverride(question, questOverrideFlag);
        List<QuestGuide.Hit> questHits = override
                ? List.of()
                : QuestGuide.indexAndMatch(gameDir, scanners, question, heldItemId);

        PackIndex.RetrieveResult retrieved = idx.retrieve(question, heldItemId, focus);
        boolean qConflict = QuestGuide.conflict(questHits, retrieved.removedItems());

        if (!questHits.isEmpty() && !override) {
            String localPlain = null;
            if (qConflict) {
                localPlain = Plainify.plainify(retrieved.snippets(), retrieved.sources());
            }
            return QuestGuide.formatGuide(questHits, qConflict, localPlain, questHits.size());
        }

        boolean packTouched = idx.touchesFocus(focus, heldItemId) || !retrieved.snippets().isEmpty();
        String policy = packTouched ? "local_only" : "online_ok";

        String plain = Plainify.plainify(retrieved.snippets(), retrieved.sources());
        if (plain != null && retrieved.highConfidence()) {
            return plain;
        }

        String mode = PackAiConfig.resolvedMode();
        String llmAnswer = null;
        if (!"offline".equals(mode)) {
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
            if (override) {
                return "【注意：已略過任務書導引（你表示任務可能有誤）】\n" + llmAnswer;
            }
            return llmAnswer;
        }

        if (plain != null) {
            return plain;
        }
        String tip = "offline".equals(mode)
                ? "\n\n提示：目前為 offline 模式（不呼叫 LLM）。若要 AI 回答，請把 llm.mode 改成 auto / cloud / ollama。"
                : "\n\n提示：在 Mods → Packai 設定 llm.mode 與 API key，或安裝並啟動 Ollama。";
        return Plainify.friendlyOffline(retrieved.sources(), question) + tip;
    }

    private static String cacheKey(Path gameDir, List<String> modIds) {
        String dir = gameDir == null ? "" : gameDir.toAbsolutePath().toString();
        return dir + "|" + String.join(",", modIds);
    }
}

package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.skps9.packai.config.PackAiConfig;

/**
 * Per-ask / per-round tool-offer gate. Drops a capable tool only on a
 * <strong>provable</strong> local-index zero-hit (or same-args empty retry).
 * Pure logic aside from index adapters — callable from no-Minecraft checks.
 */
public final class AskToolOfferGate {
    /** Machine-readable drop reason prefix. */
    public static final String REASON_NO_INDEX = "no_index_hit:";
    public static final String REASON_NO_RETRY = "no_retry:";

    /**
     * Tri-state index probes. {@code null} = unknown (not loaded) → keep tool.
     * {@code Boolean.FALSE} = proven zero → may drop. {@code Boolean.TRUE} = hit.
     */
    public interface Indices {
        Boolean hasConsumeUse(String itemId);

        Boolean hasQuestHit(String itemId, String question);

        Boolean hasAcquireRoute(String itemId);
    }

    /** One keep/drop decision for trace + tests. */
    public record Decision(String tool, boolean offered, String reason) {
        public Decision {
            tool = tool == null ? "" : tool;
            reason = reason == null ? "" : reason;
        }

        /** {@code tool:reason} for dropped rows; empty when offered. */
        public String droppedTag() {
            if (offered || reason.isBlank()) {
                return "";
            }
            return tool + ":" + reason;
        }
    }

    private final LinkedHashSet<String> noRetryKeys = new LinkedHashSet<>();
    private final ArrayList<Decision> decisions = new ArrayList<>();
    private List<Decision> lastRound = List.of();

    /**
     * Start from {@code capable} (normally {@link AskToolLoop#CAPABLE_TOOLS}) and
     * remove only proven-empty / no-retry tools.
     */
    public Set<String> offer(String question, String focusItemId, Indices indices, Collection<String> capable) {
        decisions.clear();
        String q = question == null ? "" : question;
        String focus = focusItemId == null ? "" : focusItemId.trim();
        Indices idx = indices == null ? UNKNOWN : indices;
        LinkedHashSet<String> out = new LinkedHashSet<>();
        List<String> base = capable == null || capable.isEmpty()
                ? AskToolLoop.CAPABLE_TOOLS
                : List.copyOf(capable);
        ArrayList<Decision> round = new ArrayList<>();
        for (String tool : base) {
            if (tool == null || tool.isBlank()) {
                continue;
            }
            String drop = dropReason(tool, focus, q, idx);
            if (drop == null) {
                out.add(tool);
                Decision d = new Decision(tool, true, "");
                round.add(d);
                decisions.add(d);
            } else {
                Decision d = new Decision(tool, false, drop);
                round.add(d);
                decisions.add(d);
            }
        }
        lastRound = List.copyOf(round);
        return Set.copyOf(out);
    }

    /** Record an empty tool return; same (tool, argsKey) stays out for this ask. */
    public void noRetry(String toolName, String argsKey) {
        String t = toolName == null ? "" : toolName.trim();
        String k = argsKey == null ? "" : argsKey;
        if (t.isEmpty()) {
            return;
        }
        noRetryKeys.add(t + "\0" + k);
    }

    public List<Decision> decisions() {
        return List.copyOf(decisions);
    }

    public List<Decision> lastRoundDecisions() {
        return lastRound;
    }

    public List<String> lastDroppedTags() {
        ArrayList<String> out = new ArrayList<>();
        for (Decision d : lastRound) {
            String tag = d.droppedTag();
            if (!tag.isBlank()) {
                out.add(tag);
            }
        }
        return List.copyOf(out);
    }

    public List<String> lastOffered() {
        ArrayList<String> out = new ArrayList<>();
        for (Decision d : lastRound) {
            if (d.offered()) {
                out.add(d.tool());
            }
        }
        return List.copyOf(out);
    }

    /**
     * Miss-note style lines for tools dropped this round on proven index miss
     * (not no-retry). Includes a {@code 未收錄} marker when any index drop fired.
     */
    public List<String> deterministicFactLines(String focusItemId) {
        String id = focusItemId == null ? "" : focusItemId;
        ArrayList<String> lines = new ArrayList<>();
        boolean anyIndexDrop = false;
        for (Decision d : lastRound) {
            if (d.offered() || !d.reason().startsWith(REASON_NO_INDEX)) {
                continue;
            }
            anyIndexDrop = true;
            String note = LlmClient.toolMissNote(d.tool(), id, false);
            if (note != null && !note.isBlank()) {
                lines.add(note);
            }
        }
        if (anyIndexDrop) {
            lines.add("未收錄（本地索引無命中: " + id + "）");
        }
        return List.copyOf(lines);
    }

    /** Stable args key for no-retry (item|query|dump). */
    public static String argsKey(String item, String query, String dumpLevel) {
        String i = item == null ? "" : item.trim().toLowerCase(Locale.ROOT);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String d = dumpLevel == null ? "" : dumpLevel.trim().toUpperCase(Locale.ROOT);
        return i + "|" + q + "|" + d;
    }

    public static String argsKeyFromJson(String argumentsJson) {
        String item = field(argumentsJson, "item");
        String query = field(argumentsJson, "query");
        String dump = field(argumentsJson, "dump_level");
        return argsKey(item, query, dump);
    }

    private String dropReason(String tool, String focus, String question, Indices idx) {
        // Exact (tool, argsKey) only — INFO empty must not block OUTPUT probe.
        String focusKey = argsKey(focus, "", "");
        if (noRetryKeys.contains(tool + "\0" + focusKey)) {
            return REASON_NO_RETRY + tool;
        }
        if ("consume_use".equals(tool)) {
            if (focus.isBlank()) {
                return null;
            }
            Boolean hit = idx.hasConsumeUse(focus);
            if (hit != null && !hit) {
                return REASON_NO_INDEX + "consume_use";
            }
            return null;
        }
        if ("quest_fetch".equals(tool)) {
            if (focus.isBlank()) {
                return null;
            }
            Boolean hit = idx.hasQuestHit(focus, question);
            if (hit != null && !hit) {
                return REASON_NO_INDEX + "quest_fetch";
            }
            return null;
        }
        if ("acquire".equals(tool)) {
            if (focus.isBlank()) {
                return null;
            }
            Boolean hit = idx.hasAcquireRoute(focus);
            if (hit != null && !hit) {
                return REASON_NO_INDEX + "acquire";
            }
            return null;
        }
        // jei_lookup and every other tool: keep (probing is legitimate).
        return null;
    }

    private static String field(String json, String key) {
        if (json == null || json.isBlank() || key == null) {
            return "";
        }
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) {
            return "";
        }
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) {
            return "";
        }
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) {
            return "";
        }
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) {
            return "";
        }
        return json.substring(q1 + 1, q2);
    }

    /** All probes unknown — never hard-drops on index. */
    public static final Indices UNKNOWN = new Indices() {
        @Override
        public Boolean hasConsumeUse(String itemId) {
            return null;
        }

        @Override
        public Boolean hasQuestHit(String itemId, String question) {
            return null;
        }

        @Override
        public Boolean hasAcquireRoute(String itemId) {
            return null;
        }
    };

    /** Fixed answers for offline checks / negative control. */
    public static Indices fixed(Boolean consume, Boolean quest, Boolean acquire) {
        return new Indices() {
            @Override
            public Boolean hasConsumeUse(String itemId) {
                return consume;
            }

            @Override
            public Boolean hasQuestHit(String itemId, String question) {
                return quest;
            }

            @Override
            public Boolean hasAcquireRoute(String itemId) {
                return acquire;
            }
        };
    }

    /**
     * Live adapter over {@link AskToolEnv} / gameDir indexes. Caches per ask.
     * Returns {@code null} probes when an index is not loaded yet (keep tool).
     */
    public static final class LiveIndices implements Indices {
        private final PackIndex packIndex;
        private final Path gameDir;
        private final List<String> scanners;
        private final String lang;
        private final List<String> variantKeys;

        private Boolean consumeReady;
        private List<QuestGuide.Hit> questAll;
        private final java.util.HashMap<String, Boolean> consumeCache = new java.util.HashMap<>();
        private final java.util.HashMap<String, Boolean> questCache = new java.util.HashMap<>();
        private final java.util.HashMap<String, Boolean> acquireCache = new java.util.HashMap<>();

        public LiveIndices(
                PackIndex packIndex,
                Path gameDir,
                List<String> scanners,
                String lang,
                List<String> variantKeys
        ) {
            this.packIndex = packIndex;
            this.gameDir = gameDir;
            this.scanners = scanners == null ? List.of() : List.copyOf(scanners);
            this.lang = lang == null || lang.isBlank() ? "zh_tw" : lang;
            this.variantKeys = variantKeys == null ? List.of() : List.copyOf(variantKeys);
        }

        public static LiveIndices fromEnv(AskToolEnv env, AskLoopState state) {
            PackIndex idx = env == null ? null : env.index;
            Path dir = state == null ? null : state.gameDir();
            if (dir == null && env != null) {
                dir = env.gameDir;
            }
            List<String> scanners = state == null ? List.of() : state.scanners();
            if ((scanners == null || scanners.isEmpty()) && env != null) {
                scanners = env.scanners;
            }
            String lang = state == null ? "zh_tw" : state.lang();
            List<String> keys = state == null ? List.of() : state.variantKeys();
            return new LiveIndices(idx, dir, scanners, lang, keys);
        }

        @Override
        public Boolean hasConsumeUse(String itemId) {
            String id = norm(itemId);
            if (id.isEmpty()) {
                return null;
            }
            Boolean cached = consumeCache.get(id);
            if (cached != null) {
                return cached;
            }
            ensureConsume();
            if (consumeReady == null) {
                return null;
            }
            if (!consumeReady) {
                consumeCache.put(id, Boolean.FALSE);
                return Boolean.FALSE;
            }
            boolean hit = ItemConsumeUseFacts.hasEntry(id);
            consumeCache.put(id, hit);
            return hit;
        }

        @Override
        public Boolean hasQuestHit(String itemId, String question) {
            String id = norm(itemId);
            if (id.isEmpty()) {
                return null;
            }
            // Index membership by item id / display tokens — not MatchResult score
            // (score can miss while formatGuide still had hits historically).
            Boolean cached = questCache.get(id);
            if (cached != null) {
                return cached;
            }
            if (gameDir == null) {
                return null;
            }
            try {
                if (questAll == null) {
                    // filterHidden=false: spoiler config must not invent "proven zero".
                    questAll = QuestGuide.index(gameDir, scanners, lang, false);
                }
                boolean hit = questIndexMentions(questAll, id);
                questCache.put(id, hit);
                return hit;
            } catch (Throwable t) {
                return null;
            }
        }

        private static boolean questIndexMentions(List<QuestGuide.Hit> all, String id) {
            if (all == null || all.isEmpty() || id.isEmpty()) {
                return false;
            }
            String needle = id.toLowerCase(Locale.ROOT);
            for (QuestGuide.Hit h : all) {
                if (h == null) {
                    continue;
                }
                if (h.items() != null) {
                    for (String it : h.items()) {
                        if (it != null && it.toLowerCase(Locale.ROOT).equals(needle)) {
                            return true;
                        }
                    }
                }
                if (containsFullId(h.description(), needle)
                        || containsFullId(h.title(), needle)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean containsFullId(String text, String fullId) {
            return text != null && !text.isBlank()
                    && text.toLowerCase(Locale.ROOT).contains(fullId);
        }

        @Override
        public Boolean hasAcquireRoute(String itemId) {
            String id = norm(itemId);
            if (id.isEmpty()) {
                return null;
            }
            Boolean cached = acquireCache.get(id);
            if (cached != null) {
                return cached;
            }
            if (packIndex == null) {
                return null;
            }
            try {
                List<String> loose = packIndex.acquireFactsFor(id, lang, variantKeys);
                if (loose != null && !loose.isEmpty()) {
                    acquireCache.put(id, Boolean.TRUE);
                    return Boolean.TRUE;
                }
                if (PackAiConfig.scanModJars()) {
                    if (!JarLightIndex.INSTANCE.isReady()) {
                        // Jars enabled but not scanned yet — cannot prove zero.
                        return null;
                    }
                    for (String code : JarLightIndex.INSTANCE.routeLinesForItem(id)) {
                        if (code != null && !AcquireAskTool.droppedJarRoute(code)) {
                            acquireCache.put(id, Boolean.TRUE);
                            return Boolean.TRUE;
                        }
                    }
                    List<String> wg = WorldgenIndex.routesForItem(id, gameDir);
                    if (wg != null && !wg.isEmpty()) {
                        acquireCache.put(id, Boolean.TRUE);
                        return Boolean.TRUE;
                    }
                }
                acquireCache.put(id, Boolean.FALSE);
                return Boolean.FALSE;
            } catch (Throwable t) {
                return null;
            }
        }

        private void ensureConsume() {
            if (consumeReady != null) {
                return;
            }
            if (gameDir == null) {
                consumeReady = ItemConsumeUseFacts.isLoaded() ? Boolean.TRUE : null;
                return;
            }
            try {
                ItemConsumeUseFacts.ensure(gameDir);
                consumeReady = Boolean.TRUE;
            } catch (Throwable t) {
                consumeReady = null;
            }
        }

        private static String norm(String itemId) {
            return itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
        }
    }
}

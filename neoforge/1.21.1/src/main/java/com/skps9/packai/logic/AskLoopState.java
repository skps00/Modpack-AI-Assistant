package com.skps9.packai.logic;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Per-Ask bag (eng-review R2-state). Wall clock starts at Ask click ({@link #deadlineMs}).
 * No Minecraft types so {@code -ea} loop checks stay headless.
 */
public final class AskLoopState {
    public enum Intent {
        PURPOSE,
        CRAFT,
        OBTAIN
    }

    public static final String NOISE_MARK = "[LOOT_NOISE]";

    private LongSupplier clock = System::currentTimeMillis;
    private long deadlineMs;
    private Intent intent = Intent.PURPOSE;
    private String itemId = "";
    private String dumpLevel = "SLIM";
    private List<String> variantKeys = List.of();
    private String question = "";
    private String lang = "zh_tw";
    private Path gameDir;
    private List<String> scanners = List.of();

    private int llmRounds;
    private int localTools;
    private int groundingLookups;
    private boolean skipLlm;
    private boolean missPin;
    private boolean escalate;

    private final LinkedHashSet<String> ran = new LinkedHashSet<>();
    private final LinkedHashMap<String, String> results = new LinkedHashMap<>();
    private final ArrayList<String> trace = new ArrayList<>();

    private String jeiText = "";
    /** Pass 2 station template in last jei_lookup — not other-variant dump. */
    private boolean jeiStationTemplate;
    private String acquireText = "";
    private String guideText = "";
    private String questText = "";
    private String consumeText = "";

    public static AskLoopState start(String question, String itemId, List<String> keys, long deadlineMs) {
        AskLoopState s = new AskLoopState();
        s.question = question == null ? "" : question;
        s.itemId = itemId == null ? "" : itemId;
        s.variantKeys = keys == null || keys.isEmpty() ? List.of() : List.copyOf(keys);
        s.deadlineMs = deadlineMs;
        return s;
    }

    public void setClock(LongSupplier clock) {
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    public long now() {
        return clock.getAsLong();
    }

    public long deadlineMs() {
        return deadlineMs;
    }

    public void setDeadlineMs(long deadlineMs) {
        this.deadlineMs = deadlineMs;
    }

    public long remainingWallMs() {
        return Math.max(0L, deadlineMs - now());
    }

    public boolean wallExpired() {
        return remainingWallMs() <= 0L;
    }

    /** HTTP timeout = min(90s, remaining wall); 1ms floor so expired asks fail fast. */
    public Duration httpTimeout() {
        long rem = remainingWallMs();
        if (rem <= 0L) {
            return Duration.ofMillis(1L);
        }
        return Duration.ofMillis(Math.min(AskToolLoop.WALL_MS, rem));
    }

    public Intent intent() {
        return intent;
    }

    public void setIntent(Intent intent) {
        this.intent = intent == null ? Intent.PURPOSE : intent;
    }

    public String itemId() {
        return itemId;
    }

    public String dumpLevel() {
        return dumpLevel;
    }

    public void setDumpLevel(String dumpLevel) {
        this.dumpLevel = dumpLevel == null ? "" : dumpLevel;
    }

    public List<String> variantKeys() {
        return variantKeys;
    }

    public void setVariantKeys(List<String> keys) {
        this.variantKeys = keys == null || keys.isEmpty() ? List.of() : List.copyOf(keys);
    }

    public boolean hasVariantKeys() {
        return !variantKeys.isEmpty();
    }

    public String question() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question == null ? "" : question;
    }

    public String lang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang == null || lang.isBlank() ? "zh_tw" : lang.trim();
    }

    public Path gameDir() {
        return gameDir;
    }

    public void setGameDir(Path gameDir) {
        this.gameDir = gameDir;
    }

    public List<String> scanners() {
        return scanners;
    }

    public void setScanners(List<String> scanners) {
        this.scanners = scanners == null ? List.of() : List.copyOf(scanners);
    }

    public int llmRounds() {
        return llmRounds;
    }

    public void countSuccessfulLlm() {
        llmRounds++;
    }

    public boolean canLlm() {
        return llmRounds < AskToolLoop.MAX_LLM_ROUNDS && !wallExpired();
    }

    public int localTools() {
        return localTools;
    }

    public int groundingLookups() {
        return groundingLookups;
    }

    public void incGroundingLookups() {
        groundingLookups++;
    }

    public boolean skipLlm() {
        return skipLlm;
    }

    public void setSkipLlm(boolean skipLlm) {
        this.skipLlm = skipLlm;
    }

    public boolean missPin() {
        return missPin;
    }

    public void setMissPin(boolean missPin) {
        this.missPin = missPin;
    }

    public void dropMissPin() {
        this.missPin = false;
    }

    public boolean escalate() {
        return escalate;
    }

    public void setEscalate(boolean escalate) {
        this.escalate = escalate;
    }

    public String jeiText() {
        return jeiText;
    }

    public void setJeiText(String jeiText) {
        this.jeiText = jeiText == null ? "" : jeiText;
    }

    public boolean jeiStationTemplate() {
        return jeiStationTemplate;
    }

    public void setJeiStationTemplate(boolean jeiStationTemplate) {
        this.jeiStationTemplate = jeiStationTemplate;
    }

    public String acquireText() {
        return acquireText;
    }

    public void setAcquireText(String acquireText) {
        this.acquireText = acquireText == null ? "" : acquireText;
    }

    public String guideText() {
        return guideText;
    }

    public void setGuideText(String guideText) {
        this.guideText = guideText == null ? "" : guideText;
    }

    public String questText() {
        return questText;
    }

    public void setQuestText(String questText) {
        this.questText = questText == null ? "" : questText;
    }

    public String consumeText() {
        return consumeText;
    }

    public void setConsumeText(String consumeText) {
        this.consumeText = consumeText == null ? "" : consumeText;
    }

    public List<String> toolTrace() {
        return List.copyOf(trace);
    }

    public boolean alreadyRan(String fingerprint) {
        return fingerprint != null && ran.contains(fingerprint);
    }

    public String result(String fingerprint) {
        String r = results.get(fingerprint);
        return r == null ? "" : r;
    }

    /**
     * Record a shot-0 or drain result. Duplicate fingerprint is a no-op (dup abort).
     *
     * @return false if skipped as duplicate or cap/wall hit
     */
    public boolean record(String tool, String dumpLevel, List<String> keys, String result, boolean count) {
        String fp = AskToolLoop.fingerprint(tool, itemId, dumpLevel, keys);
        if (ran.contains(fp)) {
            return false;
        }
        if (count && localTools >= AskToolLoop.MAX_LOCAL_TOOLS) {
            return false;
        }
        if (wallExpired()) {
            return false;
        }
        ran.add(fp);
        String text = result == null ? "" : result;
        results.put(fp, text);
        if (count) {
            localTools++;
            trace.add(tool);
        }
        applySection(tool, text);
        return true;
    }

    public void noteShot0(String tool, String dumpLevel, List<String> keys, String result) {
        record(tool, dumpLevel, keys, result, true);
    }

    private void applySection(String tool, String text) {
        if ("jei_lookup".equals(tool)) {
            jeiText = text;
        } else if ("acquire".equals(tool)) {
            acquireText = text;
        } else if ("guide_fetch".equals(tool)) {
            guideText = text;
        } else if ("quest_fetch".equals(tool)) {
            questText = text;
        } else if ("consume_use".equals(tool)) {
            consumeText = text;
        }
    }

    /** Craft-empty: JEI blank or HonestMiss-only. Fat acquire does not fill this. */
    public boolean craftEmpty() {
        return isEmptyOrMiss(jeiText);
    }

    /** Obtain-empty: acquire blank, miss-only, or noise-only. Fat JEI does not fill this. */
    public boolean obtainEmpty() {
        return isEmptyOrMiss(acquireText) || isNoiseOnly(acquireText);
    }

    public boolean intentRelevantEmpty() {
        return switch (intent) {
            case CRAFT -> craftEmpty() && isEmptyOrMiss(guideText) && isEmptyOrMiss(questText);
            case OBTAIN -> obtainEmpty()
                    && isEmptyOrMiss(guideText)
                    && isEmptyOrMiss(questText)
                    && isEmptyOrMiss(consumeText);
            case PURPOSE -> false;
        };
    }

    public String factBlob() {
        return String.join("\n", jeiText, acquireText, guideText, questText, consumeText);
    }

    public List<String> extraFactLines() {
        List<String> out = new ArrayList<>();
        if (!isEmptyOrMiss(guideText)) {
            out.add(guideText);
        }
        if (!isEmptyOrMiss(questText)) {
            out.add(questText);
        }
        if (!isEmptyOrMiss(consumeText)) {
            out.add(consumeText);
        }
        return out;
    }

    public List<String> relatedTools() {
        return switch (intent) {
            case PURPOSE -> List.of();
            case CRAFT -> List.of("jei_lookup", "guide_fetch", "quest_fetch");
            case OBTAIN -> List.of("acquire", "guide_fetch", "quest_fetch", "consume_use");
        };
    }

    public List<String> unrunRelated() {
        List<String> out = new ArrayList<>();
        for (String name : relatedTools()) {
            List<String> keys = "jei_lookup".equals(name) ? variantKeys : List.of();
            String level = "jei_lookup".equals(name) ? dumpLevel : "";
            if ("acquire".equals(name)) {
                level = "FULL";
            }
            String fp = AskToolLoop.fingerprint(name, itemId, level, keys);
            if (!ran.contains(fp)) {
                out.add(name);
            }
        }
        return out;
    }

    static boolean isEmptyOrMiss(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("not indexed")
                || t.contains("do not invent")
                || text.contains("未索引");
    }

    static boolean isNoiseOnly(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        boolean any = false;
        for (String line : text.split("\n")) {
            if (line == null || line.isBlank()) {
                continue;
            }
            any = true;
            if (!line.contains(NOISE_MARK) && !line.toLowerCase(Locale.ROOT).contains("trivial_self")) {
                return false;
            }
        }
        return any;
    }
}

package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Plan B — intent-gated JEI/loot injection (progressive fetch), plus Hybrid tool-loop
 * ({@link AskToolLoop}) for craft/obtain empty-gate drain.
 *
 * <p>Happy path is still single-shot {@link LlmClient#ask} without a {@code tools} schema.
 * Multi-turn {@link LlmClient#completeRound} runs only after drain + one grounding hop
 * still leave the reply ungrounded.
 *
 * <p>Client decides which local lookups enter FACT by question intent, with
 * hard per-section char/line budgets. Recipe cards stay local (UI); honesty prompts
 * unchanged.
 */
public final class AskToolContext {

    /** Hard cap JEI-U / as-ingredient encyclopedia (chars). */
    public static final int MAX_JEI_USES_CHARS = 400;
    /** Craft/acquire OUTPUT section budget (chars). */
    public static final int MAX_JEI_OUTPUT_CHARS = 4000;
    /** Purpose / default slim OUTPUT (chars). */
    public static final int MAX_JEI_OUTPUT_SLIM_CHARS = 900;
    /** Catalyst section when included (chars). */
    public static final int MAX_JEI_CATALYST_CHARS = 600;
    /** Ranked acquire lines for 配方/取得. */
    public static final int MAX_ACQUIRE_LINES_FULL = 12;
    /** Minimal obtain summary for purpose / default. */
    public static final int MAX_ACQUIRE_LINES_SLIM = 3;

    private AskToolContext() {}

    /**
     * How much JEI text to pre-inject into Ask FACT.
     * Never a full U encyclopedia — USES always tiny-capped at dump time.
     */
    public enum JeiDumpLevel {
        /** No JEI R/U/C text (cards catalog may still attach). */
        NONE,
        /** Short OUTPUT + tiny USES; skip catalyst dump (machine brief separate). */
        SLIM,
        /** Budgeted OUTPUT + tiny USES + budgeted catalyst — 配方/取得. */
        OUTPUT;

        public int outputBudget() {
            return switch (this) {
                case NONE -> 0;
                case SLIM -> MAX_JEI_OUTPUT_SLIM_CHARS;
                case OUTPUT -> MAX_JEI_OUTPUT_CHARS;
            };
        }

        public int usesBudget() {
            return this == NONE ? 0 : MAX_JEI_USES_CHARS;
        }

        public int catalystBudget() {
            return this == OUTPUT ? MAX_JEI_CATALYST_CHARS : 0;
        }

        public boolean includeOutput() {
            return this == SLIM || this == OUTPUT;
        }

        public boolean includeUses() {
            return this == SLIM || this == OUTPUT;
        }

        public boolean includeCatalyst() {
            return this == OUTPUT;
        }
    }

    /**
     * Intent gate: full OUTPUT dump only for craft/acquire; else slim (or none).
     * Machine asks stay slim — machine brief is a separate FACT pin.
     */
    public static JeiDumpLevel jeiDumpLevel(String question) {
        if (question == null || question.isBlank()) {
            return JeiDumpLevel.SLIM;
        }
        if (PackIndex.isCraftOrientedQuestion(question)
                || PackIndex.isAcquireOrientedQuestion(question)) {
            return JeiDumpLevel.OUTPUT;
        }
        return JeiDumpLevel.SLIM;
    }

    /** True when ranked loot/fish/trade should keep the full ~12 acquire lines. */
    public static boolean wantsFullAcquire(String question) {
        return PackIndex.isCraftOrientedQuestion(question)
                || PackIndex.isAcquireOrientedQuestion(question);
    }

    public static int acquireLineBudget(String question) {
        return wantsFullAcquire(question) ? MAX_ACQUIRE_LINES_FULL : MAX_ACQUIRE_LINES_SLIM;
    }

    /** Keep first {@code max} non-blank lines (order preserved). */
    public static List<String> clipLines(List<String> lines, int max) {
        if (lines == null || lines.isEmpty() || max <= 0) {
            return List.of();
        }
        if (lines.size() <= max) {
            return List.copyOf(lines);
        }
        List<String> out = new ArrayList<>(max);
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            out.add(line);
            if (out.size() >= max) {
                break;
            }
        }
        return out;
    }

    public static List<String> clipAcquireLines(List<String> acquire, String question) {
        return clipLines(acquire, acquireLineBudget(question));
    }

    /** Truncate to {@code maxChars}; append ellipsis when cut. */
    public static String clipChars(String text, int maxChars) {
        if (text == null || text.isBlank() || maxChars <= 0) {
            return "";
        }
        String t = text.strip();
        if (t.length() <= maxChars) {
            return t;
        }
        int cut = Math.max(1, maxChars - 1);
        return t.substring(0, cut) + "…";
    }

    /**
     * Truncate {@code sb} from {@code from} so the tail is at most {@code maxChars}.
     * No-op when already within budget.
     */
    public static void truncateBuilderFrom(StringBuilder sb, int from, int maxChars) {
        if (sb == null || from < 0 || from >= sb.length() || maxChars <= 0) {
            return;
        }
        int len = sb.length() - from;
        if (len <= maxChars) {
            return;
        }
        int keep = Math.max(1, maxChars - 1);
        sb.setLength(from + keep);
        sb.append('…');
    }
}

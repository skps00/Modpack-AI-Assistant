package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Display-layer miss upgrade: when model prose is empty/short/denial, surface JEI lines
 * (≤3) before facts, then a one-shot retry notice. Headless-safe (no MC types).
 */
public final class AskMissFallback {
    /** Below this char count, non-empty prose still counts as miss if denial-shaped. */
    static final int SHORT_MISS_CHARS = 48;

    private AskMissFallback() {}

    /** Empty, too short, or pure denial — player would not see a real answer. */
    public static boolean isMissAnswer(String scrubbedProse) {
        if (AskReplyScrub.isVisiblyEmpty(scrubbedProse)) {
            return true;
        }
        String t = scrubbedProse.trim();
        if (t.length() > SHORT_MISS_CHARS * 4) {
            return false;
        }
        return looksLikeDenial(t);
    }

    static boolean looksLikeDenial(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("不知道")
                || t.contains("不清楚")
                || t.contains("查不到")
                || t.contains("查唔到")
                || t.contains("找不到")
                || t.contains("未找到")
                || t.contains("没有配方")
                || t.contains("沒有配方")
                || t.contains("冇配方")
                || t.contains("无法确定")
                || t.contains("無法確定")
                || t.contains("暂时不确定")
                || t.contains("暫時不確定")
                || t.contains("do not invent")
                || t.contains("no recipe")
                || t.contains("no jei")
                || t.contains("cannot find")
                || t.contains("can't find")
                || t.contains("i don't know")
                || t.contains("i do not know")
                || t.contains("unsure how")
                || t.contains("not sure how")
                || t.contains("no obtain")
                || t.contains("nothing found");
    }

    /**
     * Player-safe JEI recipe/upgrade lines from a dump (≤ {@code max}).
     * Prefers lines with {@code →} / machine I/O; skips headers and catalog noise.
     */
    public static List<String> extractJeiPlayerLines(String jeiDump, int max) {
        List<String> out = new ArrayList<>();
        if (jeiDump == null || jeiDump.isBlank() || max <= 0) {
            return out;
        }
        for (String raw : jeiDump.split("\\R", -1)) {
            if (out.size() >= max) {
                break;
            }
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty() || !AskReplyScrub.isPlayerSafeLine(line)) {
                continue;
            }
            if (isJeiMetaLine(line)) {
                continue;
            }
            if (isJeiRecipeishLine(line)) {
                out.add(line);
            }
        }
        return out;
    }

    static boolean isJeiMetaLine(String line) {
        return line.startsWith("【JEI")
                || line.startsWith("[AS_INGREDIENT]")
                || line.startsWith("[RECIPE_CARDS]")
                || line.startsWith("（有用配方")
                || line.startsWith("(useful")
                || line.contains("已完整扫描")
                || line.contains("已完整掃描")
                || line.contains("已完整掃瞄");
    }

    static boolean isJeiRecipeishLine(String line) {
        if (line.contains("→") || line.contains("->")) {
            return true;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("改造")
                || lower.contains("升级")
                || lower.contains("升級")
                || lower.contains("upgrade")
                || line.startsWith("机器")
                || line.startsWith("機器")
                || line.startsWith("Machine");
    }

    /**
     * Compose miss display body. Order: JEI lines → facts → retry notice.
     * {@code noticeReason} is the short %s for {@link ReplyLang#askMissRetry(String, String)}.
     */
    public static Result compose(
            String lang,
            List<String> jeiLines,
            List<String> facts,
            String blankFallback,
            String noticeReason
    ) {
        StringBuilder body = new StringBuilder();
        boolean hasJei = jeiLines != null && !jeiLines.isEmpty();
        if (hasJei) {
            body.append(String.join("\n", jeiLines));
        }
        String factBlock = "";
        if (facts != null && !facts.isEmpty()) {
            factBlock = AskReplyScrub.scrubPromptEcho(String.join("\n\n", facts));
            if (!AskReplyScrub.isVisiblyEmpty(factBlock)) {
                if (body.length() > 0) {
                    body.append("\n\n");
                }
                body.append(factBlock);
            } else {
                factBlock = "";
            }
        }
        if (body.length() == 0) {
            String fb = blankFallback == null ? "" : blankFallback.trim();
            if (!fb.isEmpty()) {
                body.append(fb);
            }
        }
        String notice = ReplyLang.askMissRetry(lang, noticeReason);
        if (notice != null && !notice.isBlank()) {
            if (body.length() > 0) {
                body.append('\n');
            }
            body.append(notice.trim());
        }
        String src;
        if (hasJei) {
            src = "jei+facts";
        } else if (!factBlock.isEmpty()) {
            src = "facts";
        } else {
            src = "langfallback";
        }
        return new Result(body.toString(), src);
    }

    /** Short reason codes for UI %s and structured log. */
    public static String reasonCode(String lang, boolean hadJeiDump, boolean llmEmpty, boolean llmError) {
        if (llmError) {
            return ReplyLang.askMissReason(lang, "llm_error");
        }
        if (llmEmpty) {
            return ReplyLang.askMissReason(lang, "llm_empty");
        }
        if (hadJeiDump) {
            return ReplyLang.askMissReason(lang, "model_miss");
        }
        return ReplyLang.askMissReason(lang, "jei_empty");
    }

    /** Log bucket: miss | empty | llm_error. */
    public static String fallbackReasonBucket(boolean llmError, boolean llmEmpty) {
        if (llmError) {
            return "llm_error";
        }
        if (llmEmpty) {
            return "empty";
        }
        return "miss";
    }

    public record Result(String body, String displaySrc) {}
}

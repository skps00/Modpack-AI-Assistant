package com.skps9.packai.logic;

import java.util.Locale;

/**
 * JEI hint policy for Ask — keep text consistent with recipe cards.
 * Also post-LLM quest status allowlist (canonical line inject / replace).
 * Pure helpers (no Minecraft) so unit checks can run without the game classpath.
 */
public final class AskJeiHints {
    private AskJeiHints() {}

    /** Cards present → never claim "no JEI recipes" for a focused empty target. */
    public static boolean shouldAppendNoJeiRecipes(boolean hasCards, boolean focusPresent, boolean targetEmpty) {
        return !hasCards && focusPresent && targetEmpty;
    }

    public static boolean shouldAppendJeiHintEmpty(boolean hasCards, boolean focusPresent, boolean targetEmpty) {
        return !hasCards && !focusPresent && targetEmpty;
    }

    /** True when JEI text claims no recipes / no data (or blank). */
    public static boolean isJeiAbsenceSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return true;
        }
        return looksLikeAbsenceClaim(summary);
    }

    /** True when summary already acknowledges recipe cards (avoid double-prepend). */
    public static boolean alreadyHasCardsHint(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.contains("配方卡") || text.contains("Recipe card");
    }

    /** Detect absence wording in JEI dumps or LLM reply lines. */
    public static boolean looksLikeAbsenceClaim(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String s = text;
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.contains("no showable recipes")
                || lower.contains("no jei recipe data")
                || lower.contains("no known recipe")
                || lower.contains("0 useful recipes")
                || lower.contains("no crafting recipe")
                || lower.contains("does not list")
                || lower.contains("doesn't list")
                || (lower.contains("not listed")
                        && (lower.contains("recipe") || lower.contains("jei") || lower.contains("craft")))
                || s.contains("沒有可顯示的配方")
                || s.contains("没有可显示的配方")
                || s.contains("沒有列出")
                || s.contains("没有列出")
                || s.contains("無 JEI 配方資料")
                || s.contains("无 JEI 配方资料")
                || s.contains("無JEI配方資料")
                || s.contains("无JEI配方资料")
                || s.contains("無已知配方")
                || s.contains("无已知配方")
                || s.contains("無 JEI")
                || s.contains("无 JEI")
                || s.contains("無JEI")
                || s.contains("无JEI")
                || s.contains("未持物品")
                || lower.contains("no held item")
                || s.contains("有用配方 0 筆")
                || s.contains("有用配方 0 笔")
                || ((s.contains("沒有") || s.contains("没有"))
                        && s.contains("合成配方")
                        && (s.contains("JEI") || s.contains("jei") || s.contains("列出") || s.contains("列")))
                || (s.contains("无配方") || s.contains("無配方"))
                        && (s.contains("JEI") || s.contains("jei") || s.contains("已知") || s.contains("资料")
                                || s.contains("資料") || s.contains("可显示") || s.contains("可顯示"));
    }

    /**
     * When recipe cards exist, never forward a false "no recipe" JEI claim — ground with card hint.
     * Non-empty JEI text still gets the hint prepended so LLM cannot invent "JEI has no craft".
     */
    public static String chooseJeiSummaryText(
            String replyLang, String jeiSummary, boolean hasCards, String firstCardTitle
    ) {
        if (!hasCards) {
            if (jeiSummary != null && !jeiSummary.isBlank()) {
                return jeiSummary;
            }
            return null;
        }
        String hint = ReplyLang.jeiRecipeCardsHint(replyLang, firstCardTitle == null ? "" : firstCardTitle);
        if (isJeiAbsenceSummary(jeiSummary)) {
            return hint;
        }
        if (jeiSummary != null && !jeiSummary.isBlank()) {
            if (alreadyHasCardsHint(jeiSummary)) {
                return jeiSummary;
            }
            return hint + jeiSummary;
        }
        return hint;
    }

    /**
     * Drop / neutralize LLM lines that claim no recipes / no JEI when cards are shown.
     * Preserves {@code [[item:]]} / {@code [[recipe:]]} markers and sources footer.
     */
    public static String scrubAbsenceClaimsWhenCards(String answer, boolean hasCards) {
        if (!hasCards || answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }
        String[] lines = answer.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            boolean keep = true;
            if (!trimmed.isEmpty()
                    && !trimmed.startsWith("[[")
                    && !trimmed.startsWith("{{")
                    && !trimmed.startsWith("【來源】")
                    && !trimmed.startsWith("【来源】")
                    && !trimmed.startsWith("[Sources]")
                    && looksLikeAbsenceClaim(trimmed)) {
                keep = false;
            }
            if (keep) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(line);
            }
        }
        return out.toString()
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    /**
     * Post-LLM: force allowlisted canonical quest status line into the player-visible reply.
     * Obtain-only or submit-only (exclusive). Mixed / absent → no-op.
     * Wrong quest-ish verbs → drop/replace with template (not synonym rewrite).
     */
    public static String ensureQuestStatusVisible(
            String answer, Iterable<String> acquireLines, String replyLang
    ) {
        String canonical = canonicalQuestStatusLine(acquireLines, replyLang);
        if (canonical == null || canonical.isBlank()) {
            return answer == null ? "" : answer;
        }
        boolean scrubWrong = isObtainOnlyQuestAcquire(acquireLines);
        return ensureCanonicalQuestLine(answer, canonical, scrubWrong);
    }

    /** FACT pin for LLM: same string post-inject will restore. */
    public static String questStatusFactBlock(Iterable<String> acquireLines, String replyLang) {
        String canonical = canonicalQuestStatusLine(acquireLines, replyLang);
        if (canonical == null || canonical.isBlank()) {
            return "";
        }
        return "QUEST_STATUS (copy verbatim; do not paraphrase):\n" + canonical;
    }

    /** Allowlisted status line from acquire facts, or null when mixed/absent/no title. */
    public static String canonicalQuestStatusLine(Iterable<String> acquireLines, String replyLang) {
        String lang = replyLang == null || replyLang.isBlank() ? ReplyLang.current() : replyLang;
        QuestAcquireKind k = questAcquireKind(acquireLines);
        if (k != QuestAcquireKind.OBTAIN_ONLY && k != QuestAcquireKind.SUBMIT_ONLY) {
            return null;
        }
        String title = extractQuestTitle(acquireLines, k);
        if (title == null || title.isBlank()) {
            return null;
        }
        if (k == QuestAcquireKind.OBTAIN_ONLY) {
            String line = ReplyLang.questStatusObtain(lang, title);
            return line.isBlank() ? null : line;
        }
        String line = ReplyLang.questStatusSubmit(lang, title);
        return line.isBlank() ? null : line;
    }

    /**
     * Visible quest title from exclusive obtain/submit acquire lines.
     * Accepts humanized {@code 繳交：Title} / raw {@code -[quest_submit]-> Title}.
     */
    static String extractQuestTitle(Iterable<String> acquireLines, QuestAcquireKind kind) {
        if (acquireLines == null || kind == QuestAcquireKind.NONE || kind == QuestAcquireKind.MIXED) {
            return null;
        }
        for (String line : acquireLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String s = line.trim();
            String lower = s.toLowerCase(Locale.ROOT);
            boolean obtain = s.contains("-[quest_obtain]->")
                    || s.startsWith("取得")
                    || lower.startsWith("obtain");
            boolean submit = s.contains("-[quest_submit]->")
                    || s.startsWith("繳交")
                    || s.startsWith("缴交")
                    || lower.startsWith("submit");
            if (kind == QuestAcquireKind.OBTAIN_ONLY && !obtain) {
                continue;
            }
            if (kind == QuestAcquireKind.SUBMIT_ONLY && !submit) {
                continue;
            }
            String title = stripAcquirePrefix(s);
            if (title != null && !title.isBlank()) {
                return title;
            }
        }
        return null;
    }

    static String stripAcquirePrefix(String line) {
        String s = line.trim();
        int edge = s.indexOf("-[quest_obtain]->");
        if (edge >= 0) {
            s = s.substring(edge + "-[quest_obtain]->".length()).trim();
        } else {
            edge = s.indexOf("-[quest_submit]->");
            if (edge >= 0) {
                s = s.substring(edge + "-[quest_submit]->".length()).trim();
            }
        }
        String[] prefixes = {
                "取得：", "取得:", "取得",
                "繳交：", "繳交:", "繳交",
                "缴交：", "缴交:", "缴交",
                "Obtain: ", "Obtain:", "Obtain ",
                "Submit: ", "Submit:", "Submit "
        };
        for (String p : prefixes) {
            if (s.startsWith(p)) {
                s = s.substring(p.length()).trim();
                break;
            }
        }
        if ((s.startsWith("「") && s.endsWith("」") && s.length() > 2)
                || (s.startsWith("\"") && s.endsWith("\"") && s.length() > 2)
                || (s.startsWith("'") && s.endsWith("'") && s.length() > 2)) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    /**
     * @deprecated use {@link #ensureQuestStatusVisible}; kept for older call sites/tests.
     */
    @Deprecated
    public static String scrubObtainOnlyQuestWording(String answer, boolean obtainOnlyNoSubmit) {
        if (!obtainOnlyNoSubmit) {
            return answer == null ? "" : answer;
        }
        return ensureQuestStatusVisible(answer, java.util.List.of("取得：相關任務"), "zh_tw");
    }

    static String ensureCanonicalQuestLine(String answer, String canonical, boolean scrubWrongQuestish) {
        String body = answer == null ? "" : answer;
        if (scrubWrongQuestish) {
            body = replaceWrongQuestishWithCanonical(body, canonical);
        }
        if (body.contains(canonical)) {
            return body;
        }
        if (body.isBlank()) {
            return canonical;
        }
        var m = ReplySources.HEADER.matcher(body);
        if (m.find()) {
            int at = m.start();
            String before = body.substring(0, at).stripTrailing();
            String after = body.substring(at);
            if (before.isBlank()) {
                return canonical + "\n\n" + after;
            }
            return before + "\n\n" + canonical + "\n\n" + after;
        }
        return body.stripTrailing() + "\n\n" + canonical;
    }

    /**
     * Quest-ish lines with forbidden obtain verbs → first becomes canonical; rest dropped.
     * Non-quest lines (real shops saying 兌換) untouched.
     */
    static String replaceWrongQuestishWithCanonical(String answer, String canonical) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        boolean already = answer.contains(canonical);
        boolean placed = already;
        String[] lines = answer.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (isQuestishLine(line) && hasForbiddenObtainWording(line)) {
                if (!placed) {
                    if (out.length() > 0) {
                        out.append('\n');
                    }
                    out.append(canonical);
                    placed = true;
                }
                // drop paraphrased quest line
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(line);
        }
        return out.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    /** True when acquire lines show obtain and no submit (humanized or raw edge). */
    public static boolean isObtainOnlyQuestAcquire(Iterable<String> acquireLines) {
        QuestAcquireKind k = questAcquireKind(acquireLines);
        return k == QuestAcquireKind.OBTAIN_ONLY;
    }

    /** True when acquire lines show submit and no obtain. */
    public static boolean isSubmitOnlyQuestAcquire(Iterable<String> acquireLines) {
        return questAcquireKind(acquireLines) == QuestAcquireKind.SUBMIT_ONLY;
    }

    enum QuestAcquireKind {
        NONE,
        OBTAIN_ONLY,
        SUBMIT_ONLY,
        MIXED
    }

    static QuestAcquireKind questAcquireKind(Iterable<String> acquireLines) {
        if (acquireLines == null) {
            return QuestAcquireKind.NONE;
        }
        boolean obtain = false;
        boolean submit = false;
        for (String line : acquireLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String s = line;
            String lower = s.toLowerCase(Locale.ROOT);
            if (s.contains("-[quest_obtain]->")
                    || s.startsWith("取得")
                    || lower.startsWith("obtain")) {
                obtain = true;
            }
            if (s.contains("-[quest_submit]->")
                    || s.startsWith("繳交")
                    || s.startsWith("缴交")
                    || lower.startsWith("submit")) {
                submit = true;
            }
        }
        if (obtain && submit) {
            return QuestAcquireKind.MIXED;
        }
        if (obtain) {
            return QuestAcquireKind.OBTAIN_ONLY;
        }
        if (submit) {
            return QuestAcquireKind.SUBMIT_ONLY;
        }
        return QuestAcquireKind.NONE;
    }

    static boolean isQuestishLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String s = line;
        String lower = s.toLowerCase(Locale.ROOT);
        return s.contains("任務")
                || s.contains("任务")
                || lower.contains("quest")
                || s.contains("任務書")
                || s.contains("任务书");
    }

    /** Wrong verbs for obtain-only quest context (detect only — replace whole line with allowlist). */
    static boolean hasForbiddenObtainWording(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String s = line;
        String lower = s.toLowerCase(Locale.ROOT);
        return s.contains("轉換")
                || s.contains("转换")
                || s.contains("換成")
                || s.contains("换成")
                || s.contains("換取")
                || s.contains("换取")
                || s.contains("兌換")
                || s.contains("兑换")
                || s.contains("兌")
                || s.contains("兑")
                || s.contains("繳交")
                || s.contains("缴交")
                || s.contains("提交")
                || s.contains("上交")
                || s.contains("放入")
                || lower.contains("exchange")
                || lower.contains("redeem")
                || lower.contains("convert")
                || lower.contains("hand-in")
                || lower.contains("hand in")
                || lower.contains("submit");
    }
}

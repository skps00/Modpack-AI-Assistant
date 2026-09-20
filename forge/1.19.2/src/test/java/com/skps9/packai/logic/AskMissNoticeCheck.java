package com.skps9.packai.logic;

import java.util.List;

/**
 * Miss display upgrade: JEI lines + facts + retry notice; negative control for good prose.
 * Run with -ea.
 */
public final class AskMissNoticeCheck {
    private AskMissNoticeCheck() {}

    public static void main(String[] args) {
        missWithJeiShowsLinesAndNotice();
        missWithoutJeiShowsFactsAndNotice();
        goodProseNotMiss();
        softToolMissWhenDumpPresent();
        jeiTextNotWipedByEmptyInfo();
        System.out.println("AskMissNoticeCheck OK");
    }

    static void missWithJeiShowsLinesAndNotice() {
        String dump = "【JEI 资料】item\n"
                + "铁锭 → 寰宇支配之剑\n"
                + "巫师铁砧：符文 → 寰宇支配之剑\n"
                + "另一行 → 产物";
        List<String> jei = AskMissFallback.extractJeiPlayerLines(dump, 3);
        assert jei.size() >= 2 : jei;
        assert jei.stream().anyMatch(l -> l.contains("→")) : jei;

        AskMissFallback.Result r = AskMissFallback.compose(
                "zh_tw",
                jei,
                List.of("事實一行"),
                "fallback",
                ReplyLang.askMissReason("zh_tw", "model_miss"));
        assert "jei+facts".equals(r.displaySrc()) : r.displaySrc();
        assert r.body().contains("→") : r.body();
        assert r.body().contains("事實一行") : r.body();
        String notice = ReplyLang.askMissRetry("zh_tw", "答案不完整");
        assert notice != null && notice.contains("再問一次") : notice;
        assert r.body().contains("再問一次") || r.body().contains(notice.trim()) : r.body();
        assert AskMissFallback.isMissAnswer("不知道怎么做") : "denial should be miss";
        assert AskMissFallback.isMissAnswer("") : "empty miss";
        System.out.println("missWithJeiShowsLinesAndNotice OK lines=" + jei.size());
    }

    static void missWithoutJeiShowsFactsAndNotice() {
        AskMissFallback.Result r = AskMissFallback.compose(
                "en_us",
                List.of(),
                List.of("fact A", "fact B"),
                "blank",
                ReplyLang.askMissReason("en_us", "jei_empty"));
        assert "facts".equals(r.displaySrc()) : r.displaySrc();
        assert r.body().contains("fact A") : r.body();
        assert r.body().toLowerCase().contains("ask again")
                || r.body().contains("Query incomplete") : r.body();
        assert !r.body().contains("→") : r.body();
        System.out.println("missWithoutJeiShowsFactsAndNotice OK");
    }

    static void goodProseNotMiss() {
        String prose = "1. Use the Iron Anvil to upgrade the Infinity Sword with runes.\n"
                + "2. Materials: arcane essence and a blank rune.";
        assert !AskMissFallback.isMissAnswer(prose) : "good prose must not be miss";
        String notice = ReplyLang.askMissRetry("zh_cn", "x");
        assert notice != null && notice.contains("再问一次") : notice;
        // Negative control: compose is only for miss path — caller must not append notice on prose.
        assert !prose.contains("再问一次") && !prose.contains("ask again");
        System.out.println("goodProseNotMiss OK");
    }

    static void softToolMissWhenDumpPresent() {
        String soft = LlmClient.toolMissNote("jei_lookup", "mod:x", true);
        assert soft.contains("INFO") : soft;
        assert soft.contains("唔准講") || soft.contains("OUTPUT") : soft;
        assert soft.equals(JeiLookupAskTool.softMissNoteWhenDumpPresent()) : soft;
        String hard = LlmClient.toolMissNote("jei_lookup", "mod:x", false);
        assert hard.toLowerCase().contains("do not invent") : hard;
        assert !hard.equals(soft) : "soft != hard";
        System.out.println("softToolMissWhenDumpPresent OK");
    }

    static void jeiTextNotWipedByEmptyInfo() {
        AskLoopState s = AskLoopState.start("q", "mod:sword", List.of(), System.currentTimeMillis() + 60_000);
        assert s.record("jei_lookup", "mod:sword", "OUTPUT", List.of(), "铁 → 剑\n巫师铁砧：符文 → 剑", true);
        assert s.hadNonEmptyJeiDump();
        assert !AskLoopState.isEmptyOrMiss(s.jeiText()) : s.jeiText();
        // Empty INFO must not wipe.
        assert s.record("jei_lookup", "mod:sword", "INFO", List.of(), "", true);
        assert s.hadNonEmptyJeiDump();
        assert s.jeiText().contains("→") : s.jeiText();
        System.out.println("jeiTextNotWipedByEmptyInfo OK");
    }
}

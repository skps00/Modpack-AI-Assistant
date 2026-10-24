package com.skps9.packai.logic;

import java.util.List;

/**
 * Blank miss path must not stack {@link ReplyLang#askBodyUnavailable} under {@link ReplyLang#askMissRetry}
 * when the model returned no prose ({@code llm_empty}).
 */
public final class AskBlankDoubleNoticeCheck {
    private AskBlankDoubleNoticeCheck() {}

    public static void main(String[] args) {
        blankFallbackPolicy();
        llmEmptySkipsGenericFallback();
        llmErrorKeepsGenericFallback();
        jeiOutputUnchanged();
        System.out.println("AskBlankDoubleNoticeCheck OK");
    }

    static void blankFallbackPolicy() {
        String lang = "en_us";
        String generic = ReplyLang.askBodyUnavailable(lang).trim();
        assert AskMissFallback.blankFallbackFor(lang, false, true).isEmpty();
        String nonEmpty = AskMissFallback.blankFallbackFor(lang, false, false);
        assert !nonEmpty.isEmpty() && generic.equals(nonEmpty) : nonEmpty;

        String reasonEmpty = ReplyLang.askMissReason(lang, "llm_empty");
        AskMissFallback.Result rEmpty = AskMissFallback.compose(
                lang,
                List.of(),
                List.of(),
                AskMissFallback.blankFallbackFor(lang, false, true),
                reasonEmpty);
        String noticeEmpty = ReplyLang.askMissRetry(lang, reasonEmpty).trim();
        assert !rEmpty.body().contains(generic) : rEmpty.body();
        assert rEmpty.body().trim().equals(noticeEmpty) : rEmpty.body();

        String reasonErr = ReplyLang.askMissReason(lang, "llm_error");
        AskMissFallback.Result rErr = AskMissFallback.compose(
                lang,
                List.of(),
                List.of(),
                AskMissFallback.blankFallbackFor(lang, false, false),
                reasonErr);
        String bodyErr = rErr.body();
        assert bodyErr.contains(generic) : bodyErr;
        assert bodyErr.contains(ReplyLang.askMissRetry(lang, reasonErr)) : bodyErr;
        int segments = bodyErr.split("\n", -1).length;
        assert segments == 2 : "expected 2 segments, got " + segments + ": " + bodyErr;
        System.out.println("blankFallbackPolicy OK");
    }

    static void llmEmptySkipsGenericFallback() {
        String lang = "en_us";
        String generic = ReplyLang.askBodyUnavailable(lang).trim();
        String fb = AskMissFallback.blankFallbackFor(lang, false, true);
        assert fb.isEmpty() : fb;
        String reason = ReplyLang.askMissReason(lang, "llm_empty");
        AskMissFallback.Result r = AskMissFallback.compose(lang, List.of(), List.of(), fb, reason);
        String body = r.body();
        assert !body.contains(generic) : body;
        String notice = ReplyLang.askMissRetry(lang, reason);
        assert notice != null && !notice.isBlank() : notice;
        assert body.trim().equals(notice.trim()) : body;
        System.out.println("llmEmptySkipsGenericFallback OK");
    }

    static void llmErrorKeepsGenericFallback() {
        String lang = "en_us";
        String generic = ReplyLang.askBodyUnavailable(lang).trim();
        String fb = AskMissFallback.blankFallbackFor(lang, false, false);
        assert generic.equals(fb) : fb;
        String reason = ReplyLang.askMissReason(lang, "llm_error");
        AskMissFallback.Result r = AskMissFallback.compose(lang, List.of(), List.of(), fb, reason);
        String body = r.body();
        assert body.contains(generic) : body;
        assert body.contains(ReplyLang.askMissRetry(lang, reason)) : body;
        System.out.println("llmErrorKeepsGenericFallback OK");
    }

    static void jeiOutputUnchanged() {
        String lang = "zh_tw";
        List<String> jei = List.of("铁锭 → 寰宇支配之剑", "巫师铁砧：符文 → 寰宇支配之剑");
        String reason = ReplyLang.askMissReason(lang, "llm_empty");
        String fb = AskMissFallback.blankFallbackFor(lang, false, true);
        AskMissFallback.Result r = AskMissFallback.compose(lang, jei, List.of(), fb, reason);
        String expected = String.join("\n", jei)
                + "\n"
                + ReplyLang.askMissRetry(lang, reason).trim();
        assert expected.equals(r.body()) : "got:\n" + r.body() + "\nexpected:\n" + expected;
        System.out.println("jeiOutputUnchanged OK");
    }
}

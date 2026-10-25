package com.skps9.packai.logic;

import java.util.List;

/** Body vs cards: denial swap when cards present; orphan headings on fallback only. Run with -ea. */
public final class AskBodyCardConsistencyCheck {
    private AskBodyCardConsistencyCheck() {}

    public static void main(String[] args) {
        swapDenialWhenCardsPresent();
        keepDenialWhenNoCards();
        stripOrphanHeadingOnFallback();
        keepHeadingOnNormalProse();
        zhDenialSwap();
        negativeControlCardsGuard();
        System.out.println("AskBodyCardConsistencyCheck OK");
    }

    private static void swapDenialWhenCardsPresent() {
        String lang = "en_us";
        String unk = ReplyLang.obtainUnknown(lang);
        String below = ReplyLang.cardsBelowOnly(lang);
        String body = "Before.\n" + unk + "\nAfter.";
        int sentencesBefore = AskBodyCardConsistency.countSentences(body);
        String out = AskBodyCardConsistency.reconcile(body, lang, 7, false);
        assert !out.contains(unk) : out;
        assert out.contains(below) : out;
        assert AskBodyCardConsistency.countSentences(out) == sentencesBefore : out;
        System.out.println("swapDenialWhenCardsPresent OK");
    }

    private static void keepDenialWhenNoCards() {
        for (String lang : List.of("en_us", "zh_cn", "zh_tw")) {
            String unk = ReplyLang.obtainUnknown(lang);
            String body = "Lead.\n" + unk;
            String out = AskBodyCardConsistency.reconcile(body, lang, 0, false);
            assert out.contains(unk) : lang + " " + out;
        }
        System.out.println("keepDenialWhenNoCards OK");
    }

    private static void stripOrphanHeadingOnFallback() {
        String lang = "en_us";
        String fb = ReplyLang.askBodyUnavailable(lang).trim();
        String body = "How to get:\n\n" + fb;
        String out = AskBodyCardConsistency.reconcile(body, lang, 0, true);
        assert !out.contains("How to get:") : out;
        assert out.contains(fb) : out;
        System.out.println("stripOrphanHeadingOnFallback OK");
    }

    private static void keepHeadingOnNormalProse() {
        String lang = "en_us";
        String prose = "Craft with iron and sticks at a table.";
        String body = "How to get:\n" + prose;
        String out = AskBodyCardConsistency.reconcile(body, lang, 3, false);
        assert out.contains("How to get:") && out.contains(prose) : out;
        System.out.println("keepHeadingOnNormalProse OK");
    }

    private static void zhDenialSwap() {
        for (String lang : List.of("zh_cn", "zh_tw")) {
            String unk = ReplyLang.obtainUnknown(lang);
            String below = ReplyLang.cardsBelowOnly(lang);
            String body = unk;
            String out = AskBodyCardConsistency.reconcile(body, lang, 7, false);
            assert !out.contains(unk) : lang + " " + out;
            assert out.contains(below) : lang + " " + out;
        }
        System.out.println("zhDenialSwap OK");
    }

    private static void negativeControlCardsGuard() {
        String lang = "en_us";
        String unk = ReplyLang.obtainUnknown(lang);
        String onlyUnk = unk;
        String guarded = AskBodyCardConsistency.reconcile(onlyUnk, lang, 0, false);
        assert guarded.contains(unk) : guarded;
        String unguarded = AskBodyCardConsistency.harnessSwapDenialRegardlessOfCards(onlyUnk, lang);
        assert !unguarded.contains(unk) : "simulated missing cardsOut>0 guard";
        assert !guarded.equals(unguarded) : "guard must block swap when cards=0";
        System.out.println("negativeControlCardsGuard OK");
    }
}

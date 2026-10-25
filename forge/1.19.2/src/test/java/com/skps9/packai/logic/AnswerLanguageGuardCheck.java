package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * AnswerLanguageGuard fixtures: real en_us leak, clean-text identity, zh mod-name safety,
 * blank-after-strip fallback, negative control. Run with -ea.
 */
public final class AnswerLanguageGuardCheck {
    private AnswerLanguageGuardCheck() {}

    private static final String CJK_ONLY = "\u8b93\u4f60\u4e0d\u7528\u6574\u5957\u4e5f\u80fd\u547c\u5438";

    /** Verbatim from the real en_us answer (Chinese sentence inside an English numbered step). */
    private static final String LEAK = "Giselle addon " + CJK_ONLY;

    public static void main(String[] args) {
        realLeakSentenceStripped();
        cleanEnglishUntouched();
        chineseAnswerKeepsModName();
        blankAfterStripReturnsOriginal();
        negativeControlStripDisabled();
        System.out.println("AnswerLanguageGuardCheck OK");
    }

    /** (a) the exact real sentence loses the CJK run, keeps the English around it. */
    private static void realLeakSentenceStripped() {
        String sentenceOnly = "Step 2. " + LEAK + " then restart.";
        String out = AnswerLanguageGuard.stripForeignRuns(sentenceOnly, "en_us");
        assert out.equals("Step 2. Giselle addon then restart.") : out;
        assert !hasCjk(out) : out;

        String body = "How to get:\n\n1. Install the " + LEAK + " mod and turn the patch on.\n"
                + "2. Restart the game.\n";
        String full = AnswerLanguageGuard.stripForeignRuns(body, "en_us");
        assert !hasCjk(full) : full;
        assert full.contains("1. Install the Giselle addon mod and turn the patch on.") : full;
        assert full.contains("2. Restart the game.") : full;
        assert !full.contains("  ") : "double space left: " + full;
        assert !full.contains(" ,") && !full.contains(" .") : "space-before-punct left: " + full;

        // A numbered step made only of the foreign run must not survive as a dangling "2.".
        String numbered = "How to get:\n1. Get the frame\n2. " + CJK_ONLY + "\n3. Craft it\n";
        String steps = AnswerLanguageGuard.stripForeignRuns(numbered, "en_us");
        assert steps.equals("How to get:\n1. Get the frame\n3. Craft it\n") : steps;

        // No-space cut must not fuse the surviving words.
        String glued = AnswerLanguageGuard.stripForeignRuns("Giselle addon\u8b93\u4f60\u7528in order", "en_us");
        assert glued.equals("Giselle addon in order") : glued;

        // Leading fullwidth punctuation / Comma left dangling at line start is cleaned.
        String leading = AnswerLanguageGuard.stripForeignRuns("\u8b93\u4f60\u7528, then craft it", "en_us");
        assert leading.equals("then craft it") : leading;

        // A leak that filled a bracket pair must not leave "()" behind.
        String bracketed = AnswerLanguageGuard.stripForeignRuns(
                "Use the patch (" + CJK_ONLY + ") before you start.", "en_us");
        assert bracketed.equals("Use the patch before you start.") : bracketed;
        // …nor eat a bracket pair that was already empty in clean text.
        String preEmpty = "Use the patch () with care.";
        assert AnswerLanguageGuard.stripForeignRuns(preEmpty, "en_us").equals(preEmpty) : preEmpty;
    }

    /** (b) 100% clean English is unchanged, byte for byte. */
    private static void cleanEnglishUntouched() {
        String body = "How to get:\n"
                + "1. Craft the Modular Frame (3x iron ingot, 2x stick).\n"
                + "2. Right-click the Applied Energistics controller - JEI shows 4 recipes.\n"
                + "\n"
                + "Tip: 100% of the parts drop from the Wither; no Mekanism machine needed.\n";
        String out = AnswerLanguageGuard.stripForeignRuns(body, "en_us");
        assert out.equals(body) : out;
        assert Arrays.equals(body.getBytes(StandardCharsets.UTF_8), out.getBytes(StandardCharsets.UTF_8))
                : "clean English must be byte-for-byte identical";
        // Same instance — proves no rewrite path was taken.
        assert out == body : "clean answer must be returned untouched";
        assert AnswerLanguageGuard.stripForeignRuns(body, "en_gb").equals(body) : "en_gb too";
    }

    /** (c) Chinese answers keep English mod names; only lowercase sandwiched tokens go. */
    private static void chineseAnswerKeepsModName() {
        String zh = "\u53d6\u5f97\u65b9\u5f0f\uff1a\n"
                + "1. \u5148\u505a Modular Frame\u3002\n"
                + "2. \u518d\u653e\u5165 Applied Energistics \u7684\u63a7\u5236\u5668\u3002\n"
                + "\u5efa\u8b70\u4f7f\u7528 Mekanism \u88dd\u7f6e\u3002\n";
        String zhOut = AnswerLanguageGuard.stripForeignRuns(zh, "zh_tw");
        assert zhOut.equals(zh) : "mod names must survive: " + zhOut;
        assert zhOut.contains("Applied Energistics") : zhOut;
        assert zhOut.contains("Mekanism") : zhOut;

        // Lowercase foreign filler word sandwiched between Han does get dropped …
        String leaky = "\u9019\u500b however \u8a2d\u5b9a\u8981\u95dc\u6389\u3002";
        String leakyOut = AnswerLanguageGuard.stripForeignRuns(leaky, "zh_tw");
        assert leakyOut.equals("\u9019\u500b \u8a2d\u5b9a\u8981\u95dc\u6389\u3002") : leakyOut;
        // … while short Latin tokens and namespaced ids stay.
        assert AnswerLanguageGuard.stripForeignRuns("\u9019\u500b root \u8a2d\u5b9a", "zh_cn")
                .equals("\u9019\u500b root \u8a2d\u5b9a");
        assert AnswerLanguageGuard.stripForeignRuns("\u7528 minecraft:foo \u8a2d\u5b9a", "zh_cn")
                .equals("\u7528 minecraft:foo \u8a2d\u5b9a");
        // en* bodies that are already Chinese are not the zh path's business.
        String enAsZh = "\u9019\u500b however \u8a2d\u5b9a";
        assert AnswerLanguageGuard.stripForeignRuns(enAsZh, "ja_jp").equals(enAsZh) : "non en/zh untouched";
    }

    /** (d) a body that would become blank returns the original. */
    private static void blankAfterStripReturnsOriginal() {
        String allCjk = "\u8b93\u4f60\u4e0d\u7528\u6574\u5957\u4e5f\u80fd\u547c\u5438";
        String out = AnswerLanguageGuard.stripForeignRuns(allCjk, "en_us");
        assert out.equals(allCjk) : "blank result must fall back to original: " + out;

        String numberedCjkOnly = "1. \u8b93\u4f60\u7528\n2. \u547c\u5438\n";
        String out2 = AnswerLanguageGuard.stripForeignRuns(numberedCjkOnly, "en_us");
        assert out2.equals(numberedCjkOnly) : "blank result must fall back to original: " + out2;

        assert AnswerLanguageGuard.stripForeignRuns(null, "en_us") == null;
        assert AnswerLanguageGuard.stripForeignRuns("", "en_us").isEmpty();
    }

    /** (e) negative control: with the strip disabled the CJK case must fail. */
    private static void negativeControlStripDisabled() {
        String body = "Step 2. " + LEAK + " then restart.";
        AnswerLanguageGuard.testStripDisabled = true;
        try {
            String leaked = AnswerLanguageGuard.stripForeignRuns(body, "en_us");
            assert leaked.equals(body) : leaked;
            assert hasCjk(leaked) : "negative control: leak must be visible when stripping is off";
            boolean contractHolds = !hasCjk(leaked);
            assert !contractHolds : "with stripping disabled the en_us no-CJK contract MUST fail";
        } finally {
            AnswerLanguageGuard.testStripDisabled = false;
        }
        // Re-enabled, the same body passes the contract again.
        assert !hasCjk(AnswerLanguageGuard.stripForeignRuns(body, "en_us"));
    }

    private static boolean hasCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '\u4e00' && c <= '\u9fff') || (c >= '\u3400' && c <= '\u4dbf')) {
                return true;
            }
        }
        return false;
    }
}

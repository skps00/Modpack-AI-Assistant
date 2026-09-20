package com.skps9.packai.logic;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plan A — STANDARD frame how-to-get body = deterministic craft line.
 * Run with {@code -ea}. Headings from {@link ReplyLang} only (no CJK literals).
 */
public final class FrameStandardRecipeLineCheck {
    private FrameStandardRecipeLineCheck() {}

    public static void main(String[] args) {
        try {
            installedShapelessFlags();
            installPassThrough();
            lineByType("zh_cn");
            lineByType("en_us");
            variantsClause();
            sectionReplaced("zh_cn");
            sectionReplaced("en_us");
            inlineUseHeadNotSplit("zh_cn");
            sameLineContent("zh_cn");
            noHeadingFallback();
            noPartialMarker("zh_cn");
            stubDiscriminates("zh_cn");
            stripLangMissLineCases();
            System.out.println("FrameStandardRecipeLineCheck OK");
        } catch (AssertionError e) {
            System.err.println("FAIL: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        } catch (Throwable t) {
            System.err.println("FAIL: " + t);
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static void installedShapelessFlags() {
        List<ModularFrameStandard.FrameRecipe> installed =
                ModularFrameStandard.installedExpectedRecipes();
        assert installed.size() == 4 : "installed size=" + installed.size();
        boolean[] wantShape = {true, false, false, false};
        int[] wantVar = {1, 1, 10, 1};
        for (int i = 0; i < 4; i++) {
            ModularFrameStandard.FrameRecipe r = ModularFrameStandard.recipeAt(i);
            assert r != null : "recipeAt(" + i + ")";
            assert r.shapeless() == wantShape[i]
                    : "i=" + i + " shapeless=" + r.shapeless();
            assert r.variantCount() == wantVar[i]
                    : "i=" + i + " variantCount=" + r.variantCount();
        }
        System.out.println("installedShapelessFlags OK");
    }

    static void installPassThrough() {
        ModularFrameStandard.installExpectedRecipes(List.of(
                new ModularFrameStandard.FrameRecipe(
                        Map.of("slot", "mod"), List.of("x"), "y", true, 3)));
        ModularFrameStandard.FrameRecipe r = ModularFrameStandard.recipeAt(0);
        assert r != null && r.shapeless() && r.variantCount() == 3
                : "pass-through shapeless/variantCount";
        ModularFrameStandard.installExpectedRecipes(ModularFrameStandard.tetraBlankFrameRecipes());
        System.out.println("installPassThrough OK");
    }

    static void lineByType(String lang) {
        ModularFrameStandard.FrameRecipe sword = ModularFrameStandard.recipeAt(0);
        ModularFrameStandard.FrameRecipe single = ModularFrameStandard.recipeAt(1);
        assert sword != null && single != null;
        String matsSword = HonestMiss.joinItemLabels(sword.ingredientItemIds());
        String resSword = HonestMiss.itemLabel(sword.resultItemId());
        String matsSingle = HonestMiss.joinItemLabels(single.ingredientItemIds());
        String resSingle = HonestMiss.itemLabel(single.resultItemId());
        String swordLine = HonestMiss.frameStandardRecipeLine(lang, sword);
        String singleLine = HonestMiss.frameStandardRecipeLine(lang, single);
        String swordExpected = ReplyLang.tr(lang, "packai.reply.frame_standard_recipe", matsSword, resSword);
        String singleExpected = ReplyLang.tr(
                lang, "packai.reply.frame_standard_recipe_shaped", matsSingle, resSingle);
        assert swordLine.equals(swordExpected) : lang + " sword line mismatch: " + swordLine;
        assert singleLine.equals(singleExpected) : lang + " single line mismatch: " + singleLine;
        // Wrong-key negative control: same inputs through the other key must differ.
        String swordWrong = ReplyLang.tr(lang, "packai.reply.frame_standard_recipe_shaped", matsSword, resSword);
        String singleWrong = ReplyLang.tr(lang, "packai.reply.frame_standard_recipe", matsSingle, resSingle);
        assert !swordLine.equals(swordWrong) : lang + " sword wrong-key must differ";
        assert !singleLine.equals(singleWrong) : lang + " single wrong-key must differ";
        System.out.println("lineByType OK lang=" + lang);
    }

    static void variantsClause() {
        ModularFrameStandard.FrameRecipe sword = ModularFrameStandard.recipeAt(0);
        ModularFrameStandard.FrameRecipe dbl = ModularFrameStandard.recipeAt(2);
        String swordLine = HonestMiss.frameStandardRecipeLine("en_us", sword);
        String doubleLine = HonestMiss.frameStandardRecipeLine("en_us", dbl);
        String variants = ReplyLang.tr("en_us", "packai.reply.frame_standard_recipe_variants", "10");
        assert doubleLine.contains("10") : doubleLine;
        assert doubleLine.contains(variants) : doubleLine;
        assert !swordLine.contains(variants) : swordLine;
        System.out.println("variantsClause OK");
    }

    static void sectionReplaced(String lang) {
        ModularFrameStandard.FrameRecipe sword = ModularFrameStandard.recipeAt(0);
        String line = HonestMiss.frameStandardRecipeLine(lang, sword);
        String getHead = ReplyLang.sectionHowToGet(lang);
        String useHead = ReplyLang.sectionHowToUse(lang);
        String useBody = "use-section-body-keep";
        String footer = ReplyLang.sourceHeader(lang) + "JEI";
        String neg = "NEGATE_OBTAIN_NO_PATH_MARK";
        String body = getHead + "\n" + neg + "\n\n" + useHead + "\n" + useBody + "\n\n" + footer;
        String out = AskReplyScrub.replaceHowToGetBody(body, line);
        assert !out.equals(body) : "must replace";
        String section = howToGetInner(out, lang);
        assert section.equals(line) : "inner=" + section + " want=" + line;
        assert out.contains(useHead + "\n" + useBody) : out;
        assert out.contains(footer) : out;
        assert !out.contains(neg) : out;
        System.out.println("sectionReplaced OK lang=" + lang);
    }

    static void inlineUseHeadNotSplit(String lang) {
        ModularFrameStandard.FrameRecipe sword = ModularFrameStandard.recipeAt(0);
        String line = HonestMiss.frameStandardRecipeLine(lang, sword);
        String getHead = ReplyLang.sectionHowToGet(lang);
        String useHead = ReplyLang.sectionHowToUse(lang);
        String footer = ReplyLang.sourceHeader(lang) + "JEI";
        // Inline use-heading text mid-sentence must NOT truncate the how-to-get body.
        String neg = "see " + useHead + " below for details NEGATE_TAIL";
        String body = getHead + "\n" + neg + "\n\n" + useHead + "\nkeep-use\n\n" + footer;
        String out = AskReplyScrub.replaceHowToGetBody(body, line);
        String section = howToGetInner(out, lang);
        assert section.equals(line) : "inner residual=" + section;
        assert !out.contains("NEGATE_TAIL") : out;
        assert out.contains("keep-use") : out;
        System.out.println("inlineUseHeadNotSplit OK");
    }

    static void sameLineContent(String lang) {
        ModularFrameStandard.FrameRecipe sword = ModularFrameStandard.recipeAt(0);
        String line = HonestMiss.frameStandardRecipeLine(lang, sword);
        String getHead = ReplyLang.sectionHowToGet(lang);
        String useHead = ReplyLang.sectionHowToUse(lang);
        String footer = ReplyLang.sourceHeader(lang) + "JEI";
        String neg = "SAME_LINE_NEGATE_MARK";
        String body = getHead + ":" + neg + "\n\n" + useHead + "\nkeep\n\n" + footer;
        String out = AskReplyScrub.replaceHowToGetBody(body, line);
        assert !out.contains(neg) : out;
        String section = howToGetInner(out, lang);
        assert section.equals(line) : section;
        System.out.println("sameLineContent OK");
    }

    static void noHeadingFallback() {
        ModularFrameStandard.FrameRecipe sword = ModularFrameStandard.recipeAt(0);
        String line = HonestMiss.frameStandardRecipeLine("zh_cn", sword);
        String body = "plain answer without section heads\n\n" + ReplyLang.sourceHeader("zh_cn") + "JEI";
        String replaced = AskReplyScrub.replaceHowToGetBody(body, line);
        assert replaced.equals(body) : "no-heading must return input unchanged";
        String appended = HonestMiss.ensureFrameStandardRecipeVisible(body, "zh_cn", sword);
        assert appended.contains(line) : appended;
        System.out.println("noHeadingFallback OK");
    }

    static void noPartialMarker(String lang) {
        ModularFrameStandard.FrameRecipe sword = ModularFrameStandard.recipeAt(0);
        String line = HonestMiss.frameStandardRecipeLine(lang, sword);
        String getHead = ReplyLang.sectionHowToGet(lang);
        String useHead = ReplyLang.sectionHowToUse(lang);
        String footer = ReplyLang.sourceHeader(lang) + "JEI";
        String neg = "see [[item:tetra:modular_sword]] unknown path";
        String body = getHead + "\n" + neg + "\n\n" + useHead + "\nkeep\n\n" + footer;
        String out = AskReplyScrub.replaceHowToGetBody(body, line);
        int open = countSub(out, "[[");
        int close = countSub(out, "]]");
        assert open == close : "open=" + open + " close=" + close + " body=" + out;
        System.out.println("noPartialMarker OK");
    }

    static void stubDiscriminates(String lang) {
        ModularFrameStandard.FrameRecipe sword = ModularFrameStandard.recipeAt(0);
        String line = HonestMiss.frameStandardRecipeLine(lang, sword);
        String getHead = ReplyLang.sectionHowToGet(lang);
        String useHead = ReplyLang.sectionHowToUse(lang);
        String footer = ReplyLang.sourceHeader(lang) + "JEI";
        String neg = "NEGATE_OBTAIN_NO_PATH_MARK";
        String body = getHead + "\n" + neg + "\n\n" + useHead + "\nuse-section-body-keep\n\n" + footer;
        // Stub that does not replace — same assertions as sectionReplaced must fail.
        String stubOut = body;
        boolean failed = false;
        try {
            String section = howToGetInner(stubOut, lang);
            assert section.equals(line) : "stub must not equal line";
        } catch (AssertionError e) {
            failed = true;
        }
        assert failed : "stubDiscriminates must red without real replace";
        // Real path still green.
        String real = AskReplyScrub.replaceHowToGetBody(body, line);
        assert howToGetInner(real, lang).equals(line);
        System.out.println("stubDiscriminates OK");
    }

    /** Inner text of how-to-get section (after heading match, before use head / sources). */
    static String howToGetInner(String answer, String lang) {
        String getHead = ReplyLang.sectionHowToGet(lang);
        // Mirror HOW_TO_GET_HEAD: heading may be followed by :/：/whitespace.
        Pattern head = Pattern.compile(
                "(?im)^[ \\t]*(?:#{1,3}[ \\t]*)?(?:\\d+[.)][ \\t]*)?(?:[【\\[])?"
                        + Pattern.quote(getHead)
                        + "(?:[】\\]])?(?:[:：]|\\s|\\z)");
        Matcher m = head.matcher(answer);
        assert m.find() : "missing how-to-get head in: " + answer;
        int start = m.end();
        String useHead = ReplyLang.sectionHowToUse(lang);
        Pattern use = Pattern.compile(
                "(?im)^[ \\t]*(?:##[ \\t]*)?(?:\\d+[.)][ \\t]*)?"
                        + Pattern.quote(useHead)
                        + "(?:[:：]|\\s|\\z)");
        Matcher u = use.matcher(answer);
        int end = answer.length();
        if (u.find(start)) {
            end = u.start();
        }
        Matcher src = ReplySources.HEADER.matcher(answer);
        if (src.find(start) && src.start() < end) {
            end = src.start();
        }
        return answer.substring(start, end).strip();
    }

    static int countSub(String s, String sub) {
        int n = 0;
        int from = 0;
        while (true) {
            int i = s.indexOf(sub, from);
            if (i < 0) {
                return n;
            }
            n++;
            from = i + sub.length();
        }
    }

    /** Whole-line equality after strip. Prefix / mixed lines must survive. */
    static void stripLangMissLineCases() throws Exception {
        for (String lang : new String[] {"zh_cn", "en_us", "zh_tw"}) {
            String miss = ReplyLang.askMissAcquirePlayer(lang);
            String unk = ReplyLang.obtainUnknown(lang);
            assert miss != null && !miss.isBlank() : "miss " + lang;
            assert unk != null && !unk.isBlank() : "unk " + lang;
            // 整行相等比對嘅前提：lang 模板句必須單行（多行即靜默命中失敗 ⇒ G2 靜默復發）。
            assert miss.indexOf('\n') < 0 && miss.indexOf('\r') < 0 : "miss must be single-line " + lang;
            assert unk.indexOf('\n') < 0 && unk.indexOf('\r') < 0 : "unk must be single-line " + lang;
            String exact = "HEAD\n" + miss + "\nTAIL";
            String gone = AskReplyScrub.stripLangMissLine(exact, lang);
            assert !hasWholeLine(gone, miss) : "exact miss kept " + lang;
            assert gone.contains("HEAD") && gone.contains("TAIL") : "neighbors " + lang;
            assert AskReplyScrub.stripLangMissLine("  " + miss + "  ", lang).isBlank() : "pad " + lang;
            assert AskReplyScrub.stripLangMissLine("\u3000" + unk + "\u3000", lang).isBlank() : "fw space " + lang;
            String prefixed = "x " + miss;
            assert AskReplyScrub.stripLangMissLine(prefixed, lang).equals(prefixed) : "prefix " + lang;
            String mixed = miss + " / " + unk;
            assert AskReplyScrub.stripLangMissLine(mixed, lang).equals(mixed) : "mixed " + lang;
            // R2 LOW：前後各一空行 → 刪句後只留一個空行（唔准三個連續 \n）。
            String padded = "HEAD\n\n" + miss + "\n\nTAIL";
            String paddedOut = AskReplyScrub.stripLangMissLine(padded, lang);
            assert !paddedOut.contains(miss) : "padded miss kept " + lang;
            assert paddedOut.equals("HEAD\n\nTAIL")
                    : "padded blank structure " + lang + " -> " + paddedOut.replace("\n", "\\n");
        }
        String zhMiss = ReplyLang.askMissAcquirePlayer("zh_cn");
        assert zhMiss.indexOf('\u3002') >= 0 : "zh miss lacks fullwidth period";
        assert AskReplyScrub.stripLangMissLine(zhMiss + "   ", "zh_cn").isBlank() : "period then spaces";
        modifiedBranchDoesNotStrip();
        System.out.println("stripLangMissLineCases OK");
    }

    /**
     * Call site lives in the STANDARD branch only — 設計不變式（plan §2 D4）：strip 只准喺
     * STANDARD 分支，唔准喺 MODIFIED／其他分支。用 brace matching 判位置，唔用 lastIndexOf
     * 比大小（對 static import／抽 helper 唔敏感）。
     */
    static void modifiedBranchDoesNotStrip() throws Exception {
        java.nio.file.Path engine = engineSource();
        String src = java.nio.file.Files.readString(engine);
        String needle = "AskReplyScrub.stripLangMissLine(";
        int call = src.indexOf(needle);
        assert call > 0 : "missing strip call";
        assert src.indexOf(needle, call + needle.length()) < 0
                : "design invariant (plan §2 D4): stripLangMissLine allowed once, STANDARD branch only";
        int std = src.lastIndexOf("frameKind == ModularFrameStandard.Kind.STANDARD", call);
        assert std > 0 : "missing STANDARD branch anchor";
        int stdOpen = src.indexOf('{', std);
        assert stdOpen > 0 : "missing STANDARD branch brace";
        int stdClose = braceEnd(src, stdOpen);
        assert stdClose > stdOpen : "unbalanced STANDARD branch";
        assert call > stdOpen && call < stdClose : "stripLangMissLine not inside STANDARD branch";
        int finalCheck = src.indexOf("frame-standard: final check present");
        assert finalCheck > 0 && call > finalCheck : "strip must follow STANDARD recipe insert";
    }

    /** {@code open} 位置嘅 '{' 對應嘅 '}' index；唔平衡／唔係 '{' → -1。 */
    static int braceEnd(String src, int open) {
        if (open < 0 || open >= src.length() || src.charAt(open) != '{') {
            return -1;
        }
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    static java.nio.file.Path engineSource() {
        java.nio.file.Path[] candidates = {
                java.nio.file.Path.of("src/main/java/com/skps9/packai/logic/AskEngine.java"),
                java.nio.file.Path.of("forge/1.19.2/src/main/java/com/skps9/packai/logic/AskEngine.java"),
        };
        for (java.nio.file.Path p : candidates) {
            if (java.nio.file.Files.isRegularFile(p)) {
                return p;
            }
        }
        throw new AssertionError("AskEngine.java not found from " + java.nio.file.Path.of("").toAbsolutePath());
    }

    static boolean hasWholeLine(String body, String want) {
        if (body == null || want == null) {
            return false;
        }
        int i = 0;
        int n = body.length();
        while (i <= n) {
            int end = i;
            while (end < n) {
                char c = body.charAt(end);
                if (c == '\n' || c == '\r') {
                    break;
                }
                end++;
            }
            if (body.substring(i, end).strip().equals(want)) {
                return true;
            }
            if (end >= n) {
                break;
            }
            if (body.charAt(end) == '\r' && end + 1 < n && body.charAt(end + 1) == '\n') {
                i = end + 2;
            } else {
                i = end + 1;
            }
        }
        return false;
    }
}

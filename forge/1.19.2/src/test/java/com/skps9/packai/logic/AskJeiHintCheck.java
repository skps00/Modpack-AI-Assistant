package com.skps9.packai.logic;

/** Runnable check: recipe cards present → never append jei_no_recipes absence hints. */
public final class AskJeiHintCheck {
    private AskJeiHintCheck() {}

    public static void main(String[] args) {
        assert !AskJeiHints.shouldAppendNoJeiRecipes(true, true, false)
                : "cards non-empty must block jeiNoRecipes";
        assert !AskJeiHints.shouldAppendNoJeiRecipes(true, false, true)
                : "cards non-empty must block jeiHintEmpty path";
        assert AskJeiHints.shouldAppendNoJeiRecipes(false, true, true)
                : "no cards + focus + empty target → jeiNoRecipes ok";
        assert AskJeiHints.shouldAppendJeiHintEmpty(false, false, true)
                : "no cards + no focus + empty target → hint empty ok";
        assert !AskJeiHints.shouldAppendJeiHintEmpty(true, false, true)
                : "cards block hint empty";
        assert !AskJeiHints.shouldAppendNoJeiRecipes(false, true, false)
                : "non-empty target → no jeiNoRecipes";

        assert AskJeiHints.isJeiAbsenceSummary("[JEI] Foo has no showable recipes, uses, or machine recipes.");
        assert AskJeiHints.isJeiAbsenceSummary("【JEI 資料】「石」目前沒有可顯示的配方、用途或機器配方。");
        assert AskJeiHints.isJeiAbsenceSummary("[JEI] No JEI recipe data for the held item.\n");
        assert AskJeiHints.isJeiAbsenceSummary("无已知配方／无JEI资料");
        assert AskJeiHints.isJeiAbsenceSummary("無已知配方");
        assert AskJeiHints.looksLikeAbsenceClaim("目前无 JEI 配方资料。");
        assert AskJeiHints.looksLikeAbsenceClaim("JEI 目前沒有列出它的合成配方");
        assert AskJeiHints.looksLikeAbsenceClaim("JEI currently does not list a crafting recipe for it.");
        assert AskJeiHints.looksLikeAbsenceClaim("There is no crafting recipe in JEI.");
        assert !AskJeiHints.isJeiAbsenceSummary("[JEI] Item Foo [mod:bar]\n• [Crafting] 1 recipes\n");

        String grounded = AskJeiHints.chooseJeiSummaryText(
                "zh_tw",
                "[JEI] X has no showable recipes, uses, or machine recipes.",
                true,
                "Crafting");
        assert grounded != null && !AskJeiHints.isJeiAbsenceSummary(grounded)
                : "cards + absence summary → replace with grounded hint";
        String withUseful = AskJeiHints.chooseJeiSummaryText(
                "en_us", "[JEI] useful 3\n", true, "Crafting");
        assert withUseful != null && withUseful.contains("useful 3") : withUseful;
        assert !withUseful.trim().equals("[JEI] useful 3")
                : "cards + useful summary must prepend cards hint: " + withUseful;

        String scrubbed = AskJeiHints.scrubAbsenceClaimsWhenCards(
                "[[item:mod:monocle]]\nAdvanced Monocle helps see.\n无已知配方／无JEI资料\n[[recipe:mod:monocle]]\n[Sources] JEI",
                true);
        assert !scrubbed.contains("无已知配方") : scrubbed;
        assert scrubbed.contains("[[item:mod:monocle]]") : scrubbed;
        assert scrubbed.contains("Advanced Monocle") : scrubbed;
        assert AskJeiHints.scrubAbsenceClaimsWhenCards("无已知配方", false).contains("无已知配方");

        String grease = AskJeiHints.scrubAbsenceClaimsWhenCards(
                "GREASE满装瓶可合成。\nJEI 目前沒有列出它的合成配方，請查任務。\n【來源】JEI",
                true);
        assert !grease.contains("沒有列出") : grease;
        assert grease.contains("GREASE") : grease;
        assert grease.contains("【來源】JEI") : grease;

        System.out.println("AskJeiHintCheck OK");
    }
}

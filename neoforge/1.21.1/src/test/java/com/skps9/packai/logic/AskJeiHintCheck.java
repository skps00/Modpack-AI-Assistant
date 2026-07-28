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
        assert !AskJeiHints.isJeiAbsenceSummary("[JEI] Item Foo [mod:bar]\n• [Crafting] 1 recipes\n");

        String grounded = AskJeiHints.chooseJeiSummaryText(
                "zh_tw",
                "[JEI] X has no showable recipes, uses, or machine recipes.",
                true,
                "Crafting");
        assert grounded != null && !AskJeiHints.isJeiAbsenceSummary(grounded)
                : "cards + absence summary → replace with grounded hint";
        assert AskJeiHints.chooseJeiSummaryText("en_us", "[JEI] useful 3\n", true, "Crafting")
                .contains("useful 3");

        System.out.println("AskJeiHintCheck OK");
    }
}

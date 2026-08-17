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
        assert AskJeiHints.isJeiAbsenceSummary("未持物品");
        assert AskJeiHints.looksLikeAbsenceClaim("[JEI] No held item.");
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

        assert AskJeiHints.isObtainOnlyQuestAcquire(java.util.List.of("取得：奧秘章節"));
        assert !AskJeiHints.isObtainOnlyQuestAcquire(java.util.List.of("繳交：提交章節"));
        assert !AskJeiHints.isObtainOnlyQuestAcquire(
                java.util.List.of("取得：A", "繳交：B"));
        assert AskJeiHints.isObtainOnlyQuestAcquire(
                java.util.List.of("item:x -[quest_obtain]-> hold"));
        assert AskJeiHints.isSubmitOnlyQuestAcquire(java.util.List.of("繳交：提交章節"));
        assert !AskJeiHints.isSubmitOnlyQuestAcquire(java.util.List.of("取得：奧秘章節"));

        String obtainCanon = ReplyLang.questStatusObtain("zh_tw", "奧秘·回憶");
        assert obtainCanon.contains("背包持有即可完成") : obtainCanon;
        assert obtainCanon.contains("【任務】") : obtainCanon;
        assert obtainCanon.contains("奧秘·回憶") : obtainCanon;
        assert ReplyLang.questStatusObtain("zh_tw").isBlank() : "bare obtain must be empty";

        String submitCanon = ReplyLang.questStatusSubmit("zh_tw", "深埋的信");
        assert submitCanon.contains("須繳交") : submitCanon;
        assert submitCanon.contains("深埋的信") : submitCanon;
        assert ReplyLang.questStatusSubmit("zh_tw").isBlank() : "bare submit must be empty";

        // Missing canonical → inject titled
        String missing = AskJeiHints.ensureQuestStatusVisible(
                "1. 合成此物。\n【來源】JEI",
                java.util.List.of("取得：奧秘·回憶"),
                "zh_tw");
        assert missing.contains(obtainCanon) : missing;
        assert missing.contains("合成此物") : missing;
        assert missing.indexOf(obtainCanon) < missing.indexOf("【來源】") : missing;

        // Wrong 轉換 line → replace with allowlist; shop 兌換 untouched
        String coinBad = AskJeiHints.ensureQuestStatusVisible(
                "1. 在任務書兌換下界合金幣。\n2. 商店可兌換金幣。\n【來源】JEI",
                java.util.List.of("取得：奧秘·回憶"),
                "zh_tw");
        assert coinBad.contains(obtainCanon) : coinBad;
        assert coinBad.contains("商店可兌換金幣") : coinBad;
        assert !coinBad.contains("任務書兌換") : coinBad;
        assert coinBad.lines().filter(l -> l.contains(obtainCanon)).count() == 1 : coinBad;

        // 轉換 → allowlist replace
        String convertLine = AskJeiHints.ensureQuestStatusVisible(
                "JEI 顯示可在任務書中轉換為下界合金幣\n【來源】JEI",
                java.util.List.of("item:x -[quest_obtain]-> 奧秘·回憶"),
                "zh_tw");
        assert convertLine.contains(obtainCanon) : convertLine;
        assert !convertLine.contains("轉換") : convertLine;

        // 放入 wrong verb on quest-ish line
        String putLine = AskJeiHints.ensureQuestStatusVisible(
                "把物品放入任務書即可\n【來源】JEI",
                java.util.List.of("取得：奧秘·回憶"),
                "zh_tw");
        assert putLine.contains(obtainCanon) : putLine;
        assert !putLine.contains("放入任務") : putLine;

        // No obtain facts → no inject
        String noScrub = AskJeiHints.ensureQuestStatusVisible(
                "1. 在任務書兌換下界合金幣。",
                java.util.List.of(),
                "zh_tw");
        assert noScrub.contains("兌換") : noScrub;
        assert !noScrub.contains(obtainCanon) : noScrub;

        // Raw edge without title-like label → no bare inject
        String noTitle = AskJeiHints.ensureQuestStatusVisible(
                "1. Craft it.",
                java.util.List.of("item:x -[quest_submit]-> "),
                "zh_tw");
        assert !noTitle.contains("【任務】") : noTitle;

        // EN convert → EN canonical
        String enCanon = ReplyLang.questStatusObtain("en_us", "Mystery");
        String enScrub = AskJeiHints.ensureQuestStatusVisible(
                "1. Exchange netherite coins via the quest book.\n[Sources] JEI",
                java.util.List.of("Obtain: Mystery"),
                "en_us");
        assert enScrub.contains(enCanon) : enScrub;
        assert !enScrub.toLowerCase().contains("exchange netherite") : enScrub;

        // Submit-only inject with title
        String submitOnly = AskJeiHints.ensureQuestStatusVisible(
                "1. Craft it.\n【來源】JEI",
                java.util.List.of("繳交：深埋的信"),
                "zh_tw");
        assert submitOnly.contains(submitCanon) : submitOnly;

        System.out.println("AskJeiHintCheck OK");
    }
}

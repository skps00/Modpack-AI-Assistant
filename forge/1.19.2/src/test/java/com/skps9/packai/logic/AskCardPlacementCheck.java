package com.skps9.packai.logic;

import java.util.List;

import com.skps9.packai.config.PackAiConfig;

/**
 * Headless: modular-tool card placement + empty-frame drop. Run with -ea.
 * Does not construct RecipeCard (needs Minecraft registry).
 */
public final class AskCardPlacementCheck {
    private AskCardPlacementCheck() {}

    public static void main(String[] args) {
        assert PackAiConfig.DEFAULT_MODULAR_TOOL_SINGLE_ITEM : "modularToolSingleItem default true";

        mentionAfterItemEmbedNotSectionFirst();
        frameOutputDroppedMarkerCount();
        unmatchedGoesToSectionEnd();

        System.out.println("AskCardPlacementCheck OK");
    }

    /** 1. Body has {{item:luna_flesh_reforged:infested_spine}} → marker after that line. */
    private static void mentionAfterItemEmbedNotSectionFirst() {
        String reply = ""
                + "怎么来（组装，不是普通合成）:\n"
                + "1. 到 Tetra 工作台，按这把剑的部件配置拼装。\n"
                + "2. 剑刃材料 {{item:luna_flesh_reforged:infested_spine}}\n";
        String out = AskCardFallback.ensureGetCardsForCheck(
                reply, List.of(List.of("luna_flesh_reforged:infested_spine")), null);
        int marker = out.indexOf("[[recipe_card:0]]");
        int item = out.indexOf("luna_flesh_reforged:infested_spine");
        int step1 = out.indexOf("1. 到 Tetra");
        int titleNl = out.indexOf('\n');
        assert marker >= 0 : out;
        assert marker > item : out;
        assert marker > step1 : out;
        assert marker > titleNl : "must not sit on section first line: " + out;
    }

    /** 2. Output==focus empty-frame card excluded (marker count -1). */
    private static void frameOutputDroppedMarkerCount() {
        String reply = ""
                + "怎么来:\n"
                + "1. 到 Tetra 工作台拼装。\n"
                + "2. 剑刃材料 {{item:luna_flesh_reforged:infested_spine}}\n";
        List<List<String>> cards = List.of(
                List.of("tetra:modular_sword"),
                List.of("luna_flesh_reforged:infested_spine"));
        String withFrame = AskCardFallback.ensureGetCardsForCheck(reply, cards, null);
        String dropped = AskCardFallback.ensureGetCardsForCheck(reply, cards, "tetra:modular_sword");
        int before = countMarkers(withFrame);
        int after = countMarkers(dropped);
        assert before == after + 1 : "frame drop should remove one marker: before="
                + before + " after=" + after + "\n" + dropped;
        assert !dropped.contains("[[recipe_card:0]]") : dropped;
        assert dropped.contains("[[recipe_card:1]]") : dropped;
        assert dropped.indexOf("[[recipe_card:1]]")
                > dropped.indexOf("luna_flesh_reforged:infested_spine") : dropped;
    }

    /** 3. Card item never in body → marker at section end (after last step, not first). */
    private static void unmatchedGoesToSectionEnd() {
        String reply = ""
                + "怎么来:\n"
                + "1. 到 Tetra 工作台拼装。\n"
                + "2. 剑刃适应之剑。\n";
        String out = AskCardFallback.ensureGetCardsForCheck(
                reply, List.of(List.of("minecraft:nether_star")), null);
        int marker = out.indexOf("[[recipe_card:0]]");
        int step1 = out.indexOf("1. 到 Tetra");
        int step2 = out.indexOf("2. 剑刃");
        assert marker >= 0 : out;
        assert marker > step2 : "section end after last step: " + out;
        assert marker > step1 : out;
    }

    private static int countMarkers(String s) {
        int n = 0;
        int from = 0;
        while (true) {
            int i = s.indexOf("[[recipe_card:", from);
            if (i < 0) {
                return n;
            }
            n++;
            from = i + 1;
        }
    }
}

package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;

/** Obtain path insert vs recipe cards: no prose when cards present; slim primary path when not. */
public final class AskObtainLineDedupeCheck {
    private AskObtainLineDedupeCheck() {}

    public static void main(String[] args) {
        cardsPresentSkipsAllLinesAndHeading();
        mergeRepeatedInputNames();
        negativeControlSkipDisabled();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        zeroCardsInsertsPrimaryPathOnly();
        zeroCardsTruncatesLongPrimaryPath();
        System.out.println("AskObtainLineDedupeCheck OK");
    }

    private static void cardsPresentSkipsAllLinesAndHeading() {
        List<ObtainPathText.Line> lines = List.of(
                new ObtainPathText.Line("R|a:one", "LINE_ONE"),
                new ObtainPathText.Line("R|a:two", "LINE_TWO"),
                new ObtainPathText.Line("R|a:three", "LINE_THREE"),
                new ObtainPathText.Line("R|a:four", "LINE_FOUR"));
        List<ObtainPathText.Line> filtered = ObtainPathInsert.filterLinesForCards(
                lines, List.of(), 7, "en_us");
        assert filtered.isEmpty() : filtered;
        String body = "Answer only.\n";
        String out = AskReplyScrub.insertAtHowToGetTopOrAppend(
                body,
                ObtainPathInsert.lineTexts(filtered),
                "MISS",
                ReplyLang.obtainHead("en_us"));
        assert out.equals(body) : "must not append heading: " + out;
        System.out.println("cardsPresentSkipsAllLinesAndHeading OK");
    }

    private static void zeroCardsInsertsPrimaryPathOnly() {
        String lang = "en_us";
        List<ObtainPathText.Line> all = List.of(
                new ObtainPathText.Line("R|p1:s1", craftLine(lang, "Iron Ingot", "Stick")),
                new ObtainPathText.Line("R|p1:s2", craftLine(lang, "Iron Pickaxe", "Iron Ingot")),
                new ObtainPathText.Line("R|p2:s1", craftLine(lang, "Gold Ingot", "Gold Nugget")),
                new ObtainPathText.Line("R|p2:s2", craftLine(lang, "Gold Pickaxe", "Gold Ingot")));
        ObtainPaths.Path path1 = pathWithSteps("p1:s1", "p1:s2");
        ObtainPaths.Path path2 = pathWithSteps("p2:s1", "p2:s2");
        List<ObtainPathText.Line> filtered = ObtainPathInsert.filterLinesForCards(
                all, List.of(path1, path2), 0, lang);
        List<String> texts = ObtainPathInsert.lineTexts(filtered);
        assert texts.size() == 2 : texts;
        assert texts.get(0).contains("Stick") : texts;
        assert texts.get(1).contains("Iron Pickaxe") : texts;
        assert !texts.stream().anyMatch(t -> t.contains("Gold")) : texts;
        System.out.println("zeroCardsInsertsPrimaryPathOnly OK");
    }

    private static void zeroCardsTruncatesLongPrimaryPath() {
        String lang = "en_us";
        List<ObtainPathText.Line> all = new ArrayList<>();
        List<ObtainPaths.Step> steps = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String rid = "mod:step" + i;
            all.add(new ObtainPathText.Line("R|" + rid, craftLine(lang, "Out" + i, "In" + i)));
            steps.add(stepWithRecipe(rid));
        }
        ObtainPaths.Path primary = new ObtainPaths.Path(null, List.copyOf(steps), true);
        List<ObtainPathText.Line> filtered =
                ObtainPathInsert.filterLinesForCards(all, List.of(primary), 0, lang);
        assert filtered.size() == 3 : filtered;
        System.out.println("zeroCardsTruncatesLongPrimaryPath OK");
    }

    private static void mergeRepeatedInputNames() {
        String lang = "zh_tw";
        String raw = ReplyLang.tr(lang, "packai.obtain.step.craft", "產物", "岩石粉, 岩石粉, 岩石粉, 岩石粉");
        String merged = ObtainPathInsert.mergeRepeatedInputsInLine(raw);
        assert merged.contains("岩石粉×4") : merged;
        assert !merged.contains("岩石粉, 岩石粉") : merged;
        System.out.println("mergeRepeatedInputNames OK");
    }

    private static void negativeControlSkipDisabled() {
        List<ObtainPathText.Line> lines = List.of(
                new ObtainPathText.Line("R|mod:dup", "CRAFT_LINE_A"),
                new ObtainPathText.Line("R|other", "OTHER"));
        List<ObtainPathText.Line> filtered = ObtainPathInsert.filterLinesForCardsDisabled(
                lines, List.of(), 1, "en_us");
        assert filtered.size() == 2 : filtered;
        List<ObtainPathText.Line> enabled = ObtainPathInsert.filterLinesForCards(
                lines, List.of(), 1, "en_us");
        assert enabled.isEmpty() : "cardsOut>0 must skip all: " + enabled;
        System.out.println("negativeControlSkipDisabled OK");
    }

    private static String craftLine(String lang, String product, String inputs) {
        return ReplyLang.tr(lang, "packai.obtain.step.craft", product, inputs);
    }

    private static ObtainPaths.Path pathWithSteps(String... recipeIds) {
        List<ObtainPaths.Step> steps = new ArrayList<>();
        for (String rid : recipeIds) {
            steps.add(stepWithRecipe(rid));
        }
        return new ObtainPaths.Path(null, List.copyOf(steps), true);
    }

    private static ObtainPaths.Step stepWithRecipe(String recipeId) {
        ObtainGraph.Edge edge = new ObtainGraph.Edge(
                new ResourceLocation(recipeId),
                new ResourceLocation("minecraft:crafting"),
                Items.STICK,
                1,
                List.of(List.of(Items.STICK)),
                null,
                List.of(),
                false);
        return new ObtainPaths.Step(edge, edge.inputGroups());
    }
}

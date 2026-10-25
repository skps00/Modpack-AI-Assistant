package com.skps9.packai.logic;

import java.util.List;
import java.util.Set;

/** Obtain path insert vs recipe cards: skip duplicate R| lines. */
public final class AskObtainLineDedupeCheck {
    private AskObtainLineDedupeCheck() {}

    public static void main(String[] args) {
        oneOfTwoSkippedWhenCardCoversRecipe();
        zeroCardsInsertsAll();
        allSkippedNoHeadingAppend();
        negativeControlDedupeDisabled();
        System.out.println("AskObtainLineDedupeCheck OK");
    }

    private static void oneOfTwoSkippedWhenCardCoversRecipe() {
        String rid = "productivebees:bee_food";
        List<ObtainPathText.Line> lines = List.of(
                new ObtainPathText.Line("R|" + rid, "CRAFT_LINE_A"),
                new ObtainPathText.Line("R|other:smelt", "SMELT_LINE_B"));
        List<ObtainPathText.Line> filtered =
                ObtainPathInsert.filterLinesForCards(lines, 1, Set.of(rid));
        List<String> texts = ObtainPathInsert.lineTexts(filtered);
        assert texts.size() == 1 : texts;
        assert texts.get(0).equals("SMELT_LINE_B") : texts;
        String body = "How to get:\nprose\n\nHow to use:\nu\n";
        String out = AskReplyScrub.insertAtHowToGetTopOrAppend(
                body, texts, "MISS", ReplyLang.obtainHead("en_us"));
        assert out.contains("SMELT_LINE_B") : out;
        assert !out.contains("CRAFT_LINE_A") : out;
        System.out.println("oneOfTwoSkippedWhenCardCoversRecipe OK");
    }

    private static void zeroCardsInsertsAll() {
        List<ObtainPathText.Line> lines = List.of(
                new ObtainPathText.Line("R|a:one", "LINE_ONE"),
                new ObtainPathText.Line("R|a:two", "LINE_TWO"));
        List<ObtainPathText.Line> filtered =
                ObtainPathInsert.filterLinesForCards(lines, 0, Set.of("a:one"));
        assert filtered.size() == 2 : filtered;
        List<String> texts = ObtainPathInsert.lineTexts(filtered);
        String body = "How to get:\n\nHow to use:\nu\n";
        String out = AskReplyScrub.insertAtHowToGetTopOrAppend(
                body, texts, "MISS", ReplyLang.obtainHead("en_us"));
        assert out.contains("LINE_ONE") && out.contains("LINE_TWO") : out;
        System.out.println("zeroCardsInsertsAll OK");
    }

    private static void allSkippedNoHeadingAppend() {
        String rid = "mod:dup";
        List<ObtainPathText.Line> lines = List.of(new ObtainPathText.Line("R|" + rid, "ONLY_LINE"));
        List<ObtainPathText.Line> filtered =
                ObtainPathInsert.filterLinesForCards(lines, 2, Set.of(rid));
        assert filtered.isEmpty() : filtered;
        String body = "Answer only.\n";
        String before = body;
        String out = AskReplyScrub.insertAtHowToGetTopOrAppend(
                before,
                ObtainPathInsert.lineTexts(filtered),
                "MISS",
                ReplyLang.obtainHead("en_us"));
        assert out.equals(before) : "must not append heading: " + out;
        System.out.println("allSkippedNoHeadingAppend OK");
    }

    private static void negativeControlDedupeDisabled() {
        String rid = "mod:dup";
        List<ObtainPathText.Line> lines = List.of(
                new ObtainPathText.Line("R|" + rid, "CRAFT_LINE_A"),
                new ObtainPathText.Line("R|other", "OTHER"));
        List<ObtainPathText.Line> filtered =
                ObtainPathInsert.filterLinesForCardsDisabled(lines, 1, Set.of(rid));
        assert filtered.size() == 2 : filtered;
        List<String> texts = ObtainPathInsert.lineTexts(filtered);
        assert texts.size() == 2 : texts;
        System.out.println("negativeControlDedupeDisabled OK");
    }
}

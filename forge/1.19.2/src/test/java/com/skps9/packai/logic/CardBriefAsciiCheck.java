package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;

/** Pure-data checks for {@link RenderRecipeCardsAskTool#briefFromFreq}. */
public final class CardBriefAsciiCheck {
    private CardBriefAsciiCheck() {}

    public static void main(String[] args) {
        // 1. Four materials (each 1) — all listed, no ellipsis.
        LinkedHashMap<String, Integer> four = new LinkedHashMap<>();
        four.put("a", 1);
        four.put("b", 1);
        four.put("c", 1);
        four.put("d", 1);
        String t1 = RenderRecipeCardsAskTool.briefFromFreq(four, "", 6);
        assert t1.equals("a+b+c+d") : t1;
        assert !t1.contains("…") : t1;

        // 2. Seven materials — six kinds then +…
        LinkedHashMap<String, Integer> seven = new LinkedHashMap<>();
        for (int i = 1; i <= 7; i++) {
            seven.put("m" + i, 1);
        }
        String t2 = RenderRecipeCardsAskTool.briefFromFreq(seven, "", 6);
        assert t2.equals("m1+m2+m3+m4+m5+m6+…") : t2;

        // 3. freq contains output id — excluded from result.
        LinkedHashMap<String, Integer> withOut = new LinkedHashMap<>();
        withOut.put("torchmaster:megatorch", 1);
        withOut.put("minecraft:torch", 3);
        String t3 = RenderRecipeCardsAskTool.briefFromFreq(
                withOut, "torchmaster:megatorch", 6);
        assert t3.equals("minecraft:torch×3") : t3;

        // 4. Same material count=3 → Name×3
        LinkedHashMap<String, Integer> triple = new LinkedHashMap<>();
        triple.put("Iron Ingot", 3);
        String t4 = RenderRecipeCardsAskTool.briefFromFreq(triple, "", 6);
        assert t4.equals("Iron Ingot×3") : t4;

        // 5. Empty freq → empty string.
        assert RenderRecipeCardsAskTool.briefFromFreq(new LinkedHashMap<>(), "", 6).isEmpty();
        assert RenderRecipeCardsAskTool.briefFromFreq(null, "", 6).isEmpty();

        // 6. Same display name, different ids — must not merge (id keys).
        LinkedHashMap<String, Integer> twoIds = new LinkedHashMap<>();
        twoIds.put("mod:gold_a", 1);
        twoIds.put("mod:gold_b", 1);
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        labels.put("mod:gold_a", "Gold");
        labels.put("mod:gold_b", "Gold");
        String t6 = RenderRecipeCardsAskTool.briefFromFreq(twoIds, labels, "", 6);
        assert t6.equals("Gold+Gold") : t6;

        // 7. maxKinds=0 → empty (no throw).
        LinkedHashMap<String, Integer> one = new LinkedHashMap<>();
        one.put("x", 1);
        assert RenderRecipeCardsAskTool.briefFromFreq(one, "", 0).isEmpty();

        // slotOptions==null regression: CardLabelMerge + plain labels == briefFromFreq
        LinkedHashMap<String, Integer> regFreq = new LinkedHashMap<>();
        regFreq.put("mod:gold_a", 1);
        regFreq.put("mod:gold_b", 1);
        regFreq.put("minecraft:torch", 3);
        LinkedHashMap<String, String> regLabels = new LinkedHashMap<>();
        regLabels.put("mod:gold_a", "Gold");
        regLabels.put("mod:gold_b", "Gold");
        regLabels.put("minecraft:torch", "Torch");
        String legacy = RenderRecipeCardsAskTool.briefFromFreq(regFreq, regLabels, "", 6);
        ArrayList<CardLabelMerge.Entry> regEntries = new ArrayList<>();
        regEntries.add(new CardLabelMerge.Entry("mod:gold_a", "Gold"));
        regEntries.add(new CardLabelMerge.Entry("mod:gold_b", "Gold"));
        for (int i = 0; i < 3; i++) {
            regEntries.add(new CardLabelMerge.Entry("minecraft:torch", "Torch"));
        }
        String merged = CardLabelMerge.merge(regEntries, Set.of(), 6);
        assert merged.equals(legacy) : merged + " vs " + legacy;

        System.out.println("check CardBriefAsciiCheck OK");
    }
}

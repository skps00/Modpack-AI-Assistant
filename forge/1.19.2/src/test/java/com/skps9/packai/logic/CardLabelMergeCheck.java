package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class CardLabelMergeCheck {
    private CardLabelMergeCheck() {}

    public static void main(String[] args) {
        List<CardLabelMerge.Entry> twoLabels = List.of(
                new CardLabelMerge.Entry("mod:a", "Gold"),
                new CardLabelMerge.Entry("mod:b", "Gold"));
        assert CardLabelMerge.merge(twoLabels, Set.of(), 6).equals("Gold+Gold");

        List<CardLabelMerge.Entry> threeTorch = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            threeTorch.add(new CardLabelMerge.Entry("minecraft:torch", "Torch"));
        }
        assert CardLabelMerge.merge(threeTorch, Set.of(), 6).equals("Torch×3");

        String logTag = "Dark Oak Log (tag #minecraft:logs)";
        List<CardLabelMerge.Entry> twoLogs = List.of(
                new CardLabelMerge.Entry("minecraft:dark_oak_log", logTag),
                new CardLabelMerge.Entry("minecraft:dark_oak_log", logTag));
        String mergedLogs = CardLabelMerge.merge(twoLogs, Set.of(), 6);
        assert mergedLogs.equals("Dark Oak Log×2 (tag #minecraft:logs)") : mergedLogs;

        List<CardLabelMerge.Entry> twoSugar = List.of(
                new CardLabelMerge.Entry("minecraft:sugar", "Sugar"),
                new CardLabelMerge.Entry("minecraft:sugar", "Sugar"));
        String mergedSugar = CardLabelMerge.merge(twoSugar, Set.of(), 6);
        assert mergedSugar.equals("Sugar×2") : mergedSugar;

        List<CardLabelMerge.Entry> withOut = List.of(
                new CardLabelMerge.Entry("torchmaster:megatorch", "Mega"),
                new CardLabelMerge.Entry("minecraft:torch", "Torch"),
                new CardLabelMerge.Entry("minecraft:torch", "Torch"),
                new CardLabelMerge.Entry("minecraft:torch", "Torch"));
        assert CardLabelMerge.merge(withOut, Set.of("torchmaster:megatorch"), 6).equals("Torch×3");

        List<CardLabelMerge.Entry> seven = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            seven.add(new CardLabelMerge.Entry("m" + i, "m" + i));
        }
        assert CardLabelMerge.merge(seven, Set.of(), 6).equals("m1+m2+m3+m4+m5+m6+…");

        System.out.println("check CardLabelMergeCheck OK");
        System.out.println("mergedLogs=" + mergedLogs);
        System.out.println("mergedSugar=" + mergedSugar);
    }
}

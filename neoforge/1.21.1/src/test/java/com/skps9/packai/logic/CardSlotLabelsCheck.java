package com.skps9.packai.logic;

import java.util.List;

public final class CardSlotLabelsCheck {
    private CardSlotLabelsCheck() {}

    public static void main(String[] args) {
        String en = "en_us";
        assert CardSlotLabels.labelFor("Iron", null, en).equals("Iron");
        assert CardSlotLabels.labelFor("Iron", new SlotOptions("", List.of(), 1), en).equals("Iron");

        String unknown = CardSlotLabels.labelFor("?", new SlotOptions("", List.of(), 0), en);
        assert unknown.startsWith("?") : unknown;
        assert unknown.contains("JEI") : unknown;
        int openParen = 0;
        for (int i = 0; i < unknown.length(); i++) {
            if (unknown.charAt(i) == '(') {
                openParen++;
            }
        }
        assert openParen == 1 : unknown;

        String multi = CardSlotLabels.labelFor(
                "Pink Slime",
                new SlotOptions("minecraft:logs", List.of(), 2),
                en);
        assert !multi.contains("#") : multi;
        assert multi.contains(" (any of 2)") : multi;

        String any = CardSlotLabels.labelFor("Gem", new SlotOptions("", List.of(), 12), en);
        assert any.contains(" (any of 12)") : any;

        System.out.println("check CardSlotLabelsCheck OK");
        System.out.println("unknown=" + unknown);
        System.out.println("multi=" + multi);
        System.out.println("any=" + any);
    }
}

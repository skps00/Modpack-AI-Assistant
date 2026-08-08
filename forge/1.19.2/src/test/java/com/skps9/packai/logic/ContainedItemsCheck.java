package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

/** Runnable check: ContainedItemsText format / cap (no Minecraft bootstrap). */
public final class ContainedItemsCheck {
    private ContainedItemsCheck() {}

    public static void main(String[] args) {
        assert ContainedItemsText.formatBlock(null).isEmpty();
        assert ContainedItemsText.formatBlock(List.of()).isEmpty();
        assert ContainedItemsText.formatBlock(List.of("  ", "")).isEmpty();

        String one = ContainedItemsText.formatBlock(List.of("Dirt x32"));
        assert one.equals(ContainedItemsText.HEADER + "\nDirt x32") : one;

        assert ContainedItemsText.entryLine("Stick", 1).equals("Stick");
        assert ContainedItemsText.entryLine("Dirt", 64).equals("Dirt x64");
        assert ContainedItemsText.entryLine("  ", 3).isEmpty();

        List<String> many = new ArrayList<>();
        for (int i = 0; i < ContainedItemsText.MAX_LINES + 5; i++) {
            many.add("Item" + i);
        }
        String capped = ContainedItemsText.formatBlock(many);
        assert capped.startsWith(ContainedItemsText.HEADER + "\n");
        assert capped.contains("... (+5 more)") : capped;
        long bodyLines = capped.lines().skip(1).count();
        assert bodyLines == ContainedItemsText.MAX_LINES + 1 : bodyLines;

        System.out.println("ContainedItemsCheck OK");
    }
}

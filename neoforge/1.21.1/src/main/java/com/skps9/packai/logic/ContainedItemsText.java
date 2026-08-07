package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

/** Pure text helpers for {@link ContainedItems} — no Minecraft types (runnable check). */
final class ContainedItemsText {
    static final String HEADER = "[CONTAINED]";
    static final int MAX_LINES = 20;

    private ContainedItemsText() {}

    static String formatBlock(List<String> entryLines) {
        if (entryLines == null || entryLines.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (String line : entryLines) {
            if (line != null && !line.isBlank()) {
                lines.add(line.trim());
            }
        }
        if (lines.isEmpty()) {
            return "";
        }
        boolean truncated = lines.size() > MAX_LINES;
        if (truncated) {
            int omitted = lines.size() - MAX_LINES;
            lines = new ArrayList<>(lines.subList(0, MAX_LINES));
            lines.add("... (+" + omitted + " more)");
        }
        return HEADER + "\n" + String.join("\n", lines);
    }

    static String entryLine(String name, int count) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String n = name.trim();
        int c = Math.max(1, count);
        return c == 1 ? n : n + " x" + c;
    }
}

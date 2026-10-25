package com.skps9.packai.client.gui;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Headless check for {@link Wrap#textLines} — assistant answer panel word wrap (work order AK).
 */
public final class WrapTextCheck {
    private static final ToIntFunction<String> FONT = s -> {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += s.charAt(i) == ' ' ? 4 : 6;
        }
        return w;
    };

    private static final String SOURCES_SNIPPET =
            "[Sources] JEI (recipe and use scan); in-game item tooltip; pack item index "
                    + "(no loot/quest path found)";

    private static final String SHORT = "Short line stays.";

    private WrapTextCheck() {}

    public static void main(String[] args) {
        checkTooltipNotSplitMidToken();
        checkLongTokenHardSplit();
        checkShortUnchanged();
        checkNegativeControlCharSplit();
        System.out.println("WrapTextCheck OK");
    }

    private static void checkTooltipNotSplitMidToken() {
        int width = 200;
        assertTooltipLinesOk(Wrap.textLines(SOURCES_SNIPPET, width, FONT), width);
    }

    private static void assertTooltipLinesOk(List<String> lines, int width) {
        for (String line : lines) {
            assert !line.endsWith("toolt")
                    : "line must not end with broken 'toolt': " + lines;
            assert !line.contains(" item i")
                    : "line must not break inside 'index': " + lines;
            assert lineWidth(line) <= width : "overflow: '" + line + "'";
        }
        for (String line : lines) {
            if (line.contains("tooltip")) {
                assert line.contains("tooltip;") || line.trim().endsWith("tooltip")
                        : "tooltip token must stay whole on a line: " + line;
            }
        }
    }

    private static void checkLongTokenHardSplit() {
        String sixty = "a".repeat(60);
        int width = 120;
        List<String> lines = Wrap.textLines(sixty, width, FONT);
        assert !lines.isEmpty();
        for (String line : lines) {
            assert lineWidth(line) <= width : "hard-split line too wide: " + line;
        }
        String joined = String.join("", lines);
        assert joined.equals(sixty) : "hard-split must preserve all chars";
    }

    private static void checkShortUnchanged() {
        int width = 400;
        List<String> lines = Wrap.textLines(SHORT, width, FONT);
        assert lines.size() == 1 && SHORT.equals(lines.get(0)) : lines;
    }

    private static void checkNegativeControlCharSplit() {
        int width = 200;
        List<String> legacy = Wrap.textLinesCharSplit(SOURCES_SNIPPET, width, FONT);
        boolean failed = false;
        try {
            assertTooltipLinesOk(legacy, width);
        } catch (AssertionError e) {
            failed = true;
        }
        assert failed : "char-split negative control must fail tooltip test (check would be toothless): "
                + legacy;
    }

    private static int lineWidth(String line) {
        return FONT.applyAsInt(line);
    }
}

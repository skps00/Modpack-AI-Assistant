package com.skps9.packai.logic;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Headless guard for the "inline item marker + repeated name" bug: the model sometimes writes the
 * display name right after {@code {{item:id}}}, and the marker expansion used to emit it twice
 * ("Water Wheel Water Wheel"). Run with {@code -ea}.
 */
public final class PlainifyDupCheck {
    private PlainifyDupCheck() {}

    private static final Map<String, String> NAMES = Map.of(
            "create:water_wheel", "Water Wheel",
            "create:large_water_wheel", "Large Water Wheel",
            "mekanismgenerators:wind_generator", "Wind Generator");

    private static final Function<String, String> LOOKUP =
            id -> NAMES.getOrDefault(id, id);

    private static int countIgnoreCase(String haystack, String needle) {
        String h = haystack.toLowerCase(Locale.ROOT);
        String n = needle.toLowerCase(Locale.ROOT);
        int count = 0;
        int i = 0;
        while ((i = h.indexOf(n, i)) >= 0) {
            count++;
            i += n.length();
        }
        return count;
    }

    public static void main(String[] args) {
        // 1) the real trace case: marker followed by the name the model typed
        String a = RecipeEmbed.normalizeInlineMarkers(
                "1. Crafting Table: 8 planks (any plank type) + 1 {{item:create:water_wheel}} Water Wheel "
                        + "craft one Large Water Wheel. [card:1]", LOOKUP);
        assert !a.toLowerCase(Locale.ROOT).contains("water wheel water wheel") : "duplicate kept: " + a;
        assert countIgnoreCase(a, "water wheel") == 2 : "expected ingredient + output mentions: " + a;

        // 2) wind generator variant seen in traces
        String b = RecipeEmbed.normalizeInlineMarkers(
                "{{item:mekanismgenerators:wind_generator}} Wind Generator) — the simplest free FE block", LOOKUP);
        assert !b.toLowerCase(Locale.ROOT).contains("wind generator wind generator") : "duplicate kept: " + b;
        assert countIgnoreCase(b, "wind generator") == 1 : "expected a single mention: " + b;

        // 3) marker with no trailing name still expands exactly once
        String c = RecipeEmbed.normalizeInlineMarkers("Use {{item:create:water_wheel}} in flowing water", LOOKUP);
        assert countIgnoreCase(c, "water wheel") == 1 : "expected one mention: " + c;

        // 4) different word after the marker must survive
        String d = RecipeEmbed.normalizeInlineMarkers(
                "{{item:create:water_wheel}} drives a shaft", LOOKUP);
        assert d.contains("drives a shaft") : "trailing text lost: " + d;

        // 5) negative control: unrelated doubled word untouched
        String e = RecipeEmbed.normalizeInlineMarkers("It is very very slow but works", LOOKUP);
        assert e.contains("very very") : "unrelated duplication must not be collapsed: " + e;

        System.out.println("PlainifyDupCheck OK");
    }
}

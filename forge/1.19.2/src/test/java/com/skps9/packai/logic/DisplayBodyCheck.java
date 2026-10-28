package com.skps9.packai.logic;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Headless guard for the "GUI gets raw body so inline markers render as item icons" contract:
 * the visible text path (answer) stays plain, while the display path (displayBody) keeps
 * {@code {{item:id}}} markers so {@code RecipeEmbed.parts} can emit ITEM pieces.
 * Run with {@code -ea}.
 */
public final class DisplayBodyCheck {
    private DisplayBodyCheck() {}

    private static final Map<String, String> NAMES = Map.of(
            "create:water_wheel", "Water Wheel",
            "create:large_water_wheel", "Large Water Wheel");

    private static final Function<String, String> LOOKUP = id -> NAMES.getOrDefault(id, id);

    public static void main(String[] args) {
        String raw = "1. Crafting Table: 8 planks (any plank type) + 1 {{item:create:water_wheel}} "
                + "Water Wheel craft one Large Water Wheel.";
        String plain = RecipeEmbed.normalizeInlineMarkers(raw, LOOKUP);

        // 1) plain text path: markers gone, name appears once next to the recipe line
        assert !plain.contains("{{item:") : "plain text still has markers: " + plain;
        assert !plain.toLowerCase(java.util.Locale.ROOT).contains("water wheel water wheel") : plain;

        // 2) display path: raw body keeps the marker and parts() turns it into an ITEM piece,
        //    with the name the model wrote as ordinary text right after it.
        List<RecipeEmbed.Part> parts = RecipeEmbed.parts(raw, 0);
        boolean sawItem = false;
        boolean sawTrailingName = false;
        for (int i = 0; i < parts.size(); i++) {
            RecipeEmbed.Part p = parts.get(i);
            if (p.isItem()) {
                assert p.text().contains("water_wheel") : "unexpected item id: " + p.text();
                sawItem = true;
                if (i + 1 < parts.size() && !parts.get(i + 1).isItem()) {
                    String next = parts.get(i + 1).text();
                    sawTrailingName = next.toLowerCase(java.util.Locale.ROOT).contains("water wheel");
                }
            }
        }
        assert sawItem : "no ITEM part produced from raw body: " + parts;
        assert sawTrailingName : "model-written name missing after item part: " + parts;

        // 3) plain body must not produce icon pieces (GUI would then draw a bare icon with no text)
        List<RecipeEmbed.Part> plainParts = RecipeEmbed.parts(plain, 0);
        for (RecipeEmbed.Part p : plainParts) {
            assert !p.isItem() : "plain body produced an ITEM part: " + plainParts;
        }

        // 4) marker with no trailing name still becomes an ITEM piece (icon-only case is allowed)
        List<RecipeEmbed.Part> bare = RecipeEmbed.parts("Use {{item:create:water_wheel}} in flowing water", 0);
        assert bare.stream().anyMatch(RecipeEmbed.Part::isItem) : "bare marker not split: " + bare;

        String loose = RecipeEmbed.stripMarkers("用 {{item:stone}} 合成");
        assert !loose.contains("{{item:") && !loose.contains("[[item:") : "marker leaked: " + loose;

        System.out.println("DisplayBodyCheck OK");
    }
}

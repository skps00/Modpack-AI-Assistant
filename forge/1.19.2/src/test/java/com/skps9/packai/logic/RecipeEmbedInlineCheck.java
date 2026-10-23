package com.skps9.packai.logic;

import java.util.function.Function;

/** Inline item markers in prose → display names; headless with -ea. */
public final class RecipeEmbedInlineCheck {
    private RecipeEmbedInlineCheck() {}

    public static void main(String[] args) {
        Function<String, String> names = id -> {
            if ("minecraft:stone".equals(id)) {
                return "Stone";
            }
            return id;
        };
        try {
            RecipeEmbed.setInlineNameLookupForTests(names);

            String dbl = RecipeEmbed.normalizeInlineMarkers("a {{item:minecraft:stone}} b", names);
            assert !dbl.contains("{{") : dbl;
            assert dbl.contains("Stone") : dbl;

            String sgl = RecipeEmbed.normalizeInlineMarkers("a {item:minecraft:stone} b", names);
            assert !sgl.contains("{{") : sgl;
            assert !sgl.contains("{item:") : sgl;
            assert sgl.contains("Stone") : sgl;

            String icon = RecipeEmbed.normalizeInlineMarkers("pump ({item icon} Fluid Pipe)");
            assert "pump (Fluid Pipe)".equals(icon) : icon;

            String title = "[[item:minecraft:stone]]\n1. step";
            assert title.equals(RecipeEmbed.normalizeInlineMarkers(title)) : RecipeEmbed.normalizeInlineMarkers(title);

            assert "{{RECIPE}}".equals(RecipeEmbed.normalizeInlineMarkers("{{RECIPE}}"));
            assert "{foo}".equals(RecipeEmbed.normalizeInlineMarkers("{foo}"));

            StringBuilder huge = new StringBuilder();
            for (int i = 0; i < 2000; i++) {
                huge.append("x {{item:minecraft:stone}} ");
            }
            String hugeOut = RecipeEmbed.normalizeInlineMarkers(huge.toString(), names);
            assert hugeOut.length() > 0 && hugeOut.length() < huge.length() : hugeOut.length();

            String plain = "no markers here.";
            assert plain.equals(RecipeEmbed.normalizeInlineMarkers(plain));
            assert plain.equals(RecipeEmbed.normalizeInlineMarkers(RecipeEmbed.normalizeInlineMarkers(plain)));

            System.out.println("RecipeEmbedInlineCheck OK");
        } finally {
            RecipeEmbed.setInlineNameLookupForTests(null);
        }
    }
}

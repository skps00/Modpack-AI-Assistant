package com.skps9.packai.logic;

import java.util.List;

/** Runnable check: mod-only web result filter + query shaping. */
public final class WebSearchCheck {
    private WebSearchCheck() {}

    public static void main(String[] args) {
        String q = WebSearch.buildQuery("how to make alloy", List.of("create"), new ItemRef("create:andesite_alloy", "Alloy"));
        assert q.contains("Minecraft mod") : q;
        assert q.contains("create") : q;

        assert WebSearch.isModRelated("https://www.curseforge.com/minecraft/mc-mods/create", "Create", "");
        assert WebSearch.isModRelated("https://modrinth.com/mod/jei", "JEI", "");
        assert !WebSearch.isModRelated("https://www.bbc.com/news", "Sports", "football scores");

        List<WebSearch.Hit> filtered = WebSearch.filterModOnly(List.of(
                new WebSearch.Hit("Create", "https://modrinth.com/mod/create", "tech mod"),
                new WebSearch.Hit("Weather", "https://weather.com", "rain tomorrow")
        ));
        assert filtered.size() == 1 : filtered;
        assert filtered.get(0).title().equals("Create");

        // partial-pack wording
        String block = WebSearch.formatForLlm(filtered, true);
        assert block.contains("其他部分可能已魔改") : block;

        System.out.println("WebSearchCheck OK");
    }
}

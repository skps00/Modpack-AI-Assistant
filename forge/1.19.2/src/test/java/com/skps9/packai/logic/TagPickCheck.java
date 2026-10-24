package com.skps9.packai.logic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.skps9.packai.client.jei.IngredientReqHints;

public final class TagPickCheck {
    private TagPickCheck() {}

    public static void main(String[] args) {
        Set<String> group = Set.of("minecraft:slime_ball", "tconstruct:sky_slime_ball");

        Map<String, Set<String>> oneExactOneWrong = new LinkedHashMap<>();
        oneExactOneWrong.put("#bookshelf:slime_balls", group);
        oneExactOneWrong.put(
                "#gag:labeling_tool_charges",
                Set.of("minecraft:slime_ball", "tconstruct:earth_slime_ball"));
        assert "#bookshelf:slime_balls".equals(IngredientReqHints.pickExactTag(oneExactOneWrong, group));

        Map<String, Set<String>> bothWrong = new LinkedHashMap<>();
        bothWrong.put("#a:one", Set.of("minecraft:slime_ball"));
        bothWrong.put("#b:two", Set.of("minecraft:slime_ball", "other:item"));
        assert IngredientReqHints.pickExactTag(bothWrong, group) == null;

        Map<String, Set<String>> twoExact = new LinkedHashMap<>();
        twoExact.put("#z:slimes", group);
        twoExact.put("#a:slimes", group);
        assert "#a:slimes".equals(IngredientReqHints.pickExactTag(twoExact, group));

        assert IngredientReqHints.pickExactTag(oneExactOneWrong, Set.of()) == null;
        assert IngredientReqHints.pickExactTag(Map.of(), group) == null;

        System.out.println("check TagPickCheck OK");
    }
}

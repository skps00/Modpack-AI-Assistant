package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Non-focus per-item card cap (uses≤1, output≤2, total≤2, output first). Run with -ea. */
public final class NonFocusCardCapCheck {
    private NonFocusCardCapCheck() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        nonFocusUsesThreeToOne();
        nonFocusOutputThreeToTwo();
        mixedOutputFirstTotalTwo();
        focusUsesUnlimited();
        negativeControlDisabled();
        System.out.println("NonFocusCardCapCheck OK");
    }

    private static void nonFocusUsesThreeToOne() {
        String focus = "productivebees:spawn_egg_ashy_mining_bee";
        String other = "productivebees:bee_cage";
        List<RecipeCard> in = List.of(useCard("A"), useCard("B"), useCard("C"));
        List<RecipeCard> out = NonFocusCardCap.capToolRole(focus, other, "uses", in, 0, 0);
        assert out.size() == 1 : out;
        assert "A".equals(out.get(0).categoryTitle()) : out;
        System.out.println("nonFocusUsesThreeToOne OK");
    }

    private static void nonFocusOutputThreeToTwo() {
        String focus = "productivebees:spawn_egg_ashy_mining_bee";
        String other = "productivebees:bee_cage";
        List<RecipeCard> in = List.of(outCard("O1"), outCard("O2"), outCard("O3"));
        List<RecipeCard> out = NonFocusCardCap.capToolRole(focus, other, "output", in, 0, 0);
        assert out.size() == 2 : out;
        System.out.println("nonFocusOutputThreeToTwo OK");
    }

    private static void mixedOutputFirstTotalTwo() {
        String focus = "minecraft:bee_spawn_egg";
        String other = "minecraft:bee_cage";
        List<RecipeCard> in = new ArrayList<>();
        in.add(outCard("out"));
        in.add(useCard("u1"));
        in.add(useCard("u2"));
        List<RecipeCard> out = NonFocusCardCap.capItemCards(focus, other, in);
        assert out.size() == 2 : out;
        assert out.get(0).categoryTitle().equals("out") : out;
        assert out.get(1).isInputUse() : out;
        System.out.println("mixedOutputFirstTotalTwo OK");
    }

    private static void focusUsesUnlimited() {
        String focus = "productivebees:bee_cage";
        List<RecipeCard> in = List.of(useCard("A"), useCard("B"), useCard("C"));
        List<RecipeCard> out = NonFocusCardCap.capToolRole(focus, focus, "uses", in, 0, 0);
        assert out.size() == 3 : out;
        System.out.println("focusUsesUnlimited OK");
    }

    private static void negativeControlDisabled() {
        boolean prev = NonFocusCardCap.enabled;
        NonFocusCardCap.enabled = false;
        try {
            String focus = "productivebees:spawn_egg_ashy_mining_bee";
            String other = "productivebees:bee_cage";
            List<RecipeCard> in = List.of(useCard("A"), useCard("B"), useCard("C"));
            List<RecipeCard> out = NonFocusCardCap.capToolRole(focus, other, "uses", in, 0, 0);
            assert out.size() == 3 : "negative control: cap disabled must keep all uses";
        } finally {
            NonFocusCardCap.enabled = prev;
        }
        System.out.println("negativeControlDisabled OK");
    }

    private static RecipeCard useCard(String label) {
        return RecipeCard.flow(
                        label,
                        List.of(),
                        List.of(),
                        List.of(new ItemStack(Items.HONEYCOMB)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of())
                .withFocusRole(RecipeCard.FocusRole.INPUT);
    }

    private static RecipeCard outCard(String label) {
        return RecipeCard.flow(
                label,
                List.of(),
                List.of(),
                List.of(new ItemStack(Items.HONEYCOMB)),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}

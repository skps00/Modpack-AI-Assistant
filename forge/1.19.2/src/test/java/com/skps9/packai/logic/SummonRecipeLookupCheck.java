package com.skps9.packai.logic;

import java.util.List;

/** Official name / loot token → summon FACT. Run with -ea. No Minecraft. */
public final class SummonRecipeLookupCheck {
    private SummonRecipeLookupCheck() {}

    public static void main(String[] args) {
        String nameHit = SummonRecipeLookup.factLine(
                "how to summon Foo", List.of("Summoned Foo"));
        assert nameHit.equals("summon: Summoned Foo") : nameHit;

        String miss = SummonRecipeLookup.factLine(
                "how to craft a sword", List.of("Summoned Foo"));
        assert miss.isEmpty() : miss;

        String lootHit = SummonRecipeLookup.factLine(
                "what drops this", List.of("Summoned Dreadful Imp"), "Dreadful Dirt");
        assert lootHit.equals("summon: Summoned Dreadful Imp") : lootHit;

        String noInvent = SummonRecipeLookup.factLine(
                "rotten flesh loot", List.of("Zombie"), "Rotten Flesh");
        assert noInvent.isEmpty() : noInvent;
        assert !noInvent.contains("minecraft:") : noInvent;

        String empty = SummonRecipeLookup.factLine("summon Foo", List.of());
        assert empty.isEmpty() : empty;

        System.out.println("SummonRecipeLookupCheck OK");
    }
}

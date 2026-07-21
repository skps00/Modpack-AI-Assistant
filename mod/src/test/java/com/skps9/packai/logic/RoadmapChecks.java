package com.skps9.packai.logic;

/** Runnable checks for item marker parsing and craft priority. */
public final class RoadmapChecks {
    private RoadmapChecks() {}

    public static void main(String[] args) {
        String raw = "好的，用自動攪拌機<!--packai:items=evilcraft:environmental_accumulator,minecraft:dirt-->";
        assert ItemResolver.stripMarker(raw).equals("好的，用自動攪拌機");
        assert ItemResolver.extractIds(raw).contains("evilcraft:environmental_accumulator");

        assert CraftPriority.categoryTier("Crafting Table") < CraftPriority.categoryTier("Automatic Stirrer");
        assert ReplySources.ensure("hello", List.of("JEI")).contains("【來源】JEI");
        assert ReplySources.ensure("done\n\n【來源】JEI", List.of("任務書")).equals("done\n\n【來源】JEI");
        assert ReplySources.build(true, false, false, false, false).contains("JEI");

        System.out.println("RoadmapChecks OK");
    }
}

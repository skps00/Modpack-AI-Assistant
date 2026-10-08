package com.skps9.packai.logic;

/**
 * Fixture: player-facing scrub humanises raw loot/structure ids; model-facing lootLine keeps raw.
 */
public final class RawIdScrubFixtureCheck {
    private RawIdScrubFixtureCheck() {}

    public static void main(String[] args) {
        case1("掉落表：chests/abandoned_temple/abandoned_temple_entrance",
                "掉落表：", "abandoned temple entrance");
        case1("Loot table: chests/forge",
                "Loot table: ", "forge");
        case1("掉落表：gameplay/transmutation_table_uncommon",
                "掉落表：", "transmutation table uncommon");
        case1("掉落表：twilightforest:structures/well",
                "掉落表：", "well");
        case1("Loot table: chests/lich_tower",
                "Loot table: ", "lich tower");

        // Negative control: model-facing Plainify.lootLine still embeds raw path.
        String modelFacing = Plainify.lootLine("en_us", "minecraft:stone", "chests/village/moon/blacksmith");
        assert modelFacing.contains("chests/village/moon/blacksmith")
                : "model-facing lootLine must keep raw path, got: " + modelFacing;

        System.out.println("RawIdScrubFixtureCheck OK");
    }

    private static void case1(String input, String prefix, String label) {
        String out = AskReplyScrub.scrubPromptEcho(input);
        assert AskReplyScrub.scanRawIdShapes(out).isEmpty()
                : "raw shapes remain after scrub: " + out;
        assert out.startsWith(prefix)
                : "non-raw prefix changed; expected start " + prefix + " got: " + out;
        assert out.contains(label)
                : "missing human label '" + label + "' in: " + out;
        // Non-raw part byte-identical: only the raw id segment may change.
        String expected = prefix + label;
        assert out.equals(expected)
                : "expected exact '" + expected + "' got: " + out;
    }
}

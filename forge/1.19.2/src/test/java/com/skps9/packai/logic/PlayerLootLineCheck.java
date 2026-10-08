package com.skps9.packai.logic;

/**
 * Player-facing loot line humanises any non-blocks/ table id; model-facing lootLine keeps raw.
 */
public final class PlayerLootLineCheck {
    private PlayerLootLineCheck() {}

    public static void main(String[] args) {
        String desert = Plainify.playerLootLine("zh_tw", "minecraft:diamond", "archaeology/desert_pyramid");
        assert desert.contains("desert pyramid") : desert;
        assert !desert.contains("archaeology/") : desert;

        String bath = Plainify.playerLootLine("zh_tw", "minecraft:amethyst_shard", "chests/bathhouse/bathhouse_normal");
        assert bath.contains("bathhouse normal") : bath;
        assert !bath.contains("chests/") : bath;

        String rare = Plainify.playerLootLine("zh_tw", "minecraft:diamond", "gameplay/transmutation_table_rare");
        assert rare.contains("transmutation table rare") : rare;

        String blocksPlayer = Plainify.playerLootLine("zh_tw", "minecraft:diamond", "blocks/ritual_brazier");
        String blocksModel = Plainify.lootLine("zh_tw", "minecraft:diamond", "blocks/ritual_brazier");
        assert blocksPlayer.equals(blocksModel) : "blocks/ regression: player=" + blocksPlayer + " model=" + blocksModel;

        String modelFacing = Plainify.lootLine("zh_tw", "minecraft:diamond", "archaeology/desert_pyramid");
        assert modelFacing.contains("archaeology/desert_pyramid")
                : "model-facing lootLine must keep raw path, got: " + modelFacing;

        System.out.println("PlayerLootLineCheck OK");
    }
}

package com.skps9.packai.logic;

import java.util.function.Function;

/**
 * §3 seven (table, itemId) pairs plus {@code jarLoot} blocks/ raw-path seal.
 */
public final class LootLineHumanizeCheck {
    private LootLineHumanizeCheck() {}

    public static void main(String[] args) {
        Function<String, String> prev = OfficialDisplay.lookup;
        try {
            OfficialDisplay.lookup = id -> switch (id) {
                case "ars_nouveau:ritual_brazier" -> "仪式火盆";
                case "farmersdelight:rope" -> "绳索";
                default -> "";
            };
            String lang = "zh_cn";
            String generic = ReplyLang.lootTableGeneric(lang);

            String hit = Plainify.lootLine(lang, "ars_nouveau:ritual_brazier", "blocks/ritual_brazier");
            assert hit.equals(ReplyLang.lootTableBlock(lang, "仪式火盆")) : hit;
            assert hit.contains("仪式火盆") && !hit.contains("blocks/") : hit;

            String otherItem = Plainify.lootLine(lang, "minecraft:stone", "blocks/ritual_brazier");
            assert otherItem.equals(generic) && !otherItem.contains("blocks/") : otherItem;

            String rope = Plainify.lootLine(lang, "farmersdelight:rope", "blocks/rope");
            assert rope.equals(ReplyLang.lootTableBlock(lang, "绳索")) && !rope.contains("blocks/") : rope;

            String deep = Plainify.lootLine(lang, "minecraft:ice", "blocks/special/ice");
            assert deep.equals(generic) && !deep.contains("blocks/") : deep;

            String chest = Plainify.lootLine(lang, "minecraft:stone", "chests/village/toolsmith");
            assert chest.equals(ReplyLang.lootTableObtain(lang, "chests/village/toolsmith")) : chest;

            String withNs = Plainify.lootLine(lang, "ars_nouveau:ritual_brazier", "ns:blocks/ritual_brazier");
            assert withNs.equals(ReplyLang.lootTableBlock(lang, "仪式火盆")) && !withNs.contains("blocks/") : withNs;

            OfficialDisplay.lookup = id -> "";
            String unnamed = Plainify.lootLine(lang, "ars_nouveau:ritual_brazier", "blocks/ritual_brazier");
            assert unnamed.equals(generic) && !unnamed.contains("blocks/") : unnamed;

            String jar = ReplyLang.jarLoot(lang, "blocks/x");
            assert jar.equals(generic) && !jar.contains("blocks/") : jar;
        } finally {
            OfficialDisplay.lookup = prev;
        }
        System.out.println("LootLineHumanizeCheck OK");
    }
}

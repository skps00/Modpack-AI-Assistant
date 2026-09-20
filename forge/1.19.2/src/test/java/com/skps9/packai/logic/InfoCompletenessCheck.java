package com.skps9.packai.logic;

import java.util.List;

/** Headless gap-block insert. No Minecraft bootstrap. */
public final class InfoCompletenessCheck {
    private InfoCompletenessCheck() {}

    public static void main(String[] args) {
        byteIdentical();
        threeLangsBeforeFooter();
        tokenHit();
        pathSegment();
        quotedArrow();
        scrubKeepsGap();
        gapKeepsWorldgenWhenOnlyMarker();
        gapDropsWorldgenWhenProseMentions();
        System.out.println("InfoCompletenessCheck OK");
    }

    private static void byteIdentical() {
        String answer = "craft only";
        assert InfoCompleteness.append(answer, List.of(), "zh_tw") == answer;
        assert InfoCompleteness.append(answer, null, "en_us") == answer;
        assert InfoCompleteness.append(null, List.of(), "zh_cn") == null;
    }

    private static void threeLangsBeforeFooter() {
        List<String> gaps = List.of(
                "Loot table: minecraft:chests/simple_dungeon",
                "used in crafting -> minecraft:stick",
                "biome minecraft:plains");
        for (String lang : List.of("zh_tw", "zh_cn", "en_us")) {
            String header = ReplyLang.infoGapHeader(lang);
            assert header != null && !header.isBlank() && !header.equals("packai.reply.info_gap_header") : header;
            String answer = "synth only\n" + footer(lang);
            String out = InfoCompleteness.append(answer, gaps, lang);
            assert out.startsWith("synth only\n") : out;
            assert out.contains(header) : out;
            for (String gap : gaps) {
                assert out.contains(gap) : gap + " missing in " + out;
            }
            int headerAt = out.indexOf(header);
            int footAt = out.indexOf(footer(lang));
            assert headerAt >= 0 && footAt > headerAt : out;
            assert out.endsWith(footer(lang)) : out;
            String noFoot = "synth only";
            String appended = InfoCompleteness.append(noFoot, gaps, lang);
            assert appended.startsWith(noFoot) : appended;
            assert appended.contains(header);
        }
    }

    private static void tokenHit() {
        String covered = "see minecraft:chests/simple_dungeon here";
        List<String> gaps = List.of(
                "Loot table: minecraft:chests/simple_dungeon",
                "biome minecraft:plains");
        String out = InfoCompleteness.append(covered + "\n[Sources] JEI", gaps, "en_us");
        assert !out.contains("minecraft:chests/simple_dungeon\n") && !out.contains("Loot table:") : out;
        assert out.contains("minecraft:plains") : out;
        String missed = InfoCompleteness.append(
                "see minecraft:chests/no_such_table here\n[Sources] JEI", gaps, "en_us");
        assert missed.contains("Loot table: minecraft:chests/simple_dungeon") : missed;
        String allHit = "minecraft:stick";
        assert InfoCompleteness.append(allHit, List.of("x minecraft:stick"), "en_us") == allHit;
        String cardAnswer = "body [[recipe_card:2]]";
        String cardOut = InfoCompleteness.append(
                cardAnswer, List.of("bad [card:1] minecraft:stone", "ok minecraft:dirt"), "en_us");
        assert !cardOut.contains("[card:") : cardOut;
        assert cardOut.contains("[[recipe_card:2]]") : cardOut;
        assert cardOut.contains("minecraft:dirt") : cardOut;
        assert count(cardOut, "[[recipe_card:") == count(cardAnswer, "[[recipe_card:");
    }

    private static void pathSegment() {
        String drop = InfoCompleteness.append(
                "1. Loot: it can be found in moon village blacksmith chests.",
                List.of("Loot table: chests/village/moon/blacksmith"),
                "en_us");
        assert !drop.contains("Loot table:") : drop;
        String keep = InfoCompleteness.append(
                "unrelated text about furnaces",
                List.of("Loot table: chests/village/plains/house"),
                "en_us");
        assert keep.contains("Loot table: chests/village/plains/house") : keep;
    }

    private static void quotedArrow() {
        String gap = "used in smelting -> \"calorite ingot\"";
        String drop = InfoCompleteness.append(
                "Smelt or blast it to get a Calorite Ingot",
                List.of(gap),
                "en_us");
        assert !drop.contains("used in smelting") : drop;
        String keep = InfoCompleteness.append(
                "unrelated furnace text",
                List.of(gap),
                "en_us");
        assert keep.contains("used in smelting") : keep;
    }

    private static void scrubKeepsGap() {
        String answer = "craft text [[recipe_card:1]]\n[\u4f86\u6e90] wait";
        String zh = "only craft\n\u3010\u4f86\u6e90\u3011JEI";
        List<String> gaps = List.of("Loot table: minecraft:chests/simple_dungeon");
        String appended = InfoCompleteness.append(zh, gaps, "zh_tw");
        int cards = count(appended, "[[recipe_card:");
        String scrubbed = AskReplyScrub.stripDuplicateSectionHeaders(appended);
        scrubbed = AskReplyScrub.stripFactChrome(scrubbed);
        scrubbed = AskReplyScrub.scrubInternalFieldEcho(scrubbed);
        assert scrubbed.contains(ReplyLang.infoGapHeader("zh_tw")) : scrubbed;
        assert scrubbed.contains("minecraft:chests/simple_dungeon") : scrubbed;
        assert count(scrubbed, "[[recipe_card:") == cards;
        String without = AskReplyScrub.scrubInternalFieldEcho(
                AskReplyScrub.stripFactChrome(AskReplyScrub.stripDuplicateSectionHeaders(zh)));
        assert !without.contains(ReplyLang.infoGapHeader("zh_tw")) : without;
        assert answer.contains("[[recipe_card:");
    }

    private static void gapKeepsWorldgenWhenOnlyMarker() {
        String gap = "World gen: ad_astra:moon_desh_ore | Biome: ad_astra:lunar_wastelands | Y level: -80..80";
        String out = InfoCompleteness.append(
                "[[item:ad_astra:moon_desh_ore]] You can craft things with it.",
                List.of(gap),
                "en_us");
        assert out.contains(gap) : out;
    }

    private static void gapDropsWorldgenWhenProseMentions() {
        String gap = "World gen: ad_astra:moon_desh_ore | Biome: ad_astra:lunar_wastelands | Y level: -80..80";
        String out = InfoCompleteness.append(
                "[[item:ad_astra:moon_desh_ore]] Mine it in the Lunar Wastelands, height -80 to 80.",
                List.of(gap),
                "en_us");
        assert !out.contains(gap) : out;
    }

    private static String footer(String lang) {
        if ("en_us".equals(lang)) {
            return "[Sources] JEI";
        }
        if ("zh_cn".equals(lang)) {
            return "\u3010\u6765\u6e90\u3011JEI";
        }
        return "\u3010\u4f86\u6e90\u3011JEI";
    }

    private static int count(String text, String needle) {
        int n = 0;
        int from = 0;
        while (from <= text.length() - needle.length()) {
            int i = text.indexOf(needle, from);
            if (i < 0) {
                return n;
            }
            n++;
            from = i + needle.length();
        }
        return n;
    }
}

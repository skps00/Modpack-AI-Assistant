package com.skps9.packai.logic;

import java.util.Locale;

/** worldgenOre must not surface raw tag ids; tag-only rows become empty. */
public final class WorldgenTagFilterCheck {
    private WorldgenTagFilterCheck() {}

    public static void main(String[] args) {
        allTagsReturnsEmpty();
        mixedKeepsHumanFields();
        plainFeatureStillShown();
        labeledTagPrefixSkipped();
        negativeControlWithoutFilterWouldLeak();
        System.out.println("WorldgenTagFilterCheck OK");
    }

    private static void allTagsReturnsEmpty() {
        String out = ReplyLang.worldgenOre(
                "zh_tw",
                "#ars_nouveau:archwood_trees",
                "#minecraft:is_overworld",
                null,
                null,
                null,
                null);
        assert out.isEmpty() : out;
        System.out.println("allTagsReturnsEmpty OK");
    }

    private static void mixedKeepsHumanFields() {
        String out = ReplyLang.worldgenOre(
                "zh_tw", "#x:y", null, "-64~320", null, null, "主世界");
        assert !out.contains("#") : out;
        assert out.contains("-64~320") : out;
        assert out.contains("主世界") : out;
        assert !out.contains("世界生成：") || !out.contains("#x:y") : out;
        System.out.println("mixedKeepsHumanFields OK");
    }

    private static void plainFeatureStillShown() {
        String out = ReplyLang.worldgenOre(
                "zh_tw", "ars_nouveau:archwood_trees", null, null, null, null, null);
        assert out.contains("ars_nouveau:archwood_trees") : out;
        System.out.println("plainFeatureStillShown OK");
    }

    private static void labeledTagPrefixSkipped() {
        String out = ReplyLang.worldgenOre(
                "zh_tw",
                "標籤 #ars_nouveau:archwood_trees",
                "tag #minecraft:is_overworld",
                null,
                null,
                null,
                null);
        assert out.isEmpty() : out;
        System.out.println("labeledTagPrefixSkipped OK");
    }

    /** Pre-fix formatting would have leaked # into the completeness line. */
    private static void negativeControlWithoutFilterWouldLeak() {
        String leaky = harnessWorldgenOreWithoutTagFilter(
                "zh_tw",
                "#ars_nouveau:archwood_trees",
                "#minecraft:is_overworld",
                null,
                null,
                null,
                null);
        assert !leaky.isEmpty() && leaky.contains("#") : leaky;
        assert ReplyLang.worldgenOre(
                        "zh_tw",
                        "#ars_nouveau:archwood_trees",
                        "#minecraft:is_overworld",
                        null,
                        null,
                        null,
                        null)
                .isEmpty();
        System.out.println("negativeControlWithoutFilterWouldLeak OK");
    }

    static String harnessWorldgenOreWithoutTagFilter(
            String code, String feature, String biome, String height, String size, String count, String dim) {
        String template = ReplyLang.tr(code, "packai.reply.worldgen_ore");
        if (template == null || template.isBlank() || template.equals("packai.reply.worldgen_ore")) {
            template = ReplyLang.tr("en_us", "packai.reply.worldgen_ore");
        }
        if (template == null || template.isBlank()) {
            return "";
        }
        String[] parts = template.split("\uFF5C", -1);
        String[] vals = {feature, biome, height, size, count, dim};
        StringBuilder sb = new StringBuilder();
        int n = Math.min(parts.length, vals.length);
        for (int i = 0; i < n; i++) {
            if (vals[i] == null || vals[i].isBlank()) {
                continue;
            }
            String piece;
            try {
                piece = String.format(Locale.ROOT, parts[i], vals[i]);
            } catch (Exception e) {
                continue;
            }
            if (piece.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\uFF5C');
            }
            sb.append(piece);
        }
        return sb.toString();
    }
}

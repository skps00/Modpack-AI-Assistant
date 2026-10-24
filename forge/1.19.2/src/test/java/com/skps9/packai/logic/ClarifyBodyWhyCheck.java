package com.skps9.packai.logic;

import java.util.List;
import java.util.Map;

/**
 * Headless checks for model-written clarify {@code why} sanitization and display.
 */
public final class ClarifyBodyWhyCheck {
    private ClarifyBodyWhyCheck() {}

    public static void main(String[] args) {
        Map<String, String> names = Map.of(
                "create:brass_ingot", "Brass Ingot",
                "create:brass_sheet", "Brass Sheet");

        String zhWhy = "\u4f60\u60f3\u554f\u9ec3\u94dc\u9375\u5b9a\u9ec3\u94dc\u677f\uff1f";
        String whichZh = ClarifyBody.buildFrom(
                "",
                List.of("create:brass_ingot", "create:brass_sheet"),
                names,
                "zh_tw",
                zhWhy);
        assert whichZh.startsWith(zhWhy + "\n") : whichZh;

        String canned = ClarifyBody.buildFrom(
                "",
                List.of("create:brass_ingot", "create:brass_sheet"),
                names,
                "zh_tw");
        String idWhy = "pick one: create:brass_ingot";
        assert ClarifyBody.buildFrom("", List.of("create:brass_ingot", "create:brass_sheet"), names, "zh_tw", idWhy)
                .equals(canned) : idWhy;

        ClarifyBody.WhyPick idPick = ClarifyBody.sanitizeWhy(idWhy);
        assert idPick.text().isEmpty() && "id".equals(idPick.reason())
                : idPick.text() + "|" + idPick.reason();

        assert ClarifyBody.buildFrom("", List.of("create:brass_ingot"), names, "en_us", "see [Sources]")
                .equals(ClarifyBody.buildFrom("", List.of("create:brass_ingot"), names, "en_us"));

        assert ClarifyBody.buildFrom("", List.of("create:brass_ingot"), names, "en_us", "bad [[item:x]]")
                .equals(ClarifyBody.buildFrom("", List.of("create:brass_ingot"), names, "en_us"));

        String golden = ClarifyBody.buildFrom("create:brass_ingot", List.of(), names, "en_us");
        assert golden.equals(ClarifyBody.buildFrom("create:brass_ingot", List.of(), names, "en_us", ""))
                : "empty why must match 4-arg golden";
        assert golden.equals(ClarifyBody.buildFrom("create:brass_ingot", List.of(), names, "en_us", "   "))
                : "blank why must match golden";

        String longZh = "\u4f60".repeat(200);
        ClarifyBody.WhyPick trunc = ClarifyBody.sanitizeWhy(longZh);
        assert trunc.text().length() == 160 && "truncated".equals(trunc.reason()) : trunc;
        String withTrunc = ClarifyBody.buildFrom("", List.of("create:brass_ingot"), names, "zh_tw", longZh);
        assert withTrunc.startsWith(trunc.text()) : withTrunc;

        String enCanned = ClarifyBody.buildFrom("", List.of("create:brass_ingot"), names, "en_us");
        assert ClarifyBody.buildFrom("", List.of("create:brass_ingot"), names, "en_us", zhWhy).equals(enCanned)
                : "en_us + zh why";

        String zhOnly = ClarifyBody.buildFrom("", List.of("create:brass_ingot"), names, "zh_tw", "only english?");
        assert zhOnly.equals(ClarifyBody.buildFrom("", List.of("create:brass_ingot"), names, "zh_tw"))
                : "zh_tw + english why";

        String whyLine = "Which brass item did you mean?";
        String withWhy = ClarifyBody.buildFrom(
                "", List.of("create:brass_ingot", "create:brass_sheet"), names, "en_us", whyLine);
        String withoutWhy = ClarifyBody.buildFrom(
                "", List.of("create:brass_ingot", "create:brass_sheet"), names, "en_us");
        assert countOptions(withWhy) == countOptions(withoutWhy) : withWhy + " vs " + withoutWhy;

        System.out.println("ClarifyBodyWhyCheck OK");
    }

    private static int countOptions(String body) {
        int n = 0;
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) == ')' && i > 0 && Character.isDigit(body.charAt(i - 1))) {
                n++;
            }
        }
        return n;
    }
}

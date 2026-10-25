package com.skps9.packai.logic;

import java.util.List;

/** Post-LLM NBT type hint — deterministic insert before [Sources]. */
public final class NbtTypeHintCheck {
    private NbtTypeHintCheck() {}

    public static void main(String[] args) {
        missingQualifierInserts();
        alreadyMentionedUnchanged();
        emptyOutputSkipped();
        chineseLine();
        negativeControlMentionGuard();
        System.out.println("NbtTypeHintCheck OK");
    }

    private static List<String> qualifiersBismuth() {
        return List.of("Honey Treat");
    }

    private static void missingQualifierInserts() {
        String body = "[[item:productivebees:bee_egg]] Bee Egg\n1. Use an incubator.\n\n[Sources] JEI";
        String out = NbtTypeHint.ensure(body, qualifiersBismuth(), "Bismuth", "en_us");
        assert out.contains("Bismuth") : out;
        assert out.contains("Honey Treat") : out;
        assert out.indexOf("[Sources]") > out.indexOf("Honey Treat") : out;
        assert out.endsWith("[Sources] JEI") : out;
    }

    private static void alreadyMentionedUnchanged() {
        String body =
                "1. Combine Honey Treat and egg for Bismuth bee.\n\n[Sources] JEI";
        String out = NbtTypeHint.ensure(body, qualifiersBismuth(), "Bismuth", "en_us");
        assert body.equals(out) : "expected byte-identical, got:\n" + out;
    }

    private static void emptyOutputSkipped() {
        String body = "1. Step.\n\n[Sources] JEI";
        String out = NbtTypeHint.ensure(body, qualifiersBismuth(), "", "en_us");
        assert body.equals(out) : out;
    }

    private static void chineseLine() {
        String body = "1. 步驟。\n\n【來源】JEI";
        String out = NbtTypeHint.ensure(body, qualifiersBismuth(), "Bismuth", "zh_tw");
        assert out.contains("必須帶有與成品相同的類型") : out;
        assert out.contains("Bismuth") : out;
        assert out.indexOf("【來源】") > out.indexOf("Honey Treat") : out;
    }

    private static void negativeControlMentionGuard() {
        boolean prev = NbtTypeHint.MENTION_GUARD_ENABLED;
        try {
            NbtTypeHint.MENTION_GUARD_ENABLED = false;
            String body =
                    "1. Combine Honey Treat and egg for Bismuth bee.\n\n[Sources] JEI";
            String out = NbtTypeHint.ensure(body, qualifiersBismuth(), "Bismuth", "en_us");
            assert !body.equals(out) : "negative control: guard off must insert duplicate line";
        } finally {
            NbtTypeHint.MENTION_GUARD_ENABLED = prev;
        }
    }
}

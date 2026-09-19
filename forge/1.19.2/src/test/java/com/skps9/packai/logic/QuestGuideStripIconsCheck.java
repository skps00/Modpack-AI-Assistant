package com.skps9.packai.logic;

/**
 * Runnable check: {@link QuestGuide#stripQuestIcons} nested-icon guard (plan §4 T1a–T5).
 * CRASH rows assert no throw only — never the JDK message text.
 */
public final class QuestGuideStripIconsCheck {
    private QuestGuideStripIconsCheck() {}

    public static void main(String[] args) {
        // T1 nested — must not throw
        eq("T1a", "icon: { Icon: \"a\" }", "");
        eq("T1b", "icon:{Icon:\"a\"}", "");
        eq("T1c", "ICON: { Icon: \"a\" }", "");
        eq("T1d", "{ icon:{ Icon:\"x\" } }", "{  }");
        eq("T1e",
                "pre icon: { Count: 1b id: \"ftbquests:custom_icon\" tag: { Icon: \"ftbteams:x\" } } post",
                "pre  post");

        // T2 flat icon (negative control; does not take start < last)
        eq("T2", "icon: \"minecraft:stone\" tail", " tail");
        eq("T2b", "icon: \"a\" item: \"minecraft:stone\" icon: \"b\"", " item: \"minecraft:stone\" ");
        eq("T2c", "icon:", "");
        eq("T2d", "icon: }", "}");
        eq("T2e", "icon: 5 item: \"minecraft:stone\"", "5 item: \"minecraft:stone\"");
        eq("T2f", "icon: \"a\\\"b\" item: \"minecraft:stone\"", " item: \"minecraft:stone\"");

        // T3a unterminated '{' — known tail loss, written down
        eq("T3a",
                "head icon: { Count: 1b tag: { Icon: \"a\" item: \"minecraft:stone\" tail icon: \"b\" end",
                "head ");
        eq("T3b", "head icon: { Count: 1b item: \"minecraft:stone\" tail", "head ");
        eq("T3c", "icon: { Icon: \"a\" }   \\n\\t ", "   \\n\\t ");
        // T3d/T3e existing defect: icon: inside a string is stripped (not P0)
        eq("T3d",
                "desc: \"text icon: more\" item: \"minecraft:stone\"",
                "desc: \"text more\" item: \"minecraft:stone\"");
        eq("T3e", "desc: \"see icon:\"", "desc: \"see ");

        String t4 = QuestGuide.stripQuestIcons(
                "pre icon: { Count: 1b id: \"ftbquests:custom_icon\" tag: { Icon: \"packai:test_icon\" } } post");
        eqGot("T4", t4, "pre  post");
        assert !t4.contains("Icon:") : "T4 Icon residue";
        assert !t4.contains("custom_icon") : "T4 custom_icon residue";

        eq("T5-null", null, "");
        eq("T5-empty", "", "");
        eq("T5-plain", "plain", "plain");

        System.out.println("QuestGuideStripIconsCheck OK");
    }

    private static void eq(String id, String in, String expect) {
        eqGot(id, QuestGuide.stripQuestIcons(in), expect);
    }

    private static void eqGot(String id, String got, String expect) {
        if (!expect.equals(got)) {
            throw new AssertionError(id + " got=[" + got + "]");
        }
    }
}

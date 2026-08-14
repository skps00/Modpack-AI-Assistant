package com.skps9.packai.logic;

/** Runnable check: PURPOSE / SCROLL tags scrubbed; UI markers kept. */
public final class AskReplyScrubCheck {
    private AskReplyScrubCheck() {}

    public static void main(String[] args) {
        String leaked = ""
                + "[SCROLL_EFFECT]\n"
                + "Unlocks hammer schematic.\n"
                + "[SCROLL_MECH]\n"
                + "Place near workbench.\n"
                + "[SCROLL_UNLOCK]\n"
                + "module:basic_hammer\n"
                + "[SCROLL_MATERIALS]\n"
                + "none\n"
                + "[PURPOSE]\n"
                + "tooltip line\n"
                + "[[recipe:mod:tetra:scroll_rolled]]\n"
                + "{{item:minecraft:iron_ingot×2}}\n"
                + "[[item:tetra:scroll_rolled]] Scroll\n";
        String out = AskReplyScrub.scrubPromptEcho(leaked);
        assert !out.contains("[SCROLL_EFFECT]") : out;
        assert !out.contains("[SCROLL_MECH]") : out;
        assert !out.contains("[SCROLL_UNLOCK]") : out;
        assert !out.contains("[SCROLL_MATERIALS]") : out;
        assert !out.contains("[PURPOSE]") : out;
        assert out.contains("Unlocks hammer schematic.") : out;
        assert out.contains("Place near workbench.") : out;
        // UI markers must survive for RecipeEmbed / material inject.
        assert out.contains("[[recipe:mod:tetra:scroll_rolled]]") : out;
        assert out.contains("{{item:minecraft:iron_ingot×2}}") : out;
        assert out.contains("[[item:tetra:scroll_rolled]]") : out;

        String spaced = AskReplyScrub.scrubPromptEcho("[ SCROLL_EFFECT ] effect text");
        assert !spaced.contains("SCROLL_EFFECT") : spaced;
        assert spaced.contains("effect text") : spaced;

        // Guide / variant / ingredient headers also stripped
        String other = AskReplyScrub.scrubPromptEcho("[GUIDE]\nbook\n[VARIANT]\nv1\n[AS_INGREDIENT]\nx");
        assert !other.contains("[GUIDE]") : other;
        assert !other.contains("[VARIANT]") : other;
        assert !other.contains("[AS_INGREDIENT]") : other;
        assert other.contains("book") : other;

        String tool = AskReplyScrub.scrubPromptEcho("[TOOL_BUILD]\npart double/head_left: x");
        assert !tool.contains("[TOOL_BUILD]") : tool;
        assert tool.contains("part double/head_left") : tool;

        String emptyGet = AskReplyScrub.scrubPromptEcho(
                "used as material\n怎么来：\n\n【来源】JEI、物品提示 (PURPOSE)");
        assert !emptyGet.contains("怎么来") : emptyGet;
        assert emptyGet.contains("【来源】JEI、物品提示 (PURPOSE)") : emptyGet;
        assert emptyGet.contains("used as material") : emptyGet;

        String keepGet = AskReplyScrub.scrubPromptEcho(
                "怎么来：\n1. craft at table\n【来源】JEI");
        assert keepGet.contains("怎么来") : keepGet;
        assert keepGet.contains("craft at table") : keepGet;

        String emptyEn = AskReplyScrub.scrubPromptEcho("## How to get\n\n[Sources] JEI");
        assert !emptyEn.toLowerCase().contains("how to get") : emptyEn;
        assert emptyEn.contains("[Sources] JEI") : emptyEn;

        System.out.println("AskReplyScrubCheck OK");
    }
}

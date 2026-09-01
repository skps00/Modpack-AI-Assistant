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

        String tetra = AskReplyScrub.scrubPromptEcho("[TETRA_USE]\nmaterial key=archotech_arcane_steel category=metal");
        assert !tetra.contains("[TETRA_USE]") : tetra;
        assert tetra.contains("material key=archotech_arcane_steel") : tetra;

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

        String yellowDoorTip = ""
                + "独/黄门\n"
                + "按住Y键可单独询问此物品，会清除多选状态。\n"
                + "Hold Y to ask Pack AI about this item alone (clears multi-select)\n"
                + "[shift] +\n"
                + "Hold [shift] + rmb read more\n"
                + "packai.screen.how_to_use\n"
                + "packai.tooltip.think.suffix\n"
                + "||||||||\n"
                + "mota_dlc:yellow_door\n"
                + "消耗黄钥匙开门";
        String cleaned = AskReplyScrub.scrubPackAiTooltipChrome(yellowDoorTip);
        String purpose = "[PURPOSE]\n" + cleaned;
        assert purpose.contains("[PURPOSE]") : purpose;
        assert purpose.contains("独/黄门") : purpose;
        assert purpose.contains("mota_dlc:yellow_door") : purpose;
        assert purpose.contains("消耗黄钥匙开门") : purpose;
        assert !purpose.contains("单独询问") : purpose;
        assert !purpose.contains("ask Pack AI") : purpose;
        assert purpose.contains("[shift]") : purpose;
        assert purpose.contains("Hold [shift] + rmb read more") : purpose;
        assert !purpose.contains("packai.screen.") : purpose;
        assert !purpose.contains("packai.tooltip.") : purpose;
        assert !purpose.contains("||||||||") : purpose;
        String merged = AskReplyScrub.scrubPackAiTooltipChrome(
                "按住 Y 单独询问此物品（会清除多选）\n黄门");
        assert merged.contains("黄门") : merged;
        assert !merged.contains("单独询问") : merged;

        String shiftMix = AskReplyScrub.scrubPackAiTooltipChrome(
                "[Shift] + Right Click to open the chest\n按住 [shift] + 單獨詢問此物");
        assert shiftMix.contains("Right Click to open the chest") : shiftMix;
        assert !shiftMix.contains("單獨詢問") : shiftMix;

        System.out.println("AskReplyScrubCheck OK");
    }
}

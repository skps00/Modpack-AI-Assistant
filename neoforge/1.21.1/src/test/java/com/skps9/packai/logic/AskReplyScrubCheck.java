package com.skps9.packai.logic;

import java.util.List;

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

        String emptyGet = AskReplyScrub.ensureHowToGetBody(
                AskReplyScrub.scrubPromptEcho(
                        "used as material\n怎么来：\n\n【来源】JEI、物品提示 (PURPOSE)"),
                "",
                false,
                "本包找不到取得方式");
        assert emptyGet.contains("怎么来") : emptyGet;
        assert emptyGet.contains("本包找不到取得方式") : emptyGet;
        assert emptyGet.contains("【来源】JEI、物品提示 (PURPOSE)") : emptyGet;
        assert emptyGet.contains("used as material") : emptyGet;

        String keepGet = AskReplyScrub.scrubPromptEcho(
                "怎么来：\n1. craft at table\n【来源】JEI");
        keepGet = AskReplyScrub.ensureHowToGetBody(keepGet, "loot", false, "miss");
        assert keepGet.contains("怎么来") : keepGet;
        assert keepGet.contains("craft at table") : keepGet;
        assert !keepGet.contains("loot") : keepGet;

        String emptyThenUse = AskReplyScrub.ensureHowToGetBody(
                "怎么来：\n\n怎么用：\n器官数值",
                "可以在村庄和古城中的箱子获得",
                false,
                "本包找不到取得方式");
        assert emptyThenUse.contains("可以在村庄和古城中的箱子获得") : emptyThenUse;
        int getAt = emptyThenUse.indexOf("怎么来");
        int lootAt = emptyThenUse.indexOf("可以在村庄");
        int useAt = emptyThenUse.indexOf("怎么用");
        assert getAt >= 0 && lootAt > getAt && useAt > lootAt : emptyThenUse;

        String asMat = AskReplyScrub.ensureHowToGetBody(
                "怎么来：\n\n作为材料 (JEI 按 u)\n机械合成",
                "",
                false,
                "本包找不到取得方式");
        assert asMat.contains("本包找不到取得方式") : asMat;
        assert asMat.contains("作为材料") : asMat;
        assert asMat.indexOf("本包找不到取得方式") < asMat.indexOf("作为材料") : asMat;

        String keepForCards = AskReplyScrub.ensureHowToGetBody(
                "怎么来：\n\n怎么用：\nx",
                "",
                true,
                "本包找不到取得方式");
        assert keepForCards.contains("怎么来") : keepForCards;
        assert !keepForCards.contains("本包找不到取得方式") : keepForCards;

        String emptyEn = AskReplyScrub.ensureHowToGetBody(
                AskReplyScrub.scrubPromptEcho("## How to get\n\n[Sources] JEI"),
                "",
                false,
                "No obtain path found in this pack.");
        assert emptyEn.contains("How to get") : emptyEn;
        assert emptyEn.contains("No obtain path found") : emptyEn;

        String numberedEmptyGet = AskReplyScrub.ensureHowToGetBody(
                "1. 怎么来：\n\n2. 作为材料：在附魔装置合成花",
                "可以在下界中的箱子获得",
                false,
                "本包找不到取得方式");
        assert numberedEmptyGet.contains("可以在下界中的箱子获得") : numberedEmptyGet;
        assert numberedEmptyGet.contains("作为材料") : numberedEmptyGet;
        assert numberedEmptyGet.indexOf("怎么来") < numberedEmptyGet.indexOf("作为材料") : numberedEmptyGet;
        assert numberedEmptyGet.indexOf("可以在下界") < numberedEmptyGet.indexOf("作为材料") : numberedEmptyGet;

        String startAtTwo = AskReplyScrub.ensureHowToGetBody(
                "[[item:momo_dlc:t-02-99]] 空虚之梦\n2. 作为材料：在「附魔装置」中参与合成 芙莉艾丝·花绽放。",
                "可以在下界中的箱子获得",
                false,
                "本包找不到取得方式");
        assert startAtTwo.contains("可以在下界中的箱子获得") : startAtTwo;
        assert startAtTwo.contains("作为材料") : startAtTwo;
        int itemAt = startAtTwo.indexOf("[[item:");
        int oneAt = startAtTwo.indexOf("1.");
        int twoAt = startAtTwo.indexOf("2.");
        int asAt = startAtTwo.indexOf("作为材料");
        assert itemAt >= 0 && oneAt > itemAt && oneAt < asAt : startAtTwo;
        assert twoAt > oneAt : startAtTwo;
        String afterTitle = startAtTwo.substring(startAtTwo.indexOf('\n') + 1).stripLeading();
        assert afterTitle.startsWith("1.") : startAtTwo;

        String orphanTwo = AskReplyScrub.fixOrphanLeadingList("2. 作为材料：合成花\n3. 怎么用：穿戴");
        assert orphanTwo.startsWith("1.") : orphanTwo;
        assert orphanTwo.contains("2. 怎么用") : orphanTwo;
        assert !orphanTwo.contains("3.") : orphanTwo;

        String keepSeq = AskReplyScrub.fixOrphanLeadingList("1. 怎么来：箱子\n2. 作为材料：花");
        assert keepSeq.contains("1. 怎么来") : keepSeq;
        assert keepSeq.contains("2. 作为材料") : keepSeq;

        String missAtTwo = AskReplyScrub.ensureHowToGetBody(
                "2. 作为材料：合成花",
                "",
                false,
                "本包找不到取得方式");
        assert missAtTwo.contains("1. 怎么来") : missAtTwo;
        assert missAtTwo.contains("本包找不到取得方式") : missAtTwo;
        assert missAtTwo.contains("2. 作为材料") : missAtTwo;

        String cardsAtTwo = AskReplyScrub.ensureHowToGetBody(
                "2. 作为材料：合成花",
                "",
                true,
                "本包找不到取得方式");
        assert cardsAtTwo.contains("1. 怎么来") : cardsAtTwo;
        assert !cardsAtTwo.contains("本包找不到") : cardsAtTwo;
        assert cardsAtTwo.contains("2. 作为材料") : cardsAtTwo;

        String keepFilled = AskReplyScrub.ensureHowToGetBody(
                "1. 怎么来：已有箱子\n2. 作为材料：花",
                "可以在下界中的箱子获得",
                false,
                "本包找不到取得方式");
        assert keepFilled.contains("已有箱子") : keepFilled;
        assert !keepFilled.contains("可以在下界") : keepFilled;

        String orphanOnly = AskReplyScrub.ensureHowToGetBody("2. 作为材料：合成花", "", false, "");
        assert orphanOnly.stripLeading().startsWith("1.") : orphanOnly;

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
        assert !purpose.contains("[shift]") : purpose;
        assert !purpose.contains("packai.screen.") : purpose;
        assert !purpose.contains("packai.tooltip.") : purpose;
        assert !purpose.contains("||||||||") : purpose;
        String merged = AskReplyScrub.scrubPackAiTooltipChrome(
                "按住 Y 单独询问此物品（会清除多选）\n黄门");
        assert merged.contains("黄门") : merged;
        assert !merged.contains("单独询问") : merged;

        String dsml = ""
                + "< | DSML | | tool_calls>\n"
                + "< | DSML | | invoke name=\"recipe_lookup\">\n"
                + "< | DSML | | parameter name=\"item\" string=\"true\">graveyard:corruption</ | DSML | | parameter>\n"
                + "< | DSML | | parameter name=\"query\" string=\"true\">full</ | DSML | | parameter>\n"
                + "</ | DSML | | invoke>\n"
                + "</ | DSML | | tool_calls>\n";
        String dsmlOut = AskReplyScrub.scrubPromptEcho(dsml);
        assert !dsmlOut.contains("DSML") : dsmlOut;
        assert !dsmlOut.contains("tool_calls") : dsmlOut;
        assert !dsmlOut.contains("recipe_lookup") : dsmlOut;
        assert !dsmlOut.contains("invoke") : dsmlOut;
        assert AskReplyScrub.isVisiblyEmpty(dsmlOut) : dsmlOut;

        String keepMarkers = AskReplyScrub.scrubPromptEcho(
                dsml + "[[recipe:mod:graveyard:corruption]]\n{{item:minecraft:bone×1}}\n[[item:graveyard:corruption]] Essence\n");
        assert keepMarkers.contains("[[recipe:mod:graveyard:corruption]]") : keepMarkers;
        assert keepMarkers.contains("{{item:minecraft:bone×1}}") : keepMarkers;
        assert keepMarkers.contains("[[item:graveyard:corruption]]") : keepMarkers;
        assert !keepMarkers.contains("DSML") : keepMarkers;

        String cardOnly = AskReplyScrub.scrubPromptEcho(dsml + "[[recipe_card:0]]\n");
        assert AskReplyScrub.isVisiblyEmpty(cardOnly) : cardOnly;

        String facts = AskReplyScrub.proseOrFacts(dsml, List.of(
                "[PURPOSE]\n腐化材料，用于仪式",
                "## 怎么来\n合成：骨粉 + 腐肉"));
        assert facts.contains("腐化材料") : facts;
        assert facts.contains("合成") : facts;
        assert !facts.contains("[PURPOSE]") : facts;
        assert !facts.contains("DSML") : facts;
        assert !facts.contains("recipe_lookup") : facts;

        String keepProse = AskReplyScrub.proseOrFacts("用途：腐化祭坛\n" + dsml, List.of("SHOULD_NOT"));
        assert keepProse.contains("用途：腐化祭坛") : keepProse;
        assert !keepProse.contains("SHOULD_NOT") : keepProse;
        assert !keepProse.contains("DSML") : keepProse;

        String fallback = AskReplyScrub.proseOrFacts(dsml, List.of(), "本包對不上");
        assert fallback.contains("本包對不上") : fallback;
        assert !fallback.isBlank();

        System.out.println("AskReplyScrubCheck OK");
    }
}

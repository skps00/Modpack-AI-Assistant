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
        emptyGet = ReplySources.ensure(emptyGet, List.of("JEI"), "zh_tw");
        assert emptyGet.contains("怎么来") : emptyGet;
        assert emptyGet.contains("本包找不到取得方式") : emptyGet;
        assert emptyGet.contains("【来源】JEI、物品提示") : emptyGet;
        assert emptyGet.contains("（物品用途資料）") : emptyGet;
        assert !emptyGet.contains("PURPOSE") : emptyGet;
        assert !emptyGet.contains("()") : emptyGet;
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
        // Keybind hints stay: real tooltips include "Hold [shift] + rmb read more".
        assert purpose.contains("[shift]") : purpose;
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

        // R6: fullwidth vertical line U+FF5C (model leak variant)
        String fwDsml = ""
                + "<\uFF5CDSML\uFF5Ctool_calls>\n"
                + "<\uFF5CDSML\uFF5Cinvoke name=\"recipe_lookup\">\n"
                + "<\uFF5CDSML\uFF5Cparameter name=\"item\" string=\"true\">maodlc:wuren</\uFF5CDSML\uFF5Cparameter>\n"
                + "</\uFF5CDSML\uFF5Cinvoke>\n"
                + "</\uFF5CDSML\uFF5Ctool_calls>\n";
        String fwOut = AskReplyScrub.scrubPromptEcho(fwDsml);
        assert !fwOut.contains("DSML") : fwOut;
        assert !fwOut.contains("tool_calls") : fwOut;
        assert !fwOut.contains("invoke") : fwOut;
        assert !fwOut.contains("parameter") : fwOut;
        assert !fwOut.contains("maodlc:wuren") : fwOut;
        assert AskReplyScrub.isVisiblyEmpty(fwOut) : fwOut;

        // K1: doubled fullwidth pipe + `calls` container
        String k1 = ""
                + "<\uFF5C\uFF5CDSML\uFF5C\uFF5C calls>\n"
                + "<\uFF5C\uFF5CDSML\uFF5C\uFF5Cinvoke name=\"render_recipe_cards\">\n"
                + "<\uFF5C\uFF5CDSML\uFF5C\uFF5Cparameter name=\"item_id\" string=\"true\">minecraft:iron_pickaxe</\uFF5C\uFF5CDSML\uFF5C\uFF5Cparameter>\n"
                + "<\uFF5C\uFF5CDSML\uFF5C\uFF5Cparameter name=\"role\" string=\"true\">output</\uFF5C\uFF5CDSML\uFF5C\uFF5Cparameter>\n"
                + "</\uFF5C\uFF5CDSML\uFF5C\uFF5Cinvoke>\n"
                + "</\uFF5C\uFF5CDSML\uFF5C\uFF5C calls>\n";
        String k1Out = AskReplyScrub.scrubPromptEcho(k1);
        assert !k1Out.contains("DSML") : k1Out;
        assert !k1Out.contains("calls") : k1Out;
        assert !k1Out.contains("invoke") : k1Out;
        assert !k1Out.contains("parameter") : k1Out;
        assert !k1Out.contains("minecraft:iron_pickaxe") : k1Out;
        assert AskReplyScrub.isVisiblyEmpty(k1Out) : k1Out;

        // K2: prose must survive the sweep
        String k2 = k1 + "还可作为材料用于：堂吉诃德";
        String k2Out = AskReplyScrub.scrubPromptEcho(k2);
        assert k2Out.contains("还可作为材料用于：堂吉诃德") : k2Out;
        assert !k2Out.contains("DSML") : k2Out;
        assert !k2Out.contains("invoke") : k2Out;

        // K3: orphan / unclosed container tag
        String k3 = ""
                + "<\uFF5C\uFF5CDSML\uFF5C\uFF5C calls>\n"
                + "还可作为材料用于：堂吉诃德\n";
        String k3Out = AskReplyScrub.scrubPromptEcho(k3);
        assert k3Out.contains("还可作为材料用于：堂吉诃德") : k3Out;
        assert !k3Out.contains("DSML") : k3Out;

        // K4: do not regress markers
        String k4Out = AskReplyScrub.scrubPromptEcho(k1 + "[[recipe_card:0]]");
        assert AskReplyScrub.isVisiblyEmpty(k4Out) : k4Out;

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
        assert !facts.contains("[RECIPE_CARDS]") : facts;
        assert !facts.contains("render_recipe_cards") : facts;
        assert !facts.contains("注意：JEI") : facts;

        // K20: playerSafeFacts drops model-facing lines; keeps tooltip
        List<String> k20 = AskReplyScrub.playerSafeFacts(List.of(
                "Hold [shift] + rmb read more",
                "腐化材料，用于仪式",
                "[RECIPE_CARDS] 以下配方",
                "please call render_recipe_cards",
                "role=quest 是任务奖励"));
        assert k20.contains("Hold [shift] + rmb read more") : k20;
        assert k20.contains("腐化材料，用于仪式") : k20;
        String k20Joined = String.join("\n", k20);
        assert !k20Joined.contains("[RECIPE_CARDS]") : k20Joined;
        assert !k20Joined.contains("render_recipe_cards") : k20Joined;
        assert !k20Joined.contains("role=") : k20Joined;
        assert AskReplyScrub.isPlayerSafeLine("腐化材料");
        assert !AskReplyScrub.isPlayerSafeLine("[RECIPE_CARDS] x");
        assert !AskReplyScrub.isPlayerSafeLine("role=output");

        // K21: doubled fullwidth-pipe DSML (latest.log:562 class) — no instruction leak, no throw
        String k21Dsml = ""
                + "<\uFF5C\uFF5CDSML\uFF5C\uFF5C calls>\n"
                + "<\uFF5C\uFF5CDSML\uFF5C\uFF5Cinvoke name=\"render_recipe_cards\">\n"
                + "<\uFF5C\uFF5CDSML\uFF5C\uFF5Cparameter name=\"role\" string=\"true\">quest</\uFF5C\uFF5CDSML\uFF5C\uFF5Cparameter>\n"
                + "</\uFF5C\uFF5CDSML\uFF5C\uFF5Cinvoke>\n"
                + "</\uFF5C\uFF5CDSML\uFF5C\uFF5C calls>\n";
        List<String> k21Facts = AskReplyScrub.playerSafeFacts(List.of(
                "[PURPOSE]\n腐化材料，用于仪式",
                "[RECIPE_CARDS] 以下",
                "注意：JEI 可能混入同 id",
                "role=quest 是任务",
                "（已完整扫描）"));
        String k21 = AskReplyScrub.proseOrFacts(k21Dsml, k21Facts, "FALLBACK");
        assert !k21.contains("[RECIPE_CARDS]") : k21;
        assert !k21.contains("render_recipe_cards") : k21;
        assert !k21.contains("注意：JEI") : k21;
        assert !k21.contains("【JEI") : k21;
        assert !k21.contains("role=") : k21;
        assert !k21.contains("DSML") : k21;
        assert !k21.contains("必须") : k21;
        assert !k21.contains("禁止") : k21;
        assert k21.contains("腐化材料") : k21;

        String keepProse = AskReplyScrub.proseOrFacts("用途：腐化祭坛\n" + dsml, List.of("SHOULD_NOT"));
        assert keepProse.contains("用途：腐化祭坛") : keepProse;
        assert !keepProse.contains("SHOULD_NOT") : keepProse;
        assert !keepProse.contains("DSML") : keepProse;

        String fallback = AskReplyScrub.proseOrFacts(dsml, List.of(), "本包對不上");
        assert fallback.contains("本包對不上") : fallback;
        assert !fallback.isBlank();

        String matThenGet = AskReplyScrub.ensureHowToGetBody(
                "2. 作为材料：在附魔装置合成花\n3. 取得方式：LootJS 掉落",
                "可以在下界中的箱子获得",
                false,
                "本包找不到取得方式");
        String mtg = matThenGet.stripLeading();
        assert mtg.startsWith("1.") : matThenGet;
        assert matThenGet.contains("取得方式") : matThenGet;
        assert matThenGet.contains("作为材料") : matThenGet;
        assert matThenGet.indexOf("取得方式") < matThenGet.indexOf("作为材料") : matThenGet;
        assert !matThenGet.contains("3.") : matThenGet;

        String unspecified = JeiInfoFacts.stripUnspecifiedMiss(
                "3. 取得方式：LootJS\n本地索引未标明具体由哪种生物或宝箱掉落\n");
        assert !unspecified.contains("未标明") : unspecified;
        assert unspecified.contains("LootJS") : unspecified;

        String nlIn = "携带T-02-99击杀骷髅1%概率获得 \\n下一行";
        String nlOut = AskReplyScrub.scrubPromptEcho(nlIn);
        assert nlOut.contains("获得\n") || nlOut.indexOf('\n') > nlOut.indexOf("获得") : nlOut;
        assert !nlOut.contains("\\n") : nlOut;
        String[] nlLines = nlOut.split("\\n", -1);
        assert nlLines.length >= 2 : nlOut;

        String markerNl = AskReplyScrub.scrubPromptEcho("[[item:mod:dream_rain]] 碎片 \\n后");
        assert markerNl.contains("[[item:mod:dream_rain]]") : markerNl;
        assert markerNl.contains("碎片\n后") || markerNl.contains("碎片\n") : markerNl;

        String crlf = AskReplyScrub.unescapeLiteralNewlines("甲\\r\\n乙");
        assert crlf.equals("甲\n乙") : crlf;

        String dupDump = ""
                + "怎么来：\n"
                + "【本地获取】\"dream rain\"\n"
                + "携带T-02-99击杀骷髅1%概率获得 \\n\n"
                + "【怎么来】\n"
                + "1. JEI 按 R 查看配方\n"
                + "2. 箱子\n";
        String dup = AskReplyScrub.ensureHowToGetBody(
                AskReplyScrub.scrubPromptEcho(dupDump),
                "【本地获取】\"dream rain\"\n携带T-02-99击杀骷髅1%概率获得 \\n",
                false,
                "本包找不到取得方式");
        assert dup.indexOf("怎么来") == dup.lastIndexOf("怎么来") : dup;
        assert !dup.contains("本地获取") && !dup.contains("本地獲取") : dup;
        assert !dup.contains("dream rain") : dup;
        assert dup.contains("JEI 按 R") : dup;
        assert !dup.contains("\\n") : dup;

        String alreadyHuman = AskReplyScrub.ensureHowToGetBody(
                "[[item:mod:x]] 碎片\n【怎么来】\n1. JEI 按 R 查看\n",
                "【本地获取】\"dream rain\"\n携带T-02-99击杀骷髅1%概率获得",
                false,
                "本包找不到取得方式");
        assert alreadyHuman.contains("【怎么来】") : alreadyHuman;
        assert alreadyHuman.indexOf("怎么来") == alreadyHuman.lastIndexOf("怎么来") : alreadyHuman;
        assert !alreadyHuman.contains("本地获取") : alreadyHuman;
        assert alreadyHuman.contains("JEI 按 R") : alreadyHuman;

        String chromeOnly = AskReplyScrub.scrubPromptEcho(
                "【本地获取】\"dream rain\"\n箱子可获得");
        assert !chromeOnly.contains("本地获取") : chromeOnly;
        assert !chromeOnly.contains("dream rain") : chromeOnly;
        assert chromeOnly.contains("箱子可获得") : chromeOnly;

        String yellowDoorTipDup = ""
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
        String cleanedDup = AskReplyScrub.scrubPackAiTooltipChrome(yellowDoorTipDup);
        String purposeDup = "[PURPOSE]\n" + cleanedDup;
        assert purposeDup.contains("[PURPOSE]") : purposeDup;
        assert purposeDup.contains("独/黄门") : purposeDup;
        assert purposeDup.contains("mota_dlc:yellow_door") : purposeDup;
        assert purposeDup.contains("消耗黄钥匙开门") : purposeDup;
        assert !purposeDup.contains("单独询问") : purposeDup;
        assert !purposeDup.contains("ask Pack AI") : purposeDup;
        assert purposeDup.contains("[shift]") : purposeDup;
        assert purposeDup.contains("Hold [shift] + rmb read more") : purposeDup;
        assert !purposeDup.contains("packai.screen.") : purposeDup;
        assert !purposeDup.contains("packai.tooltip.") : purposeDup;
        assert !purposeDup.contains("||||||||") : purposeDup;
        String mergedDup = AskReplyScrub.scrubPackAiTooltipChrome(
                "按住 Y 单独询问此物品（会清除多选）\n黄门");
        assert mergedDup.contains("黄门") : mergedDup;
        assert !mergedDup.contains("单独询问") : mergedDup;

        String shiftMix = AskReplyScrub.scrubPackAiTooltipChrome(
                "[Shift] + Right Click to open the chest\n按住 [shift] + 單獨詢問此物");
        assert shiftMix.contains("Right Click to open the chest") : shiftMix;
        assert !shiftMix.contains("單獨詢問") : shiftMix;

        String dupHeaders = AskReplyScrub.stripDuplicateSectionHeaders(
                "怎样来:\n1. 工作台: 合成。\n2. 直接使用: 右键。\n1. 怎么来 :\n3. 作为材料（召唤祭坛）: 献祭。");
        assert dupHeaders.contains("怎样来:") : dupHeaders;
        assert !dupHeaders.contains("1. 怎么来") : dupHeaders;
        assert dupHeaders.contains("3. 作为材料（召唤祭坛）") : dupHeaders;

        String twiceGet = AskReplyScrub.stripDuplicateSectionHeaders("怎么来:\n步骤一。\n怎么来:\n步骤二。");
        assert twiceGet.indexOf("怎么来:") == twiceGet.lastIndexOf("怎么来:") : twiceGet;
        assert twiceGet.contains("步骤二。") : twiceGet;

        String proseKeep = AskReplyScrub.stripDuplicateSectionHeaders(
                "如果不知道怎么来，可以查 JEI。\n怎么来:\n箱子掉落。");
        assert proseKeep.contains("如果不知道怎么来") : proseKeep;

        String distinct = AskReplyScrub.stripDuplicateSectionHeaders(
                "怎么用:\n手持。\n作为材料:\n合成。\n用途:\n装饰。");
        assert distinct.equals("怎么用:\n手持。\n作为材料:\n合成。\n用途:\n装饰。") : distinct;

        String roleHyphen = AskReplyScrub.scrubInternalFieldEcho("role=quest-as-obtain");
        assert !roleHyphen.contains("-as-obtain") : roleHyphen;
        assert !roleHyphen.contains("as-obtain") : roleHyphen;
        assert roleHyphen.replaceAll("[\\s／/|,;]+", "").isEmpty() : roleHyphen;
        String roleMulti = AskReplyScrub.scrubInternalFieldEcho("role=output, role=quest");
        assert !roleMulti.contains("=quest") : roleMulti;
        assert !roleMulti.contains("quest") : roleMulti;
        assert roleMulti.replaceAll("[\\s／/|,;]+", "").isEmpty() : roleMulti;
        String roleOnly = AskReplyScrub.scrubInternalFieldEcho("role=maintenance-only");
        assert !roleOnly.contains("-only") : roleOnly;
        assert roleOnly.replaceAll("[\\s／/|,;]+", "").isEmpty() : roleOnly;

        assert !AskReplyScrub.hasInternalSourceLeak("【來源】JEI, in-game GUIDE, web search");
        assert !AskReplyScrub.hasInternalSourceLeak("【來源】JEI, in-game guide, web search");
        assert AskReplyScrub.hasInternalSourceLeak("PURPOSE：可飲用");
        assert AskReplyScrub.hasInternalSourceLeak("WORLDGEN：");
        assert AskReplyScrub.hasInternalSourceLeak("[TOOL_BUILD] parts");
        assert AskReplyScrub.hasInternalSourceLeak("Role=output");

        String srcPurpose = AskReplyScrub.scrubInternalFieldEcho("【来源】JEI、物品提示 (PURPOSE)");
        assert srcPurpose.contains("【来源】JEI、物品提示") : srcPurpose;
        assert !srcPurpose.contains("PURPOSE") : srcPurpose;
        assert !srcPurpose.contains("()") : srcPurpose;

        String rolePurpose = AskReplyScrub.scrubInternalFieldEcho(
                "JEI（配方卡 role=output／input）、物品用途資料（PURPOSE：可飲用）");
        assert !rolePurpose.contains("role=") : rolePurpose;
        assert !rolePurpose.contains("PURPOSE") : rolePurpose;
        assert rolePurpose.contains("配方卡") : rolePurpose;
        assert rolePurpose.contains("可飲用") : rolePurpose;
        assert !rolePurpose.contains("()") : rolePurpose;
        assert !rolePurpose.contains("（）") : rolePurpose;

        String wgPair = AskReplyScrub.scrubInternalFieldEcho("A、(WORLDGEN)、B");
        assert !wgPair.contains("WORLDGEN") : wgPair;
        assert !wgPair.contains("()") : wgPair;
        assert wgPair.contains("A、") : wgPair;
        assert wgPair.contains("B") : wgPair;

        String keepParen = AskReplyScrub.scrubInternalFieldEcho("（可飲用）");
        assert keepParen.equals("（可飲用）") : keepParen;
        String keepItem = AskReplyScrub.scrubInternalFieldEcho("{{item:minecraft:stone}}");
        assert keepItem.equals("{{item:minecraft:stone}}") : keepItem;

        String srcTw = AskReplyScrub.translateInternalTokens("【來源】JEI、物品提示（PURPOSE）", "zh_tw");
        assert srcTw.contains("（物品用途資料）") : srcTw;
        assert !srcTw.contains("PURPOSE") : srcTw;

        String wgTw = AskReplyScrub.translateInternalTokens("(WORLDGEN)", "zh_tw");
        assert wgTw.contains("（世界生成資料）") : wgTw;
        assert !wgTw.contains("WORLDGEN") : wgTw;

        String roleOut = AskReplyScrub.translateInternalTokens("role=output", "zh_tw");
        assert roleOut.contains("合成產出") : roleOut;
        String roleQuest = AskReplyScrub.translateInternalTokens("role=quest-as-obtain", "zh_tw");
        assert roleQuest.contains("任務取得") : roleQuest;
        assert !roleQuest.contains("role=") : roleQuest;

        String bodyPurpose = AskReplyScrub.scrubInternalFieldEcho("PURPOSE：可飲用");
        assert !bodyPurpose.contains("PURPOSE") : bodyPurpose;
        assert bodyPurpose.contains("可飲用") : bodyPurpose;

        String keepReal = ReplySources.ensure(
                "1. x\n\n【來源】JEI, in-game GUIDE, web search", List.of("JEI"), "zh_tw");
        assert keepReal.equals("1. x\n\n【來源】JEI, in-game GUIDE, web search") : keepReal;
        assert keepReal.contains("in-game GUIDE") : keepReal;
        assert keepReal.contains("web search") : keepReal;

        String dropUnknown = AskReplyScrub.translateInternalTokens("【來源】JEI role=notarealrole", "zh_tw");
        assert !dropUnknown.contains("role=") : dropUnknown;
        assert !dropUnknown.contains("notarealrole") : dropUnknown;
        assert dropUnknown.contains("JEI") : dropUnknown;

        String toolTw = AskReplyScrub.translateInternalTokens("【來源】[TOOL_BUILD] 零件", "zh_tw");
        assert toolTw.contains("（工具組成資料）") : toolTw;
        assert !toolTw.contains("[") : toolTw;
        String toolEn = AskReplyScrub.translateInternalTokens("【來源】[TOOL_BUILD] 零件", "en_us");
        assert toolEn.contains("(Tool build data)") : toolEn;
        String recipeRole = AskReplyScrub.translateInternalTokens("【來源】JEI（配方卡 role=output）", "zh_tw");
        assert recipeRole.contains("（配方卡 合成產出）") : recipeRole;
        assert !recipeRole.contains("（（") : recipeRole;
        assert !recipeRole.contains("））") : recipeRole;

        // I: structural footer renderer — 16 behaviour cases (zh_tw unless noted).
        String nestedPurpose = AskReplyScrub.translateInternalTokens("（[PURPOSE]）", "zh_tw");
        assert nestedPurpose.contains("（物品用途資料）") : nestedPurpose;
        assert !nestedPurpose.contains("[") : nestedPurpose;
        assert !nestedPurpose.contains("】") : nestedPurpose;
        String dblSquare = AskReplyScrub.translateInternalTokens("[[PURPOSE]]", "zh_tw");
        assert dblSquare.contains("（物品用途資料）") : dblSquare;
        assert !dblSquare.contains("[") : dblSquare;
        String cjkNested = AskReplyScrub.translateInternalTokens("【[PURPOSE]】", "zh_tw");
        assert cjkNested.contains("（物品用途資料）") : cjkNested;
        assert !cjkNested.contains("[") : cjkNested;
        assert !cjkNested.contains("】") : cjkNested;
        String cjkPurpose = AskReplyScrub.translateInternalTokens("【PURPOSE】", "zh_tw");
        assert cjkPurpose.contains("（物品用途資料）") : cjkPurpose;
        assert !cjkPurpose.contains("PURPOSE") : cjkPurpose;
        assert !cjkPurpose.contains("】") : cjkPurpose;
        String roleDuo = AskReplyScrub.translateInternalTokens("role=output、input", "zh_tw");
        assert roleDuo.contains("合成產出、作為材料") : roleDuo;
        assert !roleDuo.contains("input") : roleDuo;
        String roleWs = AskReplyScrub.translateInternalTokens("role=output input", "zh_tw");
        assert roleWs.contains("合成產出、作為材料") : roleWs;
        String rolePipeKeep = AskReplyScrub.translateInternalTokens("role=output｜合成", "zh_tw");
        assert rolePipeKeep.contains("合成產出、合成") : rolePipeKeep;
        assert !rolePipeKeep.contains("role=") : rolePipeKeep;
        String roleQuestTask = AskReplyScrub.translateInternalTokens("role=quest_task | 任務", "zh_tw");
        assert roleQuestTask.contains("任務取得、任務") : roleQuestTask;
        String jeiRolePipe = AskReplyScrub.translateInternalTokens("JEI role=output｜合成", "zh_tw");
        assert jeiRolePipe.contains("JEI、合成產出、合成") : jeiRolePipe;
        String emptyRole = ReplySources.ensure("1. x\n\n【來源】role=", List.of("JEI"), "zh_tw");
        assert !emptyRole.contains("role=") : emptyRole;
        assert emptyRole.contains("【來源】") : emptyRole;
        assert emptyRole.contains("JEI") : emptyRole;
        String fwEq = AskReplyScrub.translateInternalTokens("role＝output", "zh_tw");
        assert fwEq.contains("合成產出") : fwEq;
        assert !fwEq.contains("role") : fwEq;
        String dupLabel = AskReplyScrub.translateInternalTokens("物品用途資料（PURPOSE：可飲用）", "zh_tw");
        assert dupLabel.contains("物品用途資料（可飲用）") : dupLabel;
        assert !dupLabel.contains("物品用途資料（物品用途資料") : dupLabel;
        assert !dupLabel.contains("PURPOSE") : dupLabel;
        String rolePipeSpace = AskReplyScrub.translateInternalTokens("role=output | 合成", "zh_tw");
        assert rolePipeSpace.contains("合成產出、合成") : rolePipeSpace;
        String roleSlash = AskReplyScrub.translateInternalTokens("role=output／input", "zh_tw");
        assert roleSlash.contains("合成產出、作為材料") : roleSlash;
        String roleEn = AskReplyScrub.translateInternalTokens("role=output／input", "en_us");
        assert roleEn.contains("Craft output") : roleEn;
        assert roleEn.contains("used as ingredient") || roleEn.contains("Used as ingredient") : roleEn;
        String toolPartsEn = AskReplyScrub.translateInternalTokens("[TOOL_BUILD] parts", "en_us");
        assert toolPartsEn.contains("(Tool build data)") : toolPartsEn;
        assert toolPartsEn.contains("parts") : toolPartsEn;
        assert !toolPartsEn.contains("[") : toolPartsEn;
        assert !toolPartsEn.contains("]") : toolPartsEn;
        String bodyClear = AskReplyScrub.scrubInternalFieldEcho(
                "PURPOSE IS CLEAR: this is used for crafting.");
        assert bodyClear.equals("PURPOSE IS CLEAR: this is used for crafting.") : bodyClear;
        String bodyColon = AskReplyScrub.scrubInternalFieldEcho("PURPOSE：可飲用");
        assert !bodyColon.contains("PURPOSE") : bodyColon;
        assert bodyColon.contains("可飲用") : bodyColon;
        assert AskReplyScrub.scrubInternalFieldEcho("（可飲用）").equals("（可飲用）");
        assert AskReplyScrub.scrubInternalFieldEcho("(3×3)").equals("(3×3)");
        assert AskReplyScrub.scrubInternalFieldEcho("{{item:minecraft:stone}}")
                .equals("{{item:minecraft:stone}}");
        assert AskReplyScrub.scrubInternalFieldEcho("minecraft:iron_ingot")
                .equals("minecraft:iron_ingot");

        System.out.println("AskReplyScrubCheck OK");
    }
}

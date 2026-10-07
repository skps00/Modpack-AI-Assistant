package com.skps9.packai.logic;

import java.util.List;

/**
 * Slice 1b: corpus A/C golden + B passthrough + D byte-identical + zh_tw fixtures.
 * Input from {@code docs/research/artifacts/2026-09-22-slice1b-jargon-corpus.md}.
 */
public final class InternalJargonCheck {
    private InternalJargonCheck() {}

    public static void main(String[] args) {
        // A + C golden (zh_cn)
        assertZhCn(
                "本包未索引到钻石的世界生成资料，挖矿仍是原版常规途径。",
                "本包未见到钻石的世界生成资料，挖矿仍是原版常规途径。");
        assertZhCn(
                "本包未索引到它的矿脉／世界生成资料，能否自然遇到以实际地形为准",
                "本包未见到它的矿脉／世界生成资料，能否自然遇到以实际地形为准");
        assertZhCn(
                "本包索引未收录它的世界生成、宝箱／掉落或任务取得路径，本地取得资料是空的",
                "本包资料未收录它的世界生成、宝箱／掉落或任务取得路径，本地取得资料是空的");
        assertZhCn(
                "除此之外，本包索引没有这只开胸器的掉落、交易或任务取得路径，所以主要就是上面两条合成。",
                "除此之外，本包资料里没有这只开胸器的掉落、交易或任务取得路径，所以主要就是上面两条合成。");
        assertZhCn(
                "本包的掉落表、钓鱼、交易与脚本索引都没有这只星的取得路径",
                "本包的掉落表、钓鱼、交易与脚本资料里都没有这只星的取得路径");
        assertZhCn(
                "本包的掉落表、宝箱、钓鱼、交易与脚本索引都没有它的取得路径",
                "本包的掉落表、宝箱、钓鱼、交易与脚本资料里都没有它的取得路径");
        assertZhCn(
                "本包索引没有它的掉落、交易或任务取得路径，所以主要就是上面两条合成。",
                "本包资料里没有它的掉落、交易或任务取得路径，所以主要就是上面两条合成。");
        assertZhCn(
                "本包索引没有它的掉落、宝箱、钓鱼、交易、任务或合成路径",
                "本包资料里没有它的掉落、宝箱、钓鱼、交易、任务或合成路径");
        assertZhCn(
                "本包的掉落表、宝箱、钓鱼、交易、任务与脚本索引都没有它的取得路径",
                "本包的掉落表、宝箱、钓鱼、交易、任务与脚本资料里都没有它的取得路径");
        assertZhCn(
                "本包索引没有它的掉落、宝箱、钓鱼、交易或任务取得路径",
                "本包资料里没有它的掉落、宝箱、钓鱼、交易或任务取得路径");
        assertZhCn(
                "本包的掉落表、宝箱、钓鱼、交易与任务索引都没有它的条目",
                "本包的掉落表、宝箱、钓鱼、交易与任务资料里都没有它的条目");
        assertZhCn(
                "本包没有掉落／交易／任务取得路径，合成就是唯一已知来源。",
                "本包没有掉落／交易／任务取得路径，合成就是目前资料见到的来源。");
        assertZhCn(
                "本包的掉落表、宝箱、钓鱼、交易、任务与脚本索引都没有它的取得路径，所以黑暗祭坛就是目前已知的唯一来源。",
                "本包的掉落表、宝箱、钓鱼、交易、任务与脚本资料里都没有它的取得路径，所以黑暗祭坛就是目前资料见到的来源。");

        // B (Slice 1c): no jargon rewrite; must stay byte-identical
        assertIdentical(
                "zh_cn",
                "本地宝箱与器官脚本：暮色森林水井战利品表、下界墓穴宝箱（宝藏肋骨）、citadel 掉落表都有钻石");
        assertIdentical(
                "zh_cn",
                "开箱子：本包掉落表把它放进「地下墓穴宝物箱」（treasure rib）和 citadel 结构的箱子，开箱可得。");

        // D: must not touch
        assertIdentical("zh_cn", "通用知识（非本包覆写）：破坏废墟传送门框架、与猪灵以物易物也能获得。");
        assertIdentical("zh_cn", "本包资料未列用途机制，通用知识（Ars Nouveau 模组，非本包覆写）");
        assertIdentical("zh_cn", "世界生成：矿脉在下层");
        assertIdentical("zh_cn", "破坏 仪式火盆 会掉落");

        // zh_tw fixtures (§F)
        assertZhTw(
                "本包未索引到鑽石的世界生成資料，挖礦仍是原版常規途徑。",
                "本包未見到鑽石的世界生成資料，挖礦仍是原版常規途徑。");
        assertZhTw(
                "本包索引沒有它的掉落、交易或任務取得路徑。",
                "本包資料裡沒有它的掉落、交易或任務取得路徑。");
        assertZhTw(
                "本包的掉落表、寶箱、釣魚、交易與腳本索引都沒有它的取得路徑",
                "本包的掉落表、寶箱、釣魚、交易與腳本資料裡都沒有它的取得路徑");
        assertZhTw(
                "本包索引未收錄它的世界生成資料",
                "本包資料未收錄它的世界生成資料");
        assertZhTw(
                "合成就是唯一已知來源。",
                "合成就是目前資料見到的來源。");

        // en residual
        String en = AskReplyScrub.rewriteInternalJargon(
                "This pack has not indexed worldgen for diamond.", "en_us");
        assert en.equals("This pack has not seen in pack data world generation for diamond.") : en;
        assert !en.toLowerCase().contains("worldgen") : en;
        assert !en.contains("not indexed") : en;

        // Slice 1b wiring: production entry proseOrFacts must rewrite (not helper-only).
        // headless ReplyLang.current() → zh_tw — traditional glyphs required.
        String wired = AskReplyScrub.proseOrFacts(
                "本包索引沒有它的掉落、交易或任務取得路徑，合成就是唯一已知來源。",
                List.of("SHOULD_NOT_APPEAR"), "本包對不上");
        assert !wired.contains("索引") : "wiring: " + wired;
        assert !wired.contains("唯一") : "wiring: " + wired;
        assert !wired.contains("SHOULD_NOT_APPEAR") : "wiring: " + wired;

        System.out.println("InternalJargonCheck OK");
    }

    private static void assertZhCn(String in, String golden) {
        String out = AskReplyScrub.rewriteInternalJargon(in, "zh_cn");
        assert out.equals(golden) : "zh_cn\nin=" + in + "\nout=" + out + "\nwant=" + golden;
        assertNoJargonZhCn(out);
        assert !out.contains("資料") && !out.contains("裡") : "zh_cn mix: " + out;
        assert !out.contains("唯一") : "zh_cn unique: " + out;
    }

    private static void assertZhTw(String in, String golden) {
        String out = AskReplyScrub.rewriteInternalJargon(in, "zh_tw");
        assert out.equals(golden) : "zh_tw\nin=" + in + "\nout=" + out + "\nwant=" + golden;
        assert !out.contains("索引") && !out.contains("未索引") : "zh_tw jargon: " + out;
        assert !out.contains("资料") && !out.contains("里") : "zh_tw mix: " + out;
        assert !out.contains("唯一") : "zh_tw unique: " + out;
    }

    private static void assertIdentical(String lang, String in) {
        String out = AskReplyScrub.rewriteInternalJargon(in, lang);
        assert out.equals(in) : "must be identical\nin=" + in + "\nout=" + out;
    }

    private static void assertNoJargonZhCn(String out) {
        assert !out.contains("未索引") : out;
        assert !out.contains("索引未收录") : out;
        assert !out.contains("索引都没有") : out;
        assert !out.contains("索引没有") : out;
        assert !out.contains("索引") : out;
    }
}

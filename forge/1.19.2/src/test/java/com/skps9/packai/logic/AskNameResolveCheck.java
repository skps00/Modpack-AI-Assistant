package com.skps9.packai.logic;

import java.util.List;

/** Fixture name resolve + summon miss. No Minecraft, no live NFWC. Run with -ea. */
public final class AskNameResolveCheck {
    private AskNameResolveCheck() {}

    public static void main(String[] args) {
        String q = "最初的骑士怎样召唤怎样召唤？";
        assert "最初的骑士".equals(AskNameResolve.nameCore(q)) : AskNameResolve.nameCore(q);
        assert SummonRecipeLookup.isSummonQuestion(q);
        assert HonestMiss.isAcquireOrientedQuestion(q);

        List<AskNameResolve.Label> catalog = List.of(
                new AskNameResolve.Label("minecraft:dirt", "泥土"),
                new AskNameResolve.Label("somebosses:knight_garent_spawn_egg", "最初的骑士刷怪蛋"),
                new AskNameResolve.Label("somebosses:chaos_insignia", "混沌徽章"));
        String egg = AskNameResolve.resolveId(q, catalog);
        assert "somebosses:knight_garent_spawn_egg".equals(egg) : egg;
        assert egg.contains("somebosses") && egg.contains("knight_garent") : egg;

        List<AskNameResolve.Label> withTitle = List.of(
                new AskNameResolve.Label("somebosses:knight_garent_spawn_egg", "最初的骑士刷怪蛋"),
                new AskNameResolve.Label("somebosses:knight_garent", "最初的骑士"));
        String entity = AskNameResolve.resolveId(q, withTitle);
        assert "somebosses:knight_garent".equals(entity) : entity;

        assert AskNameResolve.resolveId("完全不存在的名字怎样召唤", catalog).isEmpty();

        assert "凋灵".equals(AskNameResolve.nameCore("怎样召唤凋灵")) : AskNameResolve.nameCore("怎样召唤凋灵");
        assert AskNameResolve.coreUseful("凋灵");
        assert AskNameResolve.coreUseful("骑士");
        assert "钻石".equals(AskNameResolve.nameCore("钻石怎么来")) : AskNameResolve.nameCore("钻石怎么来");
        assert AskNameResolve.coreUseful("钻石");
        assert "diamond".equals(AskNameResolve.nameCore("how to get diamond"))
                : AskNameResolve.nameCore("how to get diamond");
        assert AskNameResolve.mergeTypedCards("最初的骑士怎样召唤");
        assert AskNameResolve.mergeTypedCards("怎样召唤凋灵");
        assert AskNameResolve.mergeTypedCards("钻石怎么来");

        assert "???".equals(AskNameResolve.nameCore("how to summon ???")) : AskNameResolve.nameCore("how to summon ???");
        assert AskNameResolve.coreUseful("???");
        assert AskNameResolve.nameCore("how to summon?").isEmpty() : AskNameResolve.nameCore("how to summon?");
        List<AskNameResolve.Label> punct = List.of(
                new AskNameResolve.Label("mod:punct_mob", "???"),
                new AskNameResolve.Label("minecraft:dirt", "Dirt"));
        assert "mod:punct_mob".equals(AskNameResolve.resolveId("how to summon ???", punct));
        assert AskNameResolve.resolveId("how to summon ???", catalog).isEmpty();

        List<String> hints = AskNameResolve.relatedHintIds("somebosses:knight_garent_spawn_egg");
        assert hints.contains("somebosses:knight_garent") : hints;

        assert HonestMiss.shouldPinSummonMiss(false, false, q);
        assert !HonestMiss.shouldPinSummonMiss(true, false, q);
        List<String> miss = HonestMiss.summonMissFacts("en_us", List.of());
        String blob = String.join("\n", miss).toLowerCase();
        assert blob.contains("no indexed summon") || blob.contains("do not use web") : miss;
        assert !blob.contains("necronomicon") : miss;
        assert !blob.contains("cataclysm") : miss;
        assert !blob.contains("cursed pyramid") : miss;
        assert !blob.contains("ancient remnant") : miss;

        System.out.println("AskNameResolveCheck OK");
    }
}

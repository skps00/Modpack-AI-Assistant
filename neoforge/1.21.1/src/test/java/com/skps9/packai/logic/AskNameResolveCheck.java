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

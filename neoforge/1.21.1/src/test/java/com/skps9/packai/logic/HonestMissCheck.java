package com.skps9.packai.logic;

import java.util.List;

/** WP2 — HonestMiss pin gate + miss lines (no invent). Run with -ea. */
public final class HonestMissCheck {
    private HonestMissCheck() {}

    public static void main(String[] args) {
        assert HonestMiss.shouldPinAcquireMiss(List.of(), false, "how to get this", "mod:demo");
        assert HonestMiss.shouldPinAcquireMiss(List.of(), false, "如何取得", "pack:item");
        assert !HonestMiss.shouldPinAcquireMiss(
                List.of("Loot: chest"), false, "how to get", "mod:demo");
        assert !HonestMiss.shouldPinAcquireMiss(List.of(), true, "how to get", "mod:demo");
        assert !HonestMiss.shouldPinAcquireMiss(List.of(), false, "what does this do", "mod:demo");
        assert !HonestMiss.shouldPinAcquireMiss(List.of(), false, "how to get", "");
        assert !HonestMiss.shouldPinAcquireMiss(null, false, "how to get", null);

        List<String> miss = HonestMiss.acquireMissFacts("mod:demo", "en_us");
        assert miss.size() == 2 : miss;
        assert miss.get(1).toLowerCase().contains("not indexed") : miss;
        assert miss.get(1).toLowerCase().contains("do not invent") : miss;
        assert HonestMiss.acquireMissFacts("", "en_us").isEmpty();
        assert HonestMiss.acquireMissFacts(null, "zh_tw").isEmpty();

        assert HonestMiss.shouldPinSummonMiss(false, false, "最初的骑士怎样召唤");
        assert !HonestMiss.shouldPinSummonMiss(true, false, "最初的骑士怎样召唤");
        String summonMiss = String.join("\n", HonestMiss.summonMissFacts("en_us", List.of())).toLowerCase();
        assert !summonMiss.contains("necronomicon") && !summonMiss.contains("cataclysm") : summonMiss;

        System.out.println("HonestMissCheck OK");
    }
}

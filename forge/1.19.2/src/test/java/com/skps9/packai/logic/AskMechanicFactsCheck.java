package com.skps9.packai.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * KubeJS mechanic scan + FTB quest_text facts. Fixture strings only. Run with -ea.
 */
public final class AskMechanicFactsCheck {
    private AskMechanicFactsCheck() {}

    static final String REL = "server_scripts/demo.js";

    static final String NEW_STYLE = """
            ItemEvents.rightClicked('mod:demo_item', e => {
                e.player.give('minecraft:diamond')
            })
            """;

    static final String OLD_STYLE = """
            onEvent('item.right_click', e => {
                if (e.item.id == 'mod:old_item') {
                    e.player.tell(Text.black('clicked old relic'))
                }
            })
            """;

    static final String CURIOS_DEATH = """
            EntityEvents.death('minecraft:skeleton', event => {
                if (nbt.ForgeCaps['curios:inventory']) {
                    let curios = nbt.ForgeCaps['curios:inventory'];
                    if (curios.toString().includes('momo_dlc:t-02-99')) {
                        let random = Math.random() * Math.random() * 100
                        if (random <= 1) player.give("momo_dlc:dream_rain");
                    }
                }
            })
            """;

    static final String CHANCE_VAR_LT = """
            ItemEvents.rightClicked('mod:pct100_item', e => {
                let r = Math.random() * 100
                if (r < 5) e.player.give('minecraft:emerald')
            })
            """;

    static final String CHANCE_UNIT_LT = """
            ItemEvents.rightClicked('mod:unit_item', e => {
                if (Math.random() < 0.02) e.player.give('minecraft:gold_ingot')
            })
            """;

    static final String NO_CHANCE = """
            ItemEvents.rightClicked('mod:plain_item', e => {
                e.player.give('minecraft:dirt')
            })
            """;

    static final String BRACES = """
            ItemEvents.rightClicked('mod:brace_item', e => {
                let s = "not { closed }"
                // fake }
                /* also } here */
                let t = `template { still } ok`
                e.player.give('minecraft:pearl')
            })
            """;

    static final String ARRAY = """
            ItemEvents.rightClicked(['mod:arr_a', 'mod:arr_b'], e => {
                e.player.give('minecraft:stick')
            })
            """;

    static final String TAG = """
            ItemEvents.rightClicked('#forge:ingots', e => {
                e.player.drop('minecraft:iron_nugget')
            })
            """;

    static final String QUEST_SNBT = """
            {
            	title: "Dream charm"
            	description: [
            		"Wear momo_dlc:t-02-99 while killing skeletons."
            	]
            	tasks: [{
            		item: "momo_dlc:t-02-99"
            		type: "item"
            	}]
            }
            """;

    static long firstTouchMs;

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        KubeJsMechanicScan.warmup();
        firstTouchMs = (System.nanoTime() - t0) / 1_000_000L;
        assert firstTouchMs < 2000L
                : "first-touch class init；遊戲內由 AskService:2159 背景 warmup 承擔 ms="
                + firstTouchMs;
        KubeJsMechanicScan.resetUnknown();
        newStyle();
        oldStyle();
        curiosChance();
        chanceVariants();
        braces();
        arrayAndTag();
        negativeNone();
        questText();
        diskIndex();
        reloadInvalidate();
        System.out.println("AskMechanicFactsCheck OK");
    }

    private static void newStyle() {
        List<String> facts = KubeJsMechanicScan.factsForItem(NEW_STYLE, REL, "mod:demo_item");
        assert facts.stream().anyMatch(f ->
                f.contains("item:mod:demo_item")
                        && f.contains("-[use]->")
                        && f.contains("ItemEvents.rightClicked")
                        && f.contains("minecraft:diamond")
                        && f.contains("source:kubejs/" + REL + ":")
                        && f.contains("tier:A")) : facts;
        assert facts.stream().anyMatch(f ->
                f.contains("-[drops]->") && f.contains("item:minecraft:diamond")) : facts;
        assert facts.stream().anyMatch(f -> f.contains(REL + ":1") || f.matches(".*demo\\.js:\\d+.*"))
                : facts;
    }

    private static void oldStyle() {
        List<String> facts = KubeJsMechanicScan.factsForItem(OLD_STYLE, REL, "mod:old_item");
        assert facts.stream().anyMatch(f ->
                f.contains("item:mod:old_item")
                        && f.contains("ItemEvents.rightClicked")
                        && f.contains("tier:A")
                        && f.contains("source:kubejs/" + REL + ":")) : facts;
        assert facts.stream().anyMatch(f -> f.contains("clicked old relic") || f.contains("note:"))
                : facts;
    }

    private static void curiosChance() {
        String rel = "server_scripts/momo_dlc/entity/momo_dlc_entity_death.js";
        List<String> facts = KubeJsMechanicScan.factsForItem(CURIOS_DEATH, rel, "momo_dlc:t-02-99");
        assert facts.stream().anyMatch(f ->
                f.contains("item:momo_dlc:t-02-99")
                        && f.contains("-[drops]->")
                        && f.contains("item:momo_dlc:dream_rain")
                        && f.contains("EntityEvents.death")
                        && f.contains("minecraft:skeleton")
                        && f.contains("curios:momo_dlc:t-02-99")
                        && f.contains("機率:<=1")
                        && f.contains("<=1")
                        && f.contains("source:kubejs/" + rel + ":")
                        && f.contains("tier:A")) : facts;
        List<String> asLoot = KubeJsMechanicScan.factsForItem(CURIOS_DEATH, rel, "momo_dlc:dream_rain");
        assert asLoot.stream().anyMatch(f ->
                f.contains("-[drops]->") && f.contains("momo_dlc:dream_rain")) : asLoot;
    }

    private static void chanceVariants() {
        List<String> pct = KubeJsMechanicScan.factsForItem(CHANCE_VAR_LT, REL, "mod:pct100_item");
        assert pct.stream().anyMatch(f -> f.contains("機率:<5%")) : pct;
        List<String> unit = KubeJsMechanicScan.factsForItem(CHANCE_UNIT_LT, REL, "mod:unit_item");
        assert unit.stream().anyMatch(f -> f.contains("機率:<2%")) : unit;
        List<String> none = KubeJsMechanicScan.factsForItem(NO_CHANCE, REL, "mod:plain_item");
        assert !none.isEmpty() : none;
        assert none.stream().noneMatch(f -> f.contains("機率:")) : none;
    }

    private static void braces() {
        List<String> facts = KubeJsMechanicScan.factsForItem(BRACES, REL, "mod:brace_item");
        assert facts.stream().anyMatch(f ->
                f.contains("mod:brace_item")
                        && f.contains("minecraft:pearl")
                        && f.contains("ItemEvents.rightClicked")) : facts;
        int open = BRACES.indexOf('{', BRACES.indexOf("=>"));
        int close = KubeJsMechanicScan.matchingBrace(BRACES, open);
        String body = BRACES.substring(open, close + 1);
        assert body.contains("minecraft:pearl") : body;
        assert body.contains("not { closed }") : body;
    }

    private static void arrayAndTag() {
        List<String> a = KubeJsMechanicScan.factsForItem(ARRAY, REL, "mod:arr_a");
        List<String> b = KubeJsMechanicScan.factsForItem(ARRAY, REL, "mod:arr_b");
        assert a.stream().anyMatch(f -> f.contains("item:mod:arr_a") && f.contains("minecraft:stick"))
                : a;
        assert b.stream().anyMatch(f -> f.contains("item:mod:arr_b") && f.contains("minecraft:stick"))
                : b;
        List<String> tag = KubeJsMechanicScan.factsForItem(TAG, REL, "#forge:ingots");
        assert tag.stream().anyMatch(f ->
                f.contains("item:#forge:ingots")
                        && f.contains("minecraft:iron_nugget")) : tag;
    }

    private static void negativeNone() {
        List<String> kjs = KubeJsMechanicScan.factsForItem(NEW_STYLE, REL, "minecraft:barrier");
        assert kjs.isEmpty() : kjs;
        List<String> merged = KubeJsMechanicScan.honestMerge(kjs, List.of());
        assert merged.equals(List.of(KubeJsMechanicScan.NONE_MARK)) : merged;
        assert merged.contains("mechanic:none");
    }

    private static void questText() {
        List<String> facts = QuestMechanicFacts.factsForItem(
                QUEST_SNBT, "quests/chapters/demo.snbt", "momo_dlc:t-02-99");
        assert facts.stream().anyMatch(f ->
                f.contains("item:momo_dlc:t-02-99")
                        && f.contains("-[quest_text]->")
                        && f.contains(QuestMechanicFacts.DISCLAIMER)
                        && f.contains("tier:C")
                        && f.contains("source:ftbquests/quests/chapters/demo.snbt")) : facts;
        assert facts.stream().anyMatch(f -> f.contains("Wear momo_dlc:t-02-99")
                || f.contains("momo_dlc:t-02-99")) : facts;
        assert facts.size() <= QuestMechanicFacts.MAX_FACTS_PER_ITEM : facts;
    }

    private static void diskIndex() {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("packai-m1c-");
            Path server = dir.resolve("kubejs").resolve("server_scripts");
            Path client = dir.resolve("kubejs").resolve("client_scripts");
            Path assets = dir.resolve("kubejs").resolve("assets");
            Path data = dir.resolve("kubejs").resolve("data");
            Path chapters = dir.resolve("config").resolve("ftbquests").resolve("quests")
                    .resolve("chapters");
            Path rewards = dir.resolve("config").resolve("ftbquests").resolve("reward_tables");
            Files.createDirectories(server);
            Files.createDirectories(client);
            Files.createDirectories(assets);
            Files.createDirectories(data);
            Files.createDirectories(chapters);
            Files.createDirectories(rewards);
            Files.writeString(server.resolve("hit.js"), NEW_STYLE);
            Files.writeString(server.resolve("other.js"), OLD_STYLE);
            Files.writeString(client.resolve("third.js"), NO_CHANCE);
            Files.writeString(assets.resolve("foo.js"), NEW_STYLE);
            Files.writeString(data.resolve("foo.js"), NEW_STYLE);
            Files.writeString(chapters.resolve("demo.snbt"), QUEST_SNBT);
            Files.writeString(rewards.resolve("nope.snbt"), QUEST_SNBT);

            KubeJsMechanicScan.resetIndex();
            QuestMechanicFacts.resetIndex();
            // Absorb first ask-path (logPending / PackAiMod). Time the 2nd call.
            KubeJsMechanicScan.factsForItem(dir, "mod:demo_item");
            KubeJsMechanicScan.resetContentReads();
            long t0 = System.nanoTime();
            List<String> pending = KubeJsMechanicScan.factsForItem(dir, "mod:demo_item");
            long notReadyMs = (System.nanoTime() - t0) / 1_000_000L;
            int notReadyReads = KubeJsMechanicScan.contentReadCount();
            assert pending.isEmpty() : pending;
            assert notReadyReads == 0 : "not-ready content reads=" + notReadyReads;
            assert notReadyMs < 50 : "not-ready ms=" + notReadyMs;

            QuestMechanicFacts.factsForItem(dir, "momo_dlc:t-02-99");
            t0 = System.nanoTime();
            List<String> questPending = QuestMechanicFacts.factsForItem(dir, "momo_dlc:t-02-99");
            long questNotReadyMs = (System.nanoTime() - t0) / 1_000_000L;
            assert questPending.isEmpty() : questPending;
            assert questNotReadyMs < 50 : "quest not-ready ms=" + questNotReadyMs;

            KubeJsMechanicScan.resetContentReads();
            long c0 = System.nanoTime();
            KubeJsMechanicScan.buildIndex(
                    dir, 400, KubeJsMechanicScan.DEFAULT_SCAN_MAX_BYTES, 8000L);
            long coldMs = (System.nanoTime() - c0) / 1_000_000L;
            int coldReads = KubeJsMechanicScan.contentReadCount();
            assert KubeJsMechanicScan.isReady();
            for (String rel : KubeJsMechanicScan.indexedRels()) {
                String n = rel.replace('\\', '/').toLowerCase();
                assert !n.contains("/assets/") : rel;
                assert !n.contains("kubejs/assets") : rel;
                assert !n.contains("/data/") : rel;
            }
            assert KubeJsMechanicScan.indexedRels().size() == 3
                    : KubeJsMechanicScan.indexedRels();

            KubeJsMechanicScan.factsForItem(dir, "mod:demo_item");
            t0 = System.nanoTime();
            List<String> hit = KubeJsMechanicScan.factsForItem(dir, "mod:demo_item");
            long hitMs = (System.nanoTime() - t0) / 1_000_000L;
            assert hit.stream().anyMatch(f -> f.contains("mod:demo_item") && f.contains("minecraft:diamond"))
                    : hit;
            assert hitMs < 50 : "ask-hit ms=" + hitMs + " facts=" + hit;

            KubeJsMechanicScan.factsForItem(dir, "mod:absent_item");
            KubeJsMechanicScan.resetContentReads();
            t0 = System.nanoTime();
            List<String> miss = KubeJsMechanicScan.factsForItem(dir, "mod:absent_item");
            long missMs = (System.nanoTime() - t0) / 1_000_000L;
            int missReads = KubeJsMechanicScan.contentReadCount();
            assert miss.isEmpty() : miss;
            assert missReads == 0 : "ready-miss content reads=" + missReads;
            assert missMs < 50 : "ask-miss ms=" + missMs;

            KubeJsMechanicScan.resetContentReads();
            long w0 = System.nanoTime();
            KubeJsMechanicScan.buildIndex(
                    dir, 400, KubeJsMechanicScan.DEFAULT_SCAN_MAX_BYTES, 8000L);
            long warmMs = (System.nanoTime() - w0) / 1_000_000L;
            int warmReads = KubeJsMechanicScan.contentReadCount();
            assert warmReads == 0 : "warm reads=" + warmReads;
            assert warmMs < 50 : "warm-index ms=" + warmMs;

            KubeJsMechanicScan.resetIndex();
            KubeJsMechanicScan.buildIndex(
                    dir, 2, KubeJsMechanicScan.DEFAULT_SCAN_MAX_BYTES, 8000L);
            assert KubeJsMechanicScan.indexedRels().size() == 2
                    : KubeJsMechanicScan.indexedRels();
            assert KubeJsMechanicScan.isPartial();

            KubeJsMechanicScan.resetIndex();
            KubeJsMechanicScan.buildIndex(
                    dir, 400, KubeJsMechanicScan.DEFAULT_SCAN_MAX_BYTES, 0L);
            assert KubeJsMechanicScan.isPartial();
            assert KubeJsMechanicScan.indexedRels().isEmpty()
                    : KubeJsMechanicScan.indexedRels();

            QuestMechanicFacts.resetContentReads();
            QuestMechanicFacts.buildIndex(dir, 200, 8_388_608L, 8000L);
            assert QuestMechanicFacts.isReady();
            for (String rel : QuestMechanicFacts.indexedRels()) {
                assert !rel.contains("reward_tables") : rel;
            }
            QuestMechanicFacts.factsForItem(dir, "momo_dlc:t-02-99");
            t0 = System.nanoTime();
            List<String> qHit = QuestMechanicFacts.factsForItem(dir, "momo_dlc:t-02-99");
            long qHitMs = (System.nanoTime() - t0) / 1_000_000L;
            assert qHit.stream().anyMatch(f -> f.contains("-[quest_text]->")) : qHit;
            assert qHitMs < 50 : "quest-hit ms=" + qHitMs;

            System.out.println("M1c first-touch class init；遊戲內由 AskService:2159 背景 warmup 承擔 ms="
                    + firstTouchMs);
            System.out.println("M1c metrics firstTouchMs=" + firstTouchMs
                    + " notReadyMs=" + notReadyMs
                    + " askHitMs=" + hitMs
                    + " askMissMs=" + missMs
                    + " notReadyReads=" + notReadyReads
                    + " missReads=" + missReads
                    + " coldIndexMs=" + coldMs
                    + " coldReads=" + coldReads
                    + " warmIndexMs=" + warmMs
                    + " warmReads=" + warmReads
                    + " questNotReadyMs=" + questNotReadyMs
                    + " questHitMs=" + qHitMs
                    + " indexed=" + 3
                    + " excluded=kubejs/assets,kubejs/data,reward_tables");
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            rm(dir);
            KubeJsMechanicScan.resetIndex();
            QuestMechanicFacts.resetIndex();
        }
    }

    /** M1d: /reload invalidate → rebuild; negative = no reload keeps warm cache. */
    private static void reloadInvalidate() {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("packai-m1d-");
            Path server = dir.resolve("kubejs").resolve("server_scripts");
            Files.createDirectories(server);
            Files.writeString(server.resolve("hit.js"), NEW_STYLE);

            KubeJsMechanicScan.resetIndex();
            KubeJsMechanicScan.resetContentReads();
            final int[] fakeReads = {0};
            KubeJsMechanicScan.setContentReader(p -> {
                fakeReads[0]++;
                return Files.readAllBytes(p);
            });

            KubeJsMechanicScan.buildIndex(
                    dir, 400, KubeJsMechanicScan.DEFAULT_SCAN_MAX_BYTES, 8000L);
            assert KubeJsMechanicScan.isReady() : "index should be ready after cold build";
            assert fakeReads[0] > 0 : "cold build must read content";
            List<String> hit = KubeJsMechanicScan.factsForItem(dir, "mod:demo_item");
            assert hit.stream().anyMatch(f ->
                    f.contains("mod:demo_item") && f.contains("minecraft:diamond")) : hit;

            // Negative control: no reload → warm rebuild does not re-read / does not drop READY.
            int genBefore = KubeJsMechanicScan.buildGen();
            int readsBeforeWarm = fakeReads[0];
            KubeJsMechanicScan.buildIndex(
                    dir, 400, KubeJsMechanicScan.DEFAULT_SCAN_MAX_BYTES, 8000L);
            assert KubeJsMechanicScan.isReady() : "warm rebuild must keep READY";
            assert KubeJsMechanicScan.buildGen() == genBefore : "no reload → gen unchanged";
            assert fakeReads[0] == readsBeforeWarm : "warm rebuild must skip via mtime+size";
            List<String> still = KubeJsMechanicScan.factsForItem(dir, "mod:demo_item");
            assert still.stream().anyMatch(f -> f.contains("mod:demo_item")) : still;

            // Reload invalidate → stale; next build must re-read (index.json discarded).
            Path indexFile = dir.resolve("config").resolve("packai").resolve("mechanic-cache")
                    .resolve(KubeJsMechanicScan.INDEX_JSON);
            assert Files.isRegularFile(indexFile) : "index.json should exist before invalidate";
            KubeJsMechanicScan.invalidateOnReload(dir);
            assert !KubeJsMechanicScan.isReady() : "invalidate must mark index stale";
            assert KubeJsMechanicScan.buildGen() == genBefore + 1 : "invalidate bumps BUILD_GEN";
            assert !Files.isRegularFile(indexFile) : "index.json must be discarded";
            List<String> pending = KubeJsMechanicScan.factsForItem(dir, "mod:demo_item");
            assert pending.isEmpty() : pending;

            int readsBeforeRebuild = fakeReads[0];
            KubeJsMechanicScan.buildIndex(
                    dir, 400, KubeJsMechanicScan.DEFAULT_SCAN_MAX_BYTES, 8000L);
            assert KubeJsMechanicScan.isReady() : "rebuild after invalidate";
            assert fakeReads[0] > readsBeforeRebuild
                    : "rebuild must call content reader again reads="
                    + fakeReads[0] + " before=" + readsBeforeRebuild;
            List<String> fresh = KubeJsMechanicScan.factsForItem(dir, "mod:demo_item");
            assert fresh.stream().anyMatch(f ->
                    f.contains("mod:demo_item") && f.contains("minecraft:diamond")) : fresh;
            System.out.println("M1d reloadInvalidate OK gen=" + KubeJsMechanicScan.buildGen()
                    + " fakeReads=" + fakeReads[0]);
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            rm(dir);
            KubeJsMechanicScan.resetIndex();
        }
    }

    private static void rm(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best-effort temp cleanup
                }
            });
        } catch (Exception ignored) {
            // best-effort temp cleanup
        }
    }
}

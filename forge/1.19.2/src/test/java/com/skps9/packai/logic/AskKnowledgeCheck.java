package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * KB-1 parse / lookup / unknown jsonl / cache evict. Fixture strings + temp dirs. Run with -ea.
 */
public final class AskKnowledgeCheck {
    private AskKnowledgeCheck() {}

    static final String GOGGLES = """
            {
              "item": "create:goggles",
              "display": {"en_us": "Engineer's Goggles"},
              "mod": "create",
              "applies_to": {"mc": "1.19.2", "loader": "forge", "mod_versions": ["0.5.x"]},
              "obtain": [{"type": "craft", "chance": "100", "requires": ["create:andesite_alloy"],
                          "source": "mod:recipe_json", "tier": "A"}],
              "use": [{"trigger": "wear_in_head_slot", "effect": "overlay",
                       "source": "mod:tooltip", "tier": "B"}],
              "worn": [{"slot": "head", "effects": ["overlay"], "source": "mod:tooltip", "tier": "B"}],
              "notes": "seed", "contributors": ["SK"], "updated": "2026-09-14",
              "unknown_future": {"ok": true}
            }
            """;

    static final String ENTRIES = """
            {"entries":[
              {"item":"mod:alpha","obtain":[{"type":"loot","mob":"minecraft:zombie","source":"mod:loot","tier":"A"}]},
              {"item":"mod:beta","use":[{"trigger":"right_click","effect":"heal","source":"mod:tooltip","tier":"B"}]}
            ]}
            """;

    static final String MIXED = """
            {
              "item": "mod:mixed",
              "obtain": [
                {"type": "quest", "source": "ftb:quest", "tier": "C"},
                {"type": "craft", "source": "mod:recipe_json", "tier": "A"},
                {"type": "drop", "source": "mod:tooltip", "tier": "B", "conflict": true}
              ]
            }
            """;

    static final String STALE = """
            {
              "item": "mod:stale",
              "applies_to": {"mc": "1.18.2", "loader": "forge", "mod_versions": ["0.5.x"]},
              "use": [{"trigger": "x", "effect": "y", "source": "mod:tooltip", "tier": "B"}]
            }
            """;

    static final String MANY = """
            {
              "item": "mod:many",
              "obtain": [
                {"type": "a1", "source": "s", "tier": "A"},
                {"type": "a2", "source": "s", "tier": "A"},
                {"type": "a3", "source": "s", "tier": "A"},
                {"type": "a4", "source": "s", "tier": "A"},
                {"type": "a5", "source": "s", "tier": "A"},
                {"type": "a6", "source": "s", "tier": "A"},
                {"type": "a7", "source": "s", "tier": "A"},
                {"type": "a8", "source": "s", "tier": "A"},
                {"type": "a9", "source": "s", "tier": "A"},
                {"type": "a10", "source": "s", "tier": "A"}
              ]
            }
            """;

    public static void main(String[] args) throws Exception {
        parseShapes();
        lookupHit();
        tierOrderAndConflict();
        capEight();
        versionGate();
        enabledOff();
        unknownDedup();
        unknownCap();
        cacheEvict();
        authorOverridesCache();
        System.out.println("AskKnowledgeCheck OK");
    }

    private static void parseShapes() {
        List<KnowledgeEntry> one = KnowledgeEntry.parseAll(GOGGLES);
        assert one.size() == 1 : one.size();
        assert "create:goggles".equals(one.get(0).item) : one.get(0).item;
        assert !one.get(0).obtain.isEmpty();
        assert "100".equals(one.get(0).obtain.get(0).chance) : one.get(0).obtain.get(0).chance;
        assert one.get(0).obtain.get(0).requires.contains("create:andesite_alloy");

        List<KnowledgeEntry> wrap = KnowledgeEntry.parseAll(ENTRIES);
        assert wrap.size() == 2 : wrap.size();
        assert "mod:alpha".equals(wrap.get(0).item);
        assert "mod:beta".equals(wrap.get(1).item);

        assert KnowledgeEntry.parseAll("{").isEmpty();
        assert KnowledgeEntry.parseAll("").isEmpty();
        assert KnowledgeEntry.parseAll("not-json").isEmpty();
        assert KnowledgeEntry.parseAll("null").isEmpty();
        List<KnowledgeEntry> extra = KnowledgeEntry.parseAll(
                "{\"item\":\"mod:x\",\"totally_unknown\":[1,2],\"obtain\":[]}");
        assert extra.size() == 1 && "mod:x".equals(extra.get(0).item) : extra;
    }

    private static void lookupHit() throws Exception {
        KnowledgeStore.resetForTest();
        Path game = Files.createTempDirectory("packai-kb-hit");
        Path dir = KnowledgeStore.knowledgeDir(game);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("goggles.json"), GOGGLES, StandardCharsets.UTF_8);
        List<String> facts = KnowledgeLookup.factsForItem(
                game, "create:goggles", 8, true, "1.19.2", "forge", "0.5.1");
        assert facts.stream().anyMatch(f ->
                f.contains("item:create:goggles")
                        && f.contains("-[obtain]->")
                        && f.contains("type:craft")
                        && f.contains("chance:100")
                        && f.contains("requires:create:andesite_alloy")
                        && f.contains("source:mod:recipe_json")
                        && f.contains("tier:A")) : facts;
        assert facts.stream().anyMatch(f ->
                f.contains("-[use]->")
                        && f.contains("trigger:wear_in_head_slot")
                        && f.contains("effect:overlay")
                        && f.contains("tier:B")) : facts;
        assert facts.stream().anyMatch(f ->
                f.contains("-[worn]->")
                        && f.contains("slot:head")
                        && f.contains("effects:overlay")
                        && f.contains("tier:B")) : facts;
        assert facts.stream().noneMatch(f -> f.contains("注意")) : facts;
    }

    private static void tierOrderAndConflict() throws Exception {
        KnowledgeStore.resetForTest();
        Path game = Files.createTempDirectory("packai-kb-tier");
        Path dir = KnowledgeStore.knowledgeDir(game);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("mixed.json"), MIXED, StandardCharsets.UTF_8);
        List<String> facts = KnowledgeLookup.factsForItem(
                game, "mod:mixed", 8, true, "1.19.2", "forge", "");
        assert facts.size() == 3 : facts;
        assert facts.get(0).contains("tier:A") : facts;
        assert facts.get(1).contains("tier:B") : facts;
        assert facts.get(2).contains("tier:C") : facts;
        assert facts.get(2).contains(KnowledgeLookup.C_DISCLAIMER) : facts;
        assert facts.get(1).contains("conflict") : facts;
    }

    private static void capEight() throws Exception {
        KnowledgeStore.resetForTest();
        Path game = Files.createTempDirectory("packai-kb-cap");
        Path dir = KnowledgeStore.knowledgeDir(game);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("many.json"), MANY, StandardCharsets.UTF_8);
        List<String> facts = KnowledgeLookup.factsForItem(
                game, "mod:many", 8, true, "1.19.2", "forge", "");
        assert facts.size() == 8 : facts.size();
        assert facts.stream().noneMatch(f -> f.contains("type:a9") || f.contains("type:a10")) : facts;
    }

    private static void versionGate() throws Exception {
        KnowledgeStore.resetForTest();
        Path game = Files.createTempDirectory("packai-kb-ver");
        Path dir = KnowledgeStore.knowledgeDir(game);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("stale.json"), STALE, StandardCharsets.UTF_8);
        List<String> facts = KnowledgeLookup.factsForItem(
                game, "mod:stale", 8, true, "1.19.2", "forge", "0.6.0");
        assert !facts.isEmpty() : facts;
        assert facts.stream().anyMatch(f -> f.contains("注意") && f.contains("1.18.2")) : facts;
    }

    private static void enabledOff() throws Exception {
        KnowledgeStore.resetForTest();
        Path game = Files.createTempDirectory("packai-kb-off");
        Path dir = KnowledgeStore.knowledgeDir(game);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("goggles.json"), GOGGLES, StandardCharsets.UTF_8);
        List<String> facts = KnowledgeLookup.factsForItem(
                game, "create:goggles", 8, false, "1.19.2", "forge", "0.5.1");
        assert facts.isEmpty() : facts;
    }

    private static void unknownDedup() throws Exception {
        UnknownItemLog.clock = ticking();
        Path game = Files.createTempDirectory("packai-kb-unk");
        UnknownItemLog.record(game, "mod:dup", "miss", 2000, 1_000_000);
        UnknownItemLog.record(game, "mod:dup", "miss", 2000, 1_000_000);
        UnknownItemLog.record(game, "mod:dup", "miss", 2000, 1_000_000);
        List<String> lines = Files.readAllLines(UnknownItemLog.logFile(game), StandardCharsets.UTF_8)
                .stream().filter(s -> !s.isBlank()).toList();
        assert lines.size() == 1 : lines;
        UnknownItemLog.Row row = UnknownItemLog.decode(lines.get(0));
        assert row != null && row.seen == 3 : lines;
        assert "mod:dup".equals(row.item);
        assert "miss".equals(row.reason);
        assert row.ts != null && !row.ts.isBlank();
        UnknownItemLog.clock = Instant::now;
    }

    private static void unknownCap() throws Exception {
        UnknownItemLog.clock = ticking();
        Path game = Files.createTempDirectory("packai-kb-cap2");
        for (int i = 1; i <= 5; i++) {
            UnknownItemLog.record(game, "mod:i" + i, "miss", 3, 1_000_000);
        }
        List<UnknownItemLog.Row> rows = UnknownItemLog.readRows(UnknownItemLog.logFile(game));
        assert rows.size() == 3 : rows.size();
        List<String> ids = rows.stream().map(r -> r.item).toList();
        assert ids.contains("mod:i3") && ids.contains("mod:i4") && ids.contains("mod:i5") : ids;
        assert !ids.contains("mod:i1") && !ids.contains("mod:i2") : ids;
        UnknownItemLog.clock = Instant::now;
    }

    private static void cacheEvict() throws Exception {
        Path game = Files.createTempDirectory("packai-kb-evict");
        Path cache = KnowledgeStore.cacheDir(game);
        Files.createDirectories(cache);
        Path old = cache.resolve("old.json");
        Path mid = cache.resolve("mid.json");
        Path neu = cache.resolve("new.json");
        byte[] blob = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx".getBytes(StandardCharsets.UTF_8);
        Files.write(old, blob);
        Files.write(mid, blob);
        Files.write(neu, blob);
        Files.setLastModifiedTime(old, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(mid, FileTime.fromMillis(2_000));
        Files.setLastModifiedTime(neu, FileTime.fromMillis(3_000));
        long one = Files.size(neu);
        KnowledgeStore.evictCacheBytes(cache, one + 5);
        assert !Files.exists(old) : "old should evict";
        assert Files.exists(neu) : "newest kept";
    }

    private static void authorOverridesCache() throws Exception {
        KnowledgeStore.resetForTest();
        Path game = Files.createTempDirectory("packai-kb-prec");
        Path cache = KnowledgeStore.cacheDir(game);
        Path auth = KnowledgeStore.knowledgeDir(game);
        Files.createDirectories(cache);
        Files.createDirectories(auth);
        Files.writeString(cache.resolve("x.json"),
                "{\"item\":\"mod:prec\",\"use\":[{\"trigger\":\"cache\",\"effect\":\"c\",\"source\":\"cache\",\"tier\":\"B\"}]}",
                StandardCharsets.UTF_8);
        Files.writeString(auth.resolve("x.json"),
                "{\"item\":\"mod:prec\",\"use\":[{\"trigger\":\"author\",\"effect\":\"a\",\"source\":\"author\",\"tier\":\"A\"}]}",
                StandardCharsets.UTF_8);
        List<String> facts = KnowledgeLookup.factsForItem(
                game, "mod:prec", 8, true, "1.19.2", "forge", "");
        assert facts.stream().anyMatch(f -> f.contains("trigger:author") && f.contains("source:author")) : facts;
        assert facts.stream().noneMatch(f -> f.contains("trigger:cache")) : facts;
    }

    private static java.util.function.Supplier<Instant> ticking() {
        AtomicLong t = new AtomicLong(1_700_000_000_000L);
        return () -> Instant.ofEpochMilli(t.getAndAdd(1_000));
    }
}

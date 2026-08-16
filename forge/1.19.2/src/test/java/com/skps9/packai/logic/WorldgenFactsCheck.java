package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Headless worldgen parser + index (no Minecraft). Run with -ea. */
public final class WorldgenFactsCheck {
    private WorldgenFactsCheck() {}

    static final String BIOME_JSON =
            "{\"features\":[[],[\"minecraft:ore_iron\"],[\"minecraft:trees_plains\"]]}";
    static final String STRUCTURE_JSON =
            "{\"type\":\"minecraft:jigsaw\",\"biomes\":\"#minecraft:has_structure/village_plains\"}";
    static final String STRUCTURE_NO_BIOMES = "{\"type\":\"minecraft:jigsaw\"}";
    static final String STRUCTURE_SET_JSON =
            "{\"structures\":[{\"structure\":\"minecraft:village_plains\",\"weight\":1}],"
                    + "\"placement\":{\"type\":\"minecraft:random_spread\",\"spacing\":34,\"separation\":8}}";
    static final String ORE_CONFIGURED = "{\"type\":\"minecraft:ore\",\"config\":{\"size\":9}}";
    static final String GEODE_NO_SIZE = "{\"type\":\"minecraft:geode\",\"config\":{}}";
    static final String PLACED_HEIGHT =
            "{\"feature\":\"minecraft:ore_iron\",\"placement\":["
                    + "{\"type\":\"minecraft:count\",\"count\":10},"
                    + "{\"type\":\"minecraft:height_range\",\"height\":{"
                    + "\"type\":\"minecraft:trapezoid\","
                    + "\"min_inclusive\":{\"absolute\":-24},"
                    + "\"max_inclusive\":{\"absolute\":56}}}]}";
    static final String PLACED_NO_HEIGHT =
            "{\"feature\":\"minecraft:ore_iron\",\"placement\":[{\"type\":\"minecraft:count\",\"count\":4}]}";
    static final String MODIFIER_JSON =
            "{\"type\":\"forge:add_features\",\"biomes\":\"minecraft:plains\","
                    + "\"features\":\"mod:extra_ore\",\"step\":\"underground_ores\"}";
    static final String TAG_JSON = "{\"values\":[\"minecraft:plains\",\"minecraft:meadow\"]}";

    public static void main(String[] args) throws Exception {
        ids();
        parseEdges();
        noInvent();
        lookupLoose();
        overrideWinsJar();
        missHonest();
        ensureIdempotent();
        System.out.println("WorldgenFactsCheck OK");
    }

    private static void ids() {
        assert "minecraft:plains".equals(
                WorldgenFacts.idFromPath("data/minecraft/worldgen/biome/plains.json"));
        assert "minecraft:plains".equals(
                WorldgenFacts.idFromPath("datapacks/x/data/minecraft/worldgen/biome/plains.json"));
        assert "minecraft:plains".equals(
                WorldgenFacts.idFromPath("kubejs/data/minecraft/worldgen/biome/plains.json"));
        assert "minecraft:ore_iron".equals(
                WorldgenFacts.idFromPath("data/minecraft/worldgen/placed_feature/ore_iron.json"));
        assert "#minecraft:has_structure/village_plains".equals(
                WorldgenFacts.idFromPath(
                        "data/minecraft/tags/worldgen/biome/has_structure/village_plains.json"));
        assert WorldgenFacts.Kind.MODIFIER
                == WorldgenFacts.kindFromPath("data/mod/forge/biome_modifier/add_ore.json");
        assert !WorldgenFacts.isWorldgenPath("data/minecraft/recipes/stick.json");
    }

    private static void parseEdges() {
        WorldgenFacts.Store s = new WorldgenFacts.Store();
        WorldgenFacts.ingest(s, "data/minecraft/worldgen/biome/plains.json", BIOME_JSON, true);
        WorldgenFacts.ingest(
                s, "data/minecraft/worldgen/structure/village_plains.json", STRUCTURE_JSON, true);
        WorldgenFacts.ingest(
                s, "data/minecraft/worldgen/structure_set/villages.json", STRUCTURE_SET_JSON, true);
        WorldgenFacts.ingest(
                s, "data/minecraft/worldgen/configured_feature/ore_iron.json", ORE_CONFIGURED, true);
        WorldgenFacts.ingest(
                s, "data/minecraft/worldgen/placed_feature/ore_iron.json", PLACED_HEIGHT, true);
        WorldgenFacts.ingest(s, "data/mod/forge/biome_modifier/add_ore.json", MODIFIER_JSON, true);
        WorldgenFacts.ingest(
                s,
                "data/minecraft/tags/worldgen/biome/has_structure/village_plains.json",
                TAG_JSON,
                true);

        List<String> plains = s.formatMatches("plains", 20);
        String pjoin = String.join("\n", plains);
        assert pjoin.contains("[WORLDGEN] biome minecraft:plains") : pjoin;
        assert pjoin.contains("placed_feature=minecraft:ore_iron") : pjoin;
        assert pjoin.contains("structure minecraft:village_plains") : pjoin;
        assert pjoin.contains("biomes=#minecraft:has_structure/village_plains") : pjoin;

        List<String> village = s.formatMatches("village", 20);
        String vjoin = String.join("\n", village);
        assert vjoin.contains("structure minecraft:village_plains") : vjoin;
        assert vjoin.contains("structure_set minecraft:villages") : vjoin;
        assert vjoin.contains("spacing=34") : vjoin;
        assert vjoin.contains("separation=8") : vjoin;
        assert vjoin.contains("tag #minecraft:has_structure/village_plains") : vjoin;
        assert !vjoin.contains("Y=") && !vjoin.contains("Y =") : vjoin;

        List<String> iron = s.formatMatches("ore_iron", 20);
        String ijoin = String.join("\n", iron);
        assert ijoin.contains("configured_feature minecraft:ore_iron type=minecraft:ore size=9")
                : ijoin;
        assert ijoin.contains("count=10") : ijoin;
        assert ijoin.contains("height_range=absolute -24..absolute 56") : ijoin;
        assert ijoin.contains("in biome minecraft:plains") : ijoin;
        assert !ijoin.contains("Y=") && !ijoin.contains("Y =") : ijoin;

        List<String> extra = s.formatMatches("extra_ore", 20);
        String ejoin = String.join("\n", extra);
        assert ejoin.contains("biome_modifier biomes=minecraft:plains features=mod:extra_ore")
                : ejoin;
    }

    private static void noInvent() {
        WorldgenFacts.Store s = new WorldgenFacts.Store();
        WorldgenFacts.ingest(
                s, "data/minecraft/worldgen/structure/foo.json", STRUCTURE_NO_BIOMES, true);
        WorldgenFacts.ingest(
                s, "data/minecraft/worldgen/placed_feature/ore_iron.json", PLACED_NO_HEIGHT, true);
        WorldgenFacts.ingest(
                s, "data/minecraft/worldgen/configured_feature/amethyst_geode.json", GEODE_NO_SIZE, true);

        String struct = String.join("\n", s.formatMatches("foo", 8));
        assert struct.contains("[WORLDGEN] structure minecraft:foo") : struct;
        assert !struct.contains("biomes=") : struct;
        assert !struct.contains("Y=") && !struct.contains("Y =") : struct;

        String placed = String.join("\n", s.formatMatches("ore_iron", 8));
        assert placed.contains("count=4") : placed;
        assert !placed.contains("height_range") : placed;
        assert !placed.contains("Y=") && !placed.contains("Y =") : placed;
        assert !placed.matches("(?s).*\\bY\\s*=.*") : placed;

        String geode = String.join("\n", s.formatMatches("amethyst_geode", 8));
        assert geode.contains("type=minecraft:geode") : geode;
        assert !geode.contains("size=") : geode;
    }

    private static void lookupLoose() throws Exception {
        WorldgenIndex.reset();
        Path game = Files.createTempDirectory("packai-wg-loose");
        Path biome = game.resolve("datapacks/p/data/minecraft/worldgen/biome/plains.json");
        Files.createDirectories(biome.getParent());
        Files.writeString(biome, BIOME_JSON, StandardCharsets.UTF_8);
        Path st = game.resolve("datapacks/p/data/minecraft/worldgen/structure/village_plains.json");
        Files.createDirectories(st.getParent());
        Files.writeString(st, STRUCTURE_JSON, StandardCharsets.UTF_8);

        List<String> lines = WorldgenIndex.lookup("village_plains", game, "en_us");
        String join = String.join("\n", lines);
        assert join.startsWith("[WORLDGEN]") : join;
        assert join.contains("structure minecraft:village_plains") : join;
        assert join.contains("biomes=#minecraft:has_structure/village_plains") : join;
        assert !join.contains("Y=") : join;
        WorldgenIndex.reset();
    }

    private static void overrideWinsJar() throws Exception {
        WorldgenIndex.reset();
        Path game = Files.createTempDirectory("packai-wg-ov");
        Path mods = game.resolve("mods");
        Files.createDirectories(mods);
        Path jar = mods.resolve("base.jar");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(jar))) {
            z.putNextEntry(new ZipEntry("data/minecraft/worldgen/placed_feature/ore_iron.json"));
            z.write(PLACED_HEIGHT.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            z.putNextEntry(new ZipEntry("data/minecraft/worldgen/configured_feature/ore_iron.json"));
            z.write(ORE_CONFIGURED.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        Path loose = game.resolve("kubejs/data/minecraft/worldgen/placed_feature/ore_iron.json");
        Files.createDirectories(loose.getParent());
        Files.writeString(loose, PLACED_NO_HEIGHT, StandardCharsets.UTF_8);

        List<String> lines = WorldgenIndex.lookup("ore_iron", game, "en_us");
        String join = String.join("\n", lines);
        assert join.contains("count=4") : join;
        assert !join.contains("count=10") : join;
        assert !join.contains("height_range") : join;
        assert !join.contains("Y=") && !join.contains("Y =") : join;
        WorldgenIndex.reset();
    }

    private static void missHonest() throws Exception {
        WorldgenIndex.reset();
        Path empty = Files.createTempDirectory("packai-wg-miss");
        List<String> en = WorldgenIndex.lookup("no_such_structure", empty, "en_us");
        assert en.size() == 1 : en;
        assert en.get(0).startsWith("[WORLDGEN]") : en;
        assert en.get(0).contains("no indexed worldgen") : en;
        assert !en.get(0).contains("Y=") && !en.get(0).contains("Y =") : en;
        List<String> zh = WorldgenIndex.lookup("no_such_structure", empty, "zh_tw");
        assert zh.get(0).contains("此包未索引到") : zh;
        WorldgenIndex.reset();
    }

    private static void ensureIdempotent() throws Exception {
        WorldgenIndex.reset();
        Path game = Files.createTempDirectory("packai-wg-id");
        Path biome = game.resolve("datapacks/p/data/minecraft/worldgen/biome/plains.json");
        Files.createDirectories(biome.getParent());
        Files.writeString(biome, BIOME_JSON, StandardCharsets.UTF_8);
        WorldgenIndex.ensure(game);
        WorldgenIndex.ensure(game);
        List<String> a = WorldgenIndex.lookup("plains", game, "en_us");
        List<String> b = WorldgenIndex.lookup("plains", game, "en_us");
        assert a.equals(b) : a + " vs " + b;
        assert a.get(0).contains("biome minecraft:plains") : a;
        WorldgenIndex.reset();
    }
}

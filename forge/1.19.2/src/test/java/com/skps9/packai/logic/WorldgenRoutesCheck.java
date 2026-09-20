package com.skps9.packai.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Headless worldgen routes + dimension map + acquire merge. No Minecraft bootstrap. */
public final class WorldgenRoutesCheck {
    private static final String PLACED =
            "[WORLDGEN] placed_feature ad_astra:moon_desh_ore configured=ad_astra:moon_desh_ore "
                    + "count=9 height_range=absolute -80..absolute 80";
    private static final String CONFIGURED =
            "[WORLDGEN] configured_feature ad_astra:moon_desh_ore type=minecraft:ore size=9";
    private static final String IN_BIOME =
            "[WORLDGEN] placed_feature ad_astra:moon_desh_ore in biome ad_astra:lunar_wastelands";
    private static final String ZH_TW =
            "\u7926\u7269\u5206\u4f48\uff1aad_astra:moon_desh_ore\uff5c\u751f\u614b\u57df\uff1a"
                    + "ad_astra:lunar_wastelands\uff5c\u9ad8\u5ea6\uff1a-80..80\uff5c\u7926\u8108\u5927\u5c0f\uff1a9"
                    + "\uff5c\u6bcf\u5340\u6578\u91cf\uff1a9\uff5c\u7dad\u5ea6\uff1aad_astra:moon";

    private WorldgenRoutesCheck() {}

    public static void main(String[] args) throws Exception {
        ids();
        dimensionShapes();
        overCap();
        routesAndHuman();
        mergeOrderAndBudget();
        sandboxOrSkip();
        System.out.println("WorldgenRoutesCheck OK");
    }

    private static void ids() {
        assert WorldgenFacts.Kind.DIMENSION
                == WorldgenFacts.kindFromPath("data/ad_astra/dimension/moon.json");
        assert "ad_astra:moon".equals(WorldgenFacts.idFromPath("data/ad_astra/dimension/moon.json"));
        assert WorldgenFacts.kindFromPath("data/minecraft/recipes/stick.json") == null;
        assert "-80..80".equals(AcquireAskTool.displayHeight("absolute -80..absolute 80"));
        assert "above_bottom 1..below_top 2".equals(
                AcquireAskTool.displayHeight("above_bottom 1..below_top 2"));
    }

    private static void dimensionShapes() {
        WorldgenFacts.Store store = new WorldgenFacts.Store();
        WorldgenFacts.ingest(store, "data/ad_astra/dimension/moon.json", fixed("ad_astra:lunar_wastelands"), true);
        WorldgenFacts.ingest(store, "data/createteleporters/dimension/void.json",
                biomes("createteleporters:void_biome"), true);
        assert "ad_astra:moon".equals(store.dimensionOf("ad_astra:lunar_wastelands"));
        assert "createteleporters:void".equals(store.dimensionOf("createteleporters:void_biome"));
        assert store.biomeDimensionCount() == 2 : store.biomeDimensionCount();

        WorldgenFacts.Store skipped = new WorldgenFacts.Store();
        WorldgenFacts.ingest(skipped, "data/mod/dimension/odd.json",
                "{\"generator\":{\"biome_source\":{\"type\":\"minecraft:checkerboard\",\"biome\":\"mod:should_skip\"}}}",
                true);
        assert skipped.dimensionOf("mod:should_skip") == null;
        assert skipped.biomeDimensionCount() == 0;

        WorldgenFacts.Store ambiguous = new WorldgenFacts.Store();
        WorldgenFacts.ingest(ambiguous, "data/ad_astra/dimension/earth_orbit.json", biomes("ad_astra:orbit"), true);
        WorldgenFacts.ingest(ambiguous, "data/ad_astra/dimension/moon_orbit.json", biomes("ad_astra:orbit"), true);
        assert ambiguous.dimensionOf("ad_astra:orbit") == null : "ambiguous biome must be unused";

        WorldgenFacts.Store broken = new WorldgenFacts.Store();
        WorldgenFacts.ingest(broken, "data/ad_astra/dimension/moon.json", fixed("ad_astra:lunar_wastelands"), true);
        assert broken.dimensionOf("ad_astra:lunar_wastelands") != null;
        broken.biomeToDim.clear();
        assert broken.dimensionOf("ad_astra:lunar_wastelands") == null : "cleared map";
    }

    private static void overCap() {
        WorldgenFacts.Store store = new WorldgenFacts.Store();
        for (int i = 0; i < WorldgenFacts.MAX_DIM_FILES + 1; i++) {
            WorldgenFacts.ingest(store, "data/mod/dimension/d" + i + ".json", fixed("mod:b" + i), true);
        }
        assert "mod:d0".equals(store.dimensionOf("mod:b0"));
        assert store.dimensionOf("mod:b" + WorldgenFacts.MAX_DIM_FILES) == null;
        assert store.dimensionFilesOverCap() == 1 : store.dimensionFilesOverCap();
    }

    private static void routesAndHuman() throws Exception {
        Path dir = Files.createTempDirectory("packai-wg-routes");
        try {
            writeFixture(dir);
            WorldgenIndex.reset();
            List<String> lines = WorldgenIndex.routesForItem("ad_astra:moon_desh_ore", dir);
            assert lines.stream().anyMatch(l -> l.contains(PLACED)) : lines;
            assert lines.stream().anyMatch(l -> l.contains(CONFIGURED)) : lines;
            assert lines.stream().anyMatch(l -> l.contains(IN_BIOME)) : lines;
            List<String> empty = WorldgenIndex.routesForItem("minecraft:bedrock", dir);
            assert empty.isEmpty() : empty;
            for (String line : empty) {
                assert !line.contains("miss") && !WorldgenFacts.missLine("minecraft:bedrock", "en_us").equals(line);
            }
            String human = AcquireAskTool.humanWorldgenRoute("zh_tw", PLACED + "\n" + CONFIGURED + "\n" + IN_BIOME);
            assert ZH_TW.equals(human) : human;
            assert !human.contains("absolute");
            assert !human.contains("configured=");
            assert !human.contains("count=");
            assert !human.contains("[WORLDGEN]");
            String en = AcquireAskTool.humanWorldgenRoute("en_us", PLACED + "\n" + CONFIGURED + "\n" + IN_BIOME);
            assert en.contains("Ore spread: ad_astra:moon_desh_ore") : en;
            assert en.contains("Dimension: ad_astra:moon") : en;
            assert !ReplyLang.tr("en_us", "packai.reply.worldgen_ore").isBlank();
            assert !ReplyLang.tr("zh_cn", "packai.reply.worldgen_ore").isBlank();
            assert !ReplyLang.tr("zh_cn", "packai.reply.worldgen_ore").equals("packai.reply.worldgen_ore");
            String orbit = "[WORLDGEN] placed_feature ad_astra:orbit_ore in biome ad_astra:orbit";
            String orbitHuman = AcquireAskTool.humanWorldgenRoute("zh_tw", orbit);
            assert !orbitHuman.contains("\u7dad\u5ea6\uff1a") : orbitHuman;
        } finally {
            WorldgenIndex.reset();
        }
    }

    private static void mergeOrderAndBudget() {
        List<String> jar = List.of("jar-a", "jar-b");
        List<String> world = List.of("wg-a", "wg-b");
        List<String> loose = List.of("loose-a", "jar-b");
        List<String> got = AcquireAskTool.mergeRoutes("mod:item", "zh_tw", loose, jar, world);
        assert List.of("jar-a", "jar-b", "wg-a", "wg-b", "loose-a").equals(got) : got;
        List<String> clipped = AskToolContext.clipAcquireLines(got, "ping");
        assert clipped.size() == 3 : clipped;
        assert List.of("jar-a", "jar-b", "wg-a").equals(clipped) : clipped;
    }

    private static void sandboxOrSkip() {
        Path game = sandboxDir();
        if (game == null) {
            System.out.println("SKIP sandbox worldgen (packai_sandbox_ftb not present)");
            return;
        }
        WorldgenIndex.reset();
        List<String> lines = WorldgenIndex.routesForItem("ad_astra:moon_desh_ore", game);
        assert lines.stream().anyMatch(l -> l.contains(PLACED)) : lines;
        assert lines.stream().anyMatch(l -> l.contains(CONFIGURED)) : lines;
        assert lines.stream().anyMatch(l -> l.contains(IN_BIOME)) : lines;
        List<String> bedrock = WorldgenIndex.routesForItem("minecraft:bedrock", game);
        assert bedrock.isEmpty() : bedrock;
        assert "ad_astra:moon".equals(WorldgenIndex.dimensionOf("ad_astra:lunar_wastelands"));
        assert WorldgenIndex.dimensionOf("ad_astra:orbit") == null;
        // 14 dimension files -> 11 raw (biome,dim) pairs -> ad_astra:orbit is ambiguous (multi-dim) -> 10 kept.
        assert WorldgenIndex.biomeDimensionCount() == 10 : WorldgenIndex.biomeDimensionCount();
        WorldgenIndex.reset();
    }

    private static Path sandboxDir() {
        String root = System.getProperty("packai.prism");
        if (root == null || root.isBlank()) {
            root = System.getenv("PACKAI_PRISM");
        }
        if (root != null && !root.isBlank()) {
            Path game = Path.of(root, "instances", "packai_sandbox_ftb", "minecraft");
            if (Files.isDirectory(game.resolve("mods"))) {
                return game;
            }
        }
        Path direct = Path.of(
                "C:/Users/skps9/Documents/PrismLauncher-Windows-MinGW-w64-Portable-11.1.0"
                        + "/instances/packai_sandbox_ftb/minecraft");
        return Files.isDirectory(direct.resolve("mods")) ? direct : null;
    }

    private static void writeFixture(Path gameDir) throws Exception {
        Path data = gameDir.resolve("datapacks/t/data");
        write(data.resolve("ad_astra/worldgen/placed_feature/moon_desh_ore.json"),
                "{\"feature\":\"ad_astra:moon_desh_ore\",\"placement\":["
                        + "{\"type\":\"minecraft:count\",\"count\":9},"
                        + "{\"type\":\"minecraft:height_range\",\"height\":{"
                        + "\"type\":\"minecraft:trapezoid\","
                        + "\"min_inclusive\":{\"absolute\":-80},"
                        + "\"max_inclusive\":{\"absolute\":80}}}]}");
        write(data.resolve("ad_astra/worldgen/configured_feature/moon_desh_ore.json"),
                "{\"type\":\"minecraft:ore\",\"config\":{\"size\":9}}");
        write(data.resolve("ad_astra/worldgen/biome/lunar_wastelands.json"),
                "{\"features\":[[\"ad_astra:moon_desh_ore\"]]}");
        write(data.resolve("ad_astra/dimension/moon.json"), fixed("ad_astra:lunar_wastelands"));
        write(data.resolve("ad_astra/dimension/earth_orbit.json"), biomes("ad_astra:orbit"));
        write(data.resolve("ad_astra/dimension/moon_orbit.json"), biomes("ad_astra:orbit"));
    }

    private static void write(Path path, String json) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, json);
    }

    private static String fixed(String biome) {
        return "{\"type\":\"minecraft:dimension\",\"generator\":{\"type\":\"minecraft:noise\","
                + "\"biome_source\":{\"type\":\"minecraft:fixed\",\"biome\":\"" + biome + "\"}}}";
    }

    private static String biomes(String biome) {
        return "{\"type\":\"minecraft:dimension\",\"generator\":{\"type\":\"minecraft:noise\","
                + "\"biome_source\":{\"type\":\"minecraft:multi_noise\",\"biomes\":[{\"biome\":\""
                + biome + "\"}]}}}";
    }
}

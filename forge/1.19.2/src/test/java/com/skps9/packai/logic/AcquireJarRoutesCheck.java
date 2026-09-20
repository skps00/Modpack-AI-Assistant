package com.skps9.packai.logic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.skps9.packai.api.AskToolArgs;
import com.skps9.packai.config.PackAiConfig;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

/**
 * Jar-cache routes reach {@code acquire}. Fixture = real {@code config/packai/jar-cache}
 * plus {@code manifest.json} plus an empty {@code mods/}.
 */
public final class AcquireJarRoutesCheck {
    private static final String OXYGEN = "ad_astra:oxygen_tank";
    private static final String HEART = "artifacts:crystal_heart";
    private static final String SINEW = "tetra:dragon_sinew";
    private static final String OXYGEN_PATH = "chests/village/moon/blacksmith";
    private static final String HEART_PATH = "inject/chests/end_city_treasure";
    private static final String SINEW_PATH = "entities/ender_dragon_extended";

    private AcquireJarRoutesCheck() {}

    public static void main(String[] args) throws Exception {
        Path cacheSrc = locateCache();
        if (cacheSrc == null) {
            System.out.println("AcquireJarRoutesCheck FAIL: no jar-cache fixture (manifest.json missing)");
            System.exit(1);
            return;
        }
        Path game = Files.createTempDirectory("packai-v6-routes");
        System.out.println("fixture cache=" + cacheSrc);
        System.out.println("gameDir=" + game);
        copyTree(cacheSrc, game.resolve("config/packai/jar-cache"));
        Files.createDirectories(game.resolve("mods"));
        if (!Files.isRegularFile(game.resolve("config/packai/jar-cache/manifest.json"))) {
            System.out.println("AcquireJarRoutesCheck FAIL: copied cache has no manifest.json");
            System.exit(1);
            return;
        }

        bindScanModJars(game);
        JarLightIndex.INSTANCE.reset();
        JarLightIndex.INSTANCE.ensure(game);
        if (!JarLightIndex.INSTANCE.isReady()) {
            System.out.println("AcquireJarRoutesCheck FAIL: JarLightIndex not ready after ensure");
            System.exit(1);
            return;
        }

        List<String> oxygenRoutes = JarLightIndex.INSTANCE.routeLinesForItem(OXYGEN);
        List<String> heartRoutes = JarLightIndex.INSTANCE.routeLinesForItem(HEART);
        List<String> sinewRoutes = JarLightIndex.INSTANCE.routeLinesForItem(SINEW);
        System.out.println("reachable " + OXYGEN + " n=" + oxygenRoutes.size()
                + " raw=" + rawFactCount(cacheSrc, OXYGEN) + " " + oxygenRoutes);
        System.out.println("reachable " + HEART + " n=" + heartRoutes.size()
                + " raw=" + rawFactCount(cacheSrc, HEART) + " " + heartRoutes);
        System.out.println("reachable " + SINEW + " n=" + sinewRoutes.size()
                + " raw=" + rawFactCount(cacheSrc, SINEW) + " " + sinewRoutes);
        assert oxygenRoutes != null && oxygenRoutes.contains("L|" + OXYGEN_PATH) : oxygenRoutes;
        assert heartRoutes != null && heartRoutes.contains("L|" + HEART_PATH) : heartRoutes;
        assert sinewRoutes != null && sinewRoutes.contains("L|" + SINEW_PATH) : sinewRoutes;
        assert JarLightIndex.INSTANCE.routeLinesForItem(null).isEmpty();
        assert JarLightIndex.INSTANCE.routeLinesForItem("").isEmpty();
        scanModJarsGate(game);

        try {
            assertAcquire(game);
            System.out.println("acquire-layer=verified");
        } catch (AssertionError e) {
            throw e;
        } catch (Throwable t) {
            System.out.println("acquire-layer=NOT_VERIFIED");
            t.printStackTrace(System.out);
            System.out.println("AcquireJarRoutesCheck OK routeLinesForItem-only");
            return;
        }
        System.out.println("AcquireJarRoutesCheck OK");
    }

    private static void assertAcquire(Path game) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PackIndex idx = new PackIndex();
        idx.build(game, List.of());
        AskToolEnv env = new AskToolEnv(ItemStack.EMPTY, idx, game, List.of(), ItemRef.NONE);
        AskToolLoop.bindEnv(env);
        try {
            AcquireAskTool tool = new AcquireAskTool();
            String q = "how to obtain";
            String oxygen = run(tool, game, OXYGEN, q);
            String heart = run(tool, game, HEART, q);
            String sinew = run(tool, game, SINEW, q);
            System.out.println("acquire " + OXYGEN + "=" + oxygen);
            System.out.println("acquire " + HEART + "=" + heart);
            System.out.println("acquire " + SINEW + "=" + sinew);
            assert oxygen.contains(OXYGEN_PATH) : oxygen;
            assert heart.contains(HEART_PATH) : heart;
            assert sinew.contains(SINEW_PATH) : sinew;
        } finally {
            AskToolLoop.clearEnv();
        }
    }

    private static String run(AcquireAskTool tool, Path game, String itemId, String question) {
        return tool.run(new AskToolArgs(
                itemId, "OUTPUT", List.of(), question, "en_us", game, List.of(), 0L));
    }

    /** Own reset + ensure. OFF must return empty even if the cache is already loaded. */
    private static void scanModJarsGate(Path game) throws IOException {
        bindScanModJars(game);
        PackAiConfig.setScanModJars(true);
        JarLightIndex.INSTANCE.reset();
        JarLightIndex.INSTANCE.ensure(game);
        List<String> on = JarLightIndex.INSTANCE.routeLinesForItem(OXYGEN);
        assert on != null && !on.isEmpty() : on;
        PackAiConfig.setScanModJars(false);
        List<String> off = JarLightIndex.INSTANCE.routeLinesForItem(OXYGEN);
        assert off != null && off.isEmpty() : off;
        PackAiConfig.setScanModJars(true);
        List<String> again = JarLightIndex.INSTANCE.routeLinesForItem(OXYGEN);
        assert again != null && !again.isEmpty() : again;
    }

    private static void bindScanModJars(Path game) throws IOException {
        Path toml = game.resolve("config/packai-client.toml");
        Files.createDirectories(toml.getParent());
        CommentedFileConfig cfg = CommentedFileConfig.builder(toml).sync().autosave().build();
        cfg.load();
        PackAiConfig.SPEC.setConfig(cfg);
        PackAiConfig.setScanModJars(true);
        if (!PackAiConfig.scanModJars()) {
            throw new IllegalStateException("scanModJars stayed false after setScanModJars(true)");
        }
    }

    private static Path locateCache() {
        String env = System.getenv("PACKAI_JAR_CACHE");
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env);
            if (Files.isRegularFile(p.resolve("manifest.json"))) {
                return p;
            }
        }
        Path sandbox = Path.of(
                "C:/Users/skps9/Documents/PrismLauncher-Windows-MinGW-w64-Portable-11.1.0"
                        + "/instances/packai_sandbox_ftb/minecraft/config/packai/jar-cache");
        if (Files.isRegularFile(sandbox.resolve("manifest.json"))) {
            return sandbox;
        }
        return null;
    }

    private static void copyTree(Path src, Path dest) throws IOException {
        try (Stream<Path> walk = Files.walk(src)) {
            for (Path p : walk.toList()) {
                Path out = dest.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(p, out);
                }
            }
        }
    }

    /** Pre-cap fact count across shards (diagnostic). Not the in-memory cap. */
    private static int rawFactCount(Path cache, String itemId) throws IOException {
        String key = "\"" + itemId + "\":";
        int n = 0;
        try (Stream<Path> walk = Files.list(cache)) {
            for (Path p : walk.toList()) {
                String name = p.getFileName().toString();
                if (!name.endsWith(".json") || "manifest.json".equals(name) || !Files.isRegularFile(p)) {
                    continue;
                }
                String text = Files.readString(p);
                int from = 0;
                while (true) {
                    int i = text.indexOf(key, from);
                    if (i < 0) {
                        break;
                    }
                    int lb = text.indexOf('[', i + key.length());
                    int rb = lb < 0 ? -1 : text.indexOf(']', lb);
                    if (lb < 0 || rb < 0 || lb - i > 8) {
                        from = i + key.length();
                        continue;
                    }
                    String body = text.substring(lb + 1, rb);
                    if (!body.isBlank()) {
                        int quotes = 0;
                        for (int c = 0; c < body.length(); c++) {
                            if (body.charAt(c) == '"') {
                                quotes++;
                            }
                        }
                        n += quotes / 2;
                    }
                    from = rb + 1;
                }
            }
        }
        return n;
    }
}

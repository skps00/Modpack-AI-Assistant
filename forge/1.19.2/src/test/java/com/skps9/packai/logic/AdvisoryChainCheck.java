package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Offline harness for S-A1 advisory data layer. No Minecraft bootstrap.
 * Usage:
 * {@code AdvisoryChainCheck --instance <atm8> --instance <startech>}
 */
public final class AdvisoryChainCheck {
    private static final Path DEFAULT_ATM8 = Path.of(
            "C:/Users/skps9/Documents/PrismLauncher-Windows-MinGW-w64-Portable-11.1.0"
                    + "/instances/packai_sandbox_atm8/minecraft");
    private static final Path DEFAULT_STARTECH = Path.of(
            "C:/Users/skps9/Documents/PrismLauncher-Windows-MinGW-w64-Portable-11.1.0"
                    + "/instances/packai_sandbox_startech/minecraft");

    /** 22 cases from plan §6 / cases catalogue (+ Thermal / AE2 / RS / Powah). */
    private static final Case[] CASES = {
            new Case(1, "mekanismgenerators:fission_reactor_casing",
                    "呢個核反應爐點維持唔會停？", "atm8"),
            new Case(2, "biggerreactors:reactor_fuel_rod",
                    "BiggerReactors 反應爐一粒燃料棒燒幾久？", "atm8"),
            new Case(3, "nuclearcraft:fission_reactor_controller",
                    "NuclearCraft 反應爐＋渦輪點配？", "startech"),
            new Case(4, "ftbic:nuclear_reactor",
                    "FTBIC 核反應爐點插燃料棒、跑幾久？", "atm8"),
            new Case(5, "powah:reactor_basic",
                    "Powah 反應爐點補燃料？", "atm8"),
            new Case(6, "createdieselgenerators:diesel_engine",
                    "柴油發電機要用邊種油、燒幾快？", "startech"),
            new Case(7, "immersiveengineering:diesel_generator",
                    "IE 發電機最有效率燒咩？", "atm8"),
            new Case(8, "rftoolsbuilder:builder",
                    "RFTools 挖礦機每格要幾多 RF？點唔會爆箱？", "atm8"),
            new Case(9, "quarryplus:quarry",
                    "Additional Enchanted Miner 點設定先自動開工？", "atm8"),
            new Case(10, "minecolonies:blockhutlumberjack",
                    "MineColonies 大型採石場要幾多人、幾多資源？", "atm8"),
            new Case(11, "occultism:dimensional_mineshaft",
                    "Occultism 異界礦井點運作、幾久一次？", "atm8"),
            new Case(12, "industrialforegoing:laser_drill",
                    "Industrial Foregoing 雷射鑽石機點先出到目標礦？", "atm8"),
            new Case(13, "mysticalagriculture:iron_essence",
                    "我要大量鐵，最快係邊條路？", "atm8"),
            new Case(14, "productivebees:advanced_beehive",
                    "蜜蜂點量產資源？要幾多隻？", "atm8"),
            new Case(15, "create:mechanical_harvester",
                    "Create 收成機／水泵點自動化農場？", "atm8"),
            new Case(16, "gtceu:hv_miner",
                    "GTCEu 礦脈點搵、用咩鑽頭？", "startech"),
            new Case(17, "biggerreactors:reactor_terminal",
                    "唔用 Mekanism，我點維持核反應爐？", "atm8"),
            new Case(18, "minecraft:iron_ingot",
                    "我唔想挖礦，但又要有鐵——有邊幾條路？", "atm8"),
            new Case(19, "thermal:dynamo_stirling",
                    "Thermal 發電機燒咩、每 tick 幾多 RF？", "atm8"),
            new Case(20, "ae2:controller",
                    "AE2 儲存網路容量／vibration chamber 每 tick 幾多？", "atm8"),
            new Case(21, "refinedstorage:controller",
                    "Refined Storage 同 AE2 點揀、有咩容量數字？", "atm8"),
            new Case(22, "powah:reactor_nitro",
                    "Powah 反應爐每 tick 產出幾多？", "atm8"),
    };

    private AdvisoryChainCheck() {}

    public static void main(String[] args) throws Exception {
        List<Path> instances = parseInstances(args);
        if (instances.isEmpty()) {
            instances = List.of(DEFAULT_ATM8, DEFAULT_STARTECH);
        }
        System.out.println("AdvisoryChainCheck instances=" + instances.size());
        for (Path inst : instances) {
            System.out.println("snapshot " + inst.getFileName() + " modsJarHash="
                    + modsJarNameHash(inst) + " path=" + inst);
        }

        Path atm8 = pick(instances, "atm8");
        Path startech = pick(instances, "startech");
        if (atm8 == null) {
            atm8 = instances.get(0);
        }
        if (startech == null) {
            startech = instances.size() > 1 ? instances.get(1) : atm8;
        }

        CtxBundle atm8Ctx = loadCtx(atm8);
        CtxBundle stCtx = startech.equals(atm8) ? atm8Ctx : loadCtx(startech);

        int withNumeric = 0;
        int withRoute = 0;
        int perTickValueLines = 0;
        int perTickBad = 0;
        System.out.println("--- 22 cases ---");
        System.out.println("| # | item | numeric? | route? | tier hits | lines |");
        for (Case c : CASES) {
            CtxBundle bundle = "startech".equals(c.pack) ? stCtx : atm8Ctx;
            List<String> out = AdvisoryChain.build(c.itemId, c.question, bundle.ctx);
            boolean numeric = hasRealNumeric(out);
            if (numeric) {
                withNumeric++;
            }
            boolean route = hasRealRoute(out);
            if (route) {
                withRoute++;
            }
            String tiers = tiersIn(out);
            System.out.println("| " + c.n + " | " + c.itemId + " | " + (numeric ? "YES" : "NO")
                    + " | " + (route ? "YES" : "NO") + " | " + tiers + " | " + out.size() + " |");
            for (String line : out) {
                System.out.println("  " + line);
            }
            assert out.size() <= 12 : "case " + c.n + " >12 lines";
            assert out.stream().anyMatch(l -> l.startsWith("需求:")) : "case " + c.n + " missing 需求";
            assert out.stream().anyMatch(l -> l.startsWith("每 tick:")) : "case " + c.n + " missing 每 tick";
            assert out.stream().anyMatch(l -> l.startsWith("取得途徑") || l.contains("取得途徑:"))
                    : "case " + c.n + " missing 取得途徑";
            assert out.stream().anyMatch(l -> l.contains("省料排序未支援"))
                    : "case " + c.n + " must state 省料未支援";
            for (String line : out) {
                if (line.startsWith("每 tick:") && !line.contains("未收錄")) {
                    assert line.contains("tier=") : line;
                    assert line.contains("source=") : line;
                    perTickValueLines++;
                    if (!AdvisoryChain.RATE_MARKER.matcher(line).find()) {
                        perTickBad++;
                        throw new AssertionError("每 tick without rate marker: " + line);
                    }
                }
                if (line.startsWith("其他數值（非每 tick）:")) {
                    assert line.contains("tier=") : line;
                    assert line.contains("source=") : line;
                }
            }
        }
        System.out.println("numeric_cases=" + withNumeric + "/22 (need >=8)");
        System.out.println("route_cases=" + withRoute + "/22 (D2 target >=12; before≈8)");
        System.out.println("per_tick_value_lines=" + perTickValueLines
                + " per_tick_without_rate_marker=" + perTickBad
                + " (D1 audit: every 每 tick value line must carry rate marker)");
        assert withNumeric >= 8 : "positive lower bound failed: " + withNumeric + "/22";
        assert perTickBad == 0 : "D1 rate-label audit failed";
        assert withRoute >= 12 : "D2 route coverage failed: " + withRoute + "/22";

        negativeControl(atm8Ctx.ctx);
        rankingCounterExample();

        System.out.println("AdvisoryChainCheck OK numeric=" + withNumeric
                + "/22 routes=" + withRoute + "/22 per_tick_lines=" + perTickValueLines);
    }

    private static void negativeControl(AdvisoryChain.PackContext ctx) {
        List<String> out = AdvisoryChain.build(
                "packai:no_such_item_zzz", "zzz", ctx);
        System.out.println("--- negative control ---");
        for (String line : out) {
            System.out.println("  " + line);
        }
        assert out.stream().anyMatch(l -> l.contains("未收錄")) : out;
        for (String line : out) {
            if (line.startsWith("每 tick:") && !line.contains("未收錄")) {
                throw new AssertionError("invented numeric row: " + line);
            }
        }
        System.out.println("negative_control=OK");
    }

    private static void rankingCounterExample() {
        List<AdvisoryChain.Route> different = List.of(
                new AdvisoryChain.Route(AdvisoryChain.BAND_QUEST_ONCE, 0, "quest once"),
                new AdvisoryChain.Route(AdvisoryChain.BAND_FISH, 1, "fish route"),
                new AdvisoryChain.Route(AdvisoryChain.BAND_TRADE, 2, "trade route"));
        AdvisoryChain.Route best = AdvisoryChain.pickFastest(different);
        assert best != null && best.band() == AdvisoryChain.BAND_FISH : best;
        assert "fish route".equals(best.line()) : best;

        List<AdvisoryChain.Route> tied = List.of(
                new AdvisoryChain.Route(AdvisoryChain.BAND_LOOT, 5, "loot-b"),
                new AdvisoryChain.Route(AdvisoryChain.BAND_LOOT, 2, "loot-a"),
                new AdvisoryChain.Route(AdvisoryChain.BAND_TRADE, 0, "trade"));
        List<AdvisoryChain.Route> sorted = new ArrayList<>(tied);
        sorted.sort(Comparator.comparingInt(AdvisoryChain.Route::band)
                .thenComparingInt(AdvisoryChain.Route::seq));
        AdvisoryChain.Route tieBest = AdvisoryChain.pickFastest(tied);
        assert tieBest != null && "loot-a".equals(tieBest.line()) : tieBest;
        String note = AdvisoryChain.tieBreakNote(sorted);
        assert note != null && note.contains("tie-break") : note;
        System.out.println("ranking_counter_example=OK best_band=" + best.band()
                + " tie_note=" + note);
    }

    private static boolean hasRealNumeric(List<String> out) {
        for (String line : out) {
            if (!(line.startsWith("每 tick:") || line.startsWith("其他數值（非每 tick）:"))) {
                continue;
            }
            if (line.contains("未收錄")) {
                continue;
            }
            if ((line.contains("tier=A1") || line.contains("tier=A2"))
                    && line.contains("source=")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRealRoute(List<String> out) {
        for (String line : out) {
            if (line.startsWith("取得途徑:") && !line.contains("未收錄")) {
                return true;
            }
        }
        return false;
    }

    private static String tiersIn(List<String> out) {
        StringBuilder sb = new StringBuilder();
        for (String line : out) {
            if (!(line.startsWith("每 tick:") || line.startsWith("其他數值（非每 tick）:"))
                    || line.contains("未收錄")) {
                continue;
            }
            int i = line.indexOf("tier=");
            if (i < 0) {
                continue;
            }
            int j = line.indexOf(' ', i + 5);
            String t = j < 0 ? line.substring(i + 5) : line.substring(i + 5, j);
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(t);
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static CtxBundle loadCtx(Path gameDir) {
        System.out.println("loading PackConfigFacts " + gameDir + " ...");
        PackConfigFacts facts = new PackConfigFacts(gameDir);
        facts.load();
        System.out.println("  a1=" + facts.a1Count() + " a3=" + facts.a3Count());
        PackIndex idx = new PackIndex();
        System.out.println("loading PackIndex scanners=kubejs,datapacks,ftbquests ...");
        idx.build(gameDir, List.of("kubejs", "datapacks", "ftbquests"));
        System.out.println("  paths=" + idx.paths().size());
        return new CtxBundle(new AdvisoryChain.PackContext(gameDir, idx, facts));
    }

    private static List<Path> parseInstances(String[] args) {
        List<Path> out = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if ("--instance".equals(args[i]) && i + 1 < args.length) {
                out.add(Path.of(args[++i]));
            }
        }
        return out;
    }

    private static Path pick(List<Path> instances, String needle) {
        for (Path p : instances) {
            String s = p.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
            if (s.contains(needle)) {
                return p;
            }
        }
        return null;
    }

    private static String modsJarNameHash(Path gameDir) throws Exception {
        Path mods = gameDir.resolve("mods");
        if (!Files.isDirectory(mods)) {
            return "NONE";
        }
        StringBuilder names = new StringBuilder();
        try (Stream<Path> stream = Files.list(mods)) {
            stream.map(p -> p.getFileName().toString())
                    .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted()
                    .forEach(n -> names.append(n).append('\n'));
        }
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] dig = md.digest(names.toString().getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(dig).substring(0, 16);
    }

    private record Case(int n, String itemId, String question, String pack) {}

    private record CtxBundle(AdvisoryChain.PackContext ctx) {}
}

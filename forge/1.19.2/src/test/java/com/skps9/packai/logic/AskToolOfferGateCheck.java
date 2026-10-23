package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Offline replay of real ask traces through {@link AskToolOfferGate}.
 * No Minecraft bootstrap. Run with {@code -ea} via {@code -I tmp-check.gradle}.
 */
public final class AskToolOfferGateCheck {
    private static Path instancesRoot() {
        String env = System.getenv("PACKAI_PRISM_ROOT");
        String root = (env != null && !env.isBlank())
                ? env
                : "C:/Users/skps9/Documents/PrismLauncher-Windows-MinGW-w64-Portable-11.1.0";
        return Path.of(root).resolve("instances");
    }

    private static final Path INSTANCES_ROOT = instancesRoot();

    private static final Pattern ITEM_IN_ARGS = Pattern.compile(
            "\"item\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern QUERY_IN_ARGS = Pattern.compile(
            "\"query\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DUMP_IN_ARGS = Pattern.compile(
            "\"dump_level\"\\s*:\\s*\"([^\"]+)\"");

    private AskToolOfferGateCheck() {}

    public static void main(String[] args) throws Exception {
        List<Path> gameDirs = discoverGameDirs();
        assert !gameDirs.isEmpty() : "no instances with packai/trace";

        System.out.println("AskToolOfferGateCheck instances=" + gameDirs.size());
        Map<Path, InstanceCtx> ctxByDir = new LinkedHashMap<>();
        for (Path gameDir : gameDirs) {
            String hash = modsJarNameHash(gameDir);
            System.out.println("snapshot " + gameDir.getParent().getFileName()
                    + " modsJarHash=" + hash + " path=" + gameDir);
            ctxByDir.put(gameDir, new InstanceCtx(gameDir, hash));
        }

        int emptyTotal = 0;
        int emptyDropped = 0;
        int asksWithTool = 0;
        int asksWithEmptyAfter = 0;
        int suppressedNonempty = 0;
        int infoLoss = 0;
        List<String> violations = new ArrayList<>();
        List<String> infoLossRows = new ArrayList<>();
        Map<String, String> preAnswerAnchors = Map.of(
                "jei_lookup",
                "AskEngine JeiInfoFacts.splitFromDump(jeiSummary) + markPreAnswered INFO; "
                        + "softMissNoteWhenDumpPresent",
                "acquire",
                "AskEngine acquireFactsDetailed + clipAcquireLines → noteShot0; "
                        + "markPreAnswered only when acqShot blank",
                "purpose_lookup",
                "AskPurposeContext.buildPurposeBlock blank ⇒ PurposeLookupAskTool empty",
                "quest_fetch",
                "Indices.hasQuestHit proven zero (no pre-answer when facts stripped in capable)");

        for (Path gameDir : gameDirs) {
            InstanceCtx ctx = ctxByDir.get(gameDir);
            Path traceDir = gameDir.resolve("packai/trace");
            List<Path> files;
            try (Stream<Path> stream = Files.list(traceDir)) {
                files = stream
                        .filter(p -> {
                            String n = p.getFileName().toString();
                            return n.startsWith("ask-") && n.endsWith(".jsonl");
                        })
                        .sorted()
                        .toList();
            }
            for (Path file : files) {
                AskReplay ask = parseAsk(file);
                if (ask.calls.isEmpty()) {
                    continue;
                }
                asksWithTool++;
                AskToolOfferGate gate = new AskToolOfferGate();
                AskToolOfferGate.Indices indices = ctx.indices();
                seedPreAnswered(gate, ask, indices, ctx);
                boolean stillEmpty = false;
                for (ToolCall call : ask.calls) {
                    String focus = !call.item.isBlank() ? call.item : ask.focusItem;
                    Set<String> offered = gate.offer(
                            ask.question, focus, indices, AskToolLoop.CAPABLE_TOOLS);
                    boolean wouldAllow = gate.wouldAllowCall(
                            call.name, focus, call.dump, offered);
                    if (call.empty) {
                        emptyTotal++;
                        if (!wouldAllow) {
                            emptyDropped++;
                        } else {
                            stillEmpty = true;
                        }
                        gate.noRetry(call.name, AskToolOfferGate.argsKey(
                                call.item, call.query, call.dump));
                    } else if (!wouldAllow) {
                        suppressedNonempty++;
                        boolean reproduced = deterministicallyReproduced(call, ask, indices, ctx);
                        if (!reproduced) {
                            infoLoss++;
                            String row = gameDir.getParent().getFileName()
                                    + "/" + file.getFileName()
                                    + " tool=" + call.name
                                    + " item=" + focus
                                    + " dump=" + call.dump
                                    + " anchor=" + preAnswerAnchors.getOrDefault(call.name, "?");
                            infoLossRows.add(row);
                            violations.add(row + " reason=nonempty_wrongly_dropped");
                        }
                    }
                }
                if (stillEmpty) {
                    asksWithEmptyAfter++;
                }
            }
        }

        System.out.println("empty_calls_total=" + emptyTotal);
        System.out.println("empty_calls_would_be_dropped=" + emptyDropped
                + " pct=" + pct(emptyDropped, emptyTotal));
        System.out.println("nonempty_calls_wrongly_dropped=" + violations.size());
        for (String v : violations) {
            System.out.println("VIOLATION " + v);
        }
        System.out.println("asks_with_tool_rounds=" + asksWithTool);
        System.out.println("asks_with_any_empty_after=" + asksWithEmptyAfter
                + " pct=" + pct(asksWithEmptyAfter, asksWithTool));
        System.out.println("suppressed_nonempty_calls=" + suppressedNonempty);
        System.out.println("suppressed_nonempty_not_reproduced=" + infoLoss);
        for (String row : infoLossRows) {
            System.out.println("INFO_LOSS " + row);
        }
        System.out.println("pre_answer_anchors:");
        for (Map.Entry<String, String> e : preAnswerAnchors.entrySet()) {
            System.out.println("  " + e.getKey() + " <- " + e.getValue());
        }

        assert violations.isEmpty()
                : "nonempty_calls_wrongly_dropped must be 0, got " + violations.size()
                + " first=" + (violations.isEmpty() ? "" : violations.get(0));
        assert infoLoss == 0
                : "suppressed_nonempty_not_reproduced must be 0, got " + infoLoss;

        negativeControl();
        determinismSmoke(ctxByDir.values().iterator().next());

        System.out.println("AskToolOfferGateCheck OK");
    }

    /**
     * Mirror AskEngine shot-0 pre-answer marks using offline indexes only.
     * jei INFO always (JeiInfoFacts path). acquire when clipped shot-0 would be blank.
     */
    private static void seedPreAnswered(
            AskToolOfferGate gate,
            AskReplay ask,
            AskToolOfferGate.Indices indices,
            InstanceCtx ctx
    ) {
        String focus = ask.focusItem == null ? "" : ask.focusItem;
        if (focus.isBlank()) {
            return;
        }
        gate.markPreAnswered("jei_lookup", focus, "INFO");
        if (acquireShot0Blank(focus, ask.question, ctx)) {
            gate.markPreAnswered("acquire", focus, "");
            gate.markPreAnswered("acquire", focus, "FULL");
            gate.markPreAnswered("acquire", focus, "OUTPUT");
        }
        // purpose_lookup: blank purpose cannot be proven offline without tooltip/JEI info;
        // leave unmarked (0 historical empties).
    }

    private static boolean acquireShot0Blank(String itemId, String question, InstanceCtx ctx) {
        if (ctx.packIndex() == null) {
            return false;
        }
        try {
            PackIndex.AcquireFacts bundle = ctx.packIndex().acquireFactsDetailed(
                    itemId, "zh_tw", List.of());
            List<String> lines = AskToolContext.clipAcquireLines(
                    AcquireAskTool.mergeJarRoutes(itemId, "zh_tw", bundle.lines()),
                    question);
            return lines == null || lines.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Control #4: suppressed nonempty call still covered by deterministic path.
     * jei INFO nonempty never appears in corpus; acquire nonempty must not be suppressed
     * when shot-0 was non-blank (seed only marks blank shot-0).
     */
    private static boolean deterministicallyReproduced(
            ToolCall call,
            AskReplay ask,
            AskToolOfferGate.Indices indices,
            InstanceCtx ctx
    ) {
        String focus = !call.item.isBlank() ? call.item : ask.focusItem;
        if ("jei_lookup".equals(call.name) && "INFO".equalsIgnoreCase(call.dump)) {
            // JeiInfoFacts.splitFromDump already extracted INFO into acquire/purpose facts.
            return true;
        }
        if ("acquire".equals(call.name)) {
            // We only suppress acquire when shot-0 clip is blank ⇒ nonempty must not land here.
            return acquireShot0Blank(focus, ask.question, ctx);
        }
        return false;
    }

    private static void negativeControl() {
        AskToolOfferGate gate = new AskToolOfferGate();
        AskToolOfferGate.Indices none = AskToolOfferGate.fixed(
                Boolean.FALSE, Boolean.FALSE, Boolean.FALSE);
        Set<String> offered = gate.offer(
                "這東西點用？", "mod:missing_item_zzz", none, AskToolLoop.CAPABLE_TOOLS);
        assert !offered.contains("consume_use") : offered;
        assert !offered.contains("quest_fetch") : offered;
        assert !offered.contains("acquire") : offered;
        assert offered.contains("jei_lookup") : offered;
        gate.markPreAnswered("jei_lookup", "mod:missing_item_zzz", "INFO");
        assert !gate.wouldAllowCall(
                "jei_lookup", "mod:missing_item_zzz", "INFO", offered)
                : "INFO pre-answered must block";
        assert gate.wouldAllowCall(
                "jei_lookup", "mod:missing_item_zzz", "OUTPUT", offered)
                : "OUTPUT must stay when only INFO pre-answered";
        List<String> lines = gate.deterministicFactLines("mod:missing_item_zzz");
        assert lines.stream().anyMatch(l -> l.contains("未收錄")) : lines;
        Set<String> offered2 = gate.offer(
                "這東西點用？", "mod:missing_item_zzz", none, AskToolLoop.CAPABLE_TOOLS);
        assert offered.equals(offered2) : offered + " vs " + offered2;
        System.out.println("negative_control OK lines=" + lines.size());
    }

    private static void determinismSmoke(InstanceCtx ctx) {
        AskToolOfferGate a = new AskToolOfferGate();
        AskToolOfferGate b = new AskToolOfferGate();
        AskToolOfferGate.Indices idx = ctx.indices();
        a.markPreAnswered("jei_lookup", "minecraft:dirt", "INFO");
        b.markPreAnswered("jei_lookup", "minecraft:dirt", "INFO");
        Set<String> o1 = a.offer("test", "minecraft:dirt", idx, AskToolLoop.CAPABLE_TOOLS);
        Set<String> o2 = b.offer("test", "minecraft:dirt", idx, AskToolLoop.CAPABLE_TOOLS);
        assert o1.equals(o2) : o1 + " vs " + o2;
        assert !a.wouldAllowCall("jei_lookup", "minecraft:dirt", "INFO", o1);
        assert a.wouldAllowCall("jei_lookup", "minecraft:dirt", "OUTPUT", o1);
        System.out.println("determinism OK offered=" + o1.size());
    }

    private static List<Path> discoverGameDirs() throws Exception {
        LinkedHashSet<Path> out = new LinkedHashSet<>();
        if (!Files.isDirectory(INSTANCES_ROOT)) {
            return List.of();
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(INSTANCES_ROOT)) {
            for (Path inst : ds) {
                Path game = inst.resolve("minecraft");
                Path trace = game.resolve("packai/trace");
                if (Files.isDirectory(trace)) {
                    try (Stream<Path> stream = Files.list(trace)) {
                        boolean any = stream.anyMatch(p -> {
                            String n = p.getFileName().toString();
                            return n.startsWith("ask-") && n.endsWith(".jsonl");
                        });
                        if (any) {
                            out.add(game);
                        }
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    private static AskReplay parseAsk(Path file) throws Exception {
        String question = "";
        String focus = "";
        ArrayList<ToolCall> calls = new ArrayList<>();
        Map<String, String> pendingArgs = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            JsonObject o;
            try {
                o = JsonParser.parseString(line).getAsJsonObject();
            } catch (Throwable t) {
                continue;
            }
            String event = str(o, "event");
            if ("send.user".equals(event) && question.isBlank()) {
                question = extractQuestion(str(o, "content"));
            }
            if ("tool.call".equals(event)) {
                String name = str(o, "name");
                String args = str(o, "args");
                int round = o.has("round") ? o.get("round").getAsInt() : 0;
                pendingArgs.put(name + "#" + round, args);
                if (focus.isBlank()) {
                    focus = field(args, ITEM_IN_ARGS);
                }
            }
            if ("tool.result".equals(event)) {
                String name = str(o, "name");
                int round = o.has("round") ? o.get("round").getAsInt() : 0;
                String args = pendingArgs.getOrDefault(name + "#" + round, "");
                String result = o.has("result") && !o.get("result").isJsonNull()
                        ? o.get("result").getAsString()
                        : "";
                boolean empty = result == null || result.isBlank();
                calls.add(new ToolCall(
                        name,
                        field(args, ITEM_IN_ARGS),
                        field(args, QUERY_IN_ARGS),
                        field(args, DUMP_IN_ARGS),
                        empty));
            }
        }
        if (focus.isBlank()) {
            focus = focusFromFilename(file.getFileName().toString());
        }
        return new AskReplay(question, focus, List.copyOf(calls));
    }

    private static String extractQuestion(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String c = content.trim();
        if (c.startsWith("{")) {
            try {
                JsonObject o = JsonParser.parseString(c).getAsJsonObject();
                if (o.has("question") && o.get("question").isJsonPrimitive()) {
                    return o.get("question").getAsString();
                }
            } catch (Throwable ignored) {
                // plain text
            }
        }
        return c;
    }

    private static String focusFromFilename(String name) {
        if (name == null || !name.startsWith("ask-") || !name.endsWith(".jsonl")) {
            return "";
        }
        String body = name.substring(4, name.length() - 6);
        int second = body.indexOf('-');
        if (second < 0) {
            return "";
        }
        int third = body.indexOf('-', second + 1);
        if (third < 0) {
            return "";
        }
        String idPart = body.substring(third + 1);
        int us = idPart.indexOf('_');
        if (us <= 0) {
            return idPart.replace('_', ':');
        }
        return idPart.substring(0, us) + ":" + idPart.substring(us + 1);
    }

    private static String field(String args, Pattern p) {
        if (args == null || args.isBlank()) {
            return "";
        }
        Matcher m = p.matcher(args);
        return m.find() ? m.group(1) : "";
    }

    private static String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return "";
        }
        try {
            return o.get(key).getAsString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String pct(int num, int den) {
        if (den <= 0) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.1f%%", 100.0 * num / den);
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

    private static final class InstanceCtx {
        final Path gameDir;
        final String modsHash;
        private AskToolOfferGate.LiveIndices indices;
        private PackIndex packIndex;

        InstanceCtx(Path gameDir, String modsHash) {
            this.gameDir = gameDir;
            this.modsHash = modsHash;
        }

        PackIndex packIndex() {
            indices();
            return packIndex;
        }

        AskToolOfferGate.Indices indices() {
            if (indices == null) {
                System.out.println("loading indexes " + gameDir.getParent().getFileName() + " ...");
                packIndex = new PackIndex();
                packIndex.build(gameDir, List.of("kubejs", "datapacks", "ftbquests"));
                ItemConsumeUseFacts.reset();
                ItemConsumeUseFacts.ensure(gameDir);
                indices = new AskToolOfferGate.LiveIndices(
                        packIndex,
                        gameDir,
                        List.of("ftbquests", "heracles"),
                        "zh_tw",
                        List.of());
            }
            return indices;
        }
    }

    private record AskReplay(String question, String focusItem, List<ToolCall> calls) {}

    private record ToolCall(
            String name, String item, String query, String dump, boolean empty
    ) {}
}

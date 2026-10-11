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
    private static final Path INSTANCES_ROOT = Path.of(
            "C:/Users/skps9/Documents/PrismLauncher-Windows-MinGW-w64-Portable-11.1.0/instances");

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
        List<String> violations = new ArrayList<>();

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
                boolean stillEmpty = false;
                for (ToolCall call : ask.calls) {
                    String focus = !call.item.isBlank() ? call.item : ask.focusItem;
                    Set<String> offered = gate.offer(
                            ask.question, focus, indices, AskToolLoop.CAPABLE_TOOLS);
                    boolean wouldOffer = offered.contains(call.name);
                    if (call.empty) {
                        emptyTotal++;
                        if (!wouldOffer) {
                            emptyDropped++;
                        } else {
                            stillEmpty = true;
                        }
                        gate.noRetry(call.name, AskToolOfferGate.argsKey(
                                call.item, call.query, call.dump));
                    } else if (!wouldOffer) {
                        violations.add(gameDir.getParent().getFileName()
                                + "/" + file.getFileName()
                                + " tool=" + call.name
                                + " item=" + focus
                                + " reason=nonempty_wrongly_dropped");
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

        assert violations.isEmpty()
                : "nonempty_calls_wrongly_dropped must be 0, got " + violations.size()
                + " first=" + (violations.isEmpty() ? "" : violations.get(0));

        negativeControl();
        determinismSmoke(ctxByDir.values().iterator().next());

        System.out.println("AskToolOfferGateCheck OK");
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
        List<String> lines = gate.deterministicFactLines("mod:missing_item_zzz");
        assert lines.stream().anyMatch(l -> l.contains("未收錄")) : lines;
        // same inputs → same offered set
        Set<String> offered2 = gate.offer(
                "這東西點用？", "mod:missing_item_zzz", none, AskToolLoop.CAPABLE_TOOLS);
        assert offered.equals(offered2) : offered + " vs " + offered2;
        System.out.println("negative_control OK lines=" + lines.size());
    }

    private static void determinismSmoke(InstanceCtx ctx) {
        AskToolOfferGate a = new AskToolOfferGate();
        AskToolOfferGate b = new AskToolOfferGate();
        AskToolOfferGate.Indices idx = ctx.indices();
        Set<String> o1 = a.offer("test", "minecraft:dirt", idx, AskToolLoop.CAPABLE_TOOLS);
        Set<String> o2 = b.offer("test", "minecraft:dirt", idx, AskToolLoop.CAPABLE_TOOLS);
        assert o1.equals(o2) : o1 + " vs " + o2;
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
        // ask-YYYYMMDD-HHMMSS-mod_item.jsonl
        if (name == null || !name.startsWith("ask-") || !name.endsWith(".jsonl")) {
            return "";
        }
        String body = name.substring(4, name.length() - 6);
        int last = body.lastIndexOf('-');
        if (last < 0) {
            return "";
        }
        // strip timestamp prefix YYYYMMDD-HHMMSS-
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

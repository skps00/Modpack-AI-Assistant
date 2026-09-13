package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Headless AskTrace IO. Run with -ea. */
public final class AskTraceCheck {
    private AskTraceCheck() {}

    public static void main(String[] args) throws Exception {
        assert AskTrace.DEFAULT_ENABLED : "askTraceJsonl default true";
        assert AskTrace.DEFAULT_KEEP_FILES == 50 : "askTraceKeepFiles default 50";
        assert AskTrace.KEEP_MIN == 1 && AskTrace.KEEP_MAX == 500;

        writeThreeEvents();
        rotateKeepsIndex();
        unwritableWarnsNoThrow();
        focusIdColonFilename();
        maskApiKeys();

        System.out.println("AskTraceCheck OK");
    }

    private static void writeThreeEvents() throws Exception {
        AskTrace.resetForTest();
        Path tmp = Files.createTempDirectory("packai-ask-trace-write");
        AskTrace.begin(tmp, "how to craft dirt?", "minecraft:dirt", true, 50);
        AskTrace.event("send.user", o -> o.addProperty("content", "how to craft dirt?"));
        AskTrace.toolCall("jei_lookup", "{\"item_id\":\"minecraft:dirt\"}", 0);
        AskTrace.toolResult("jei_lookup", "JEI dump", 0);
        AskTrace.close("ok", 1, 2);

        Path dir = AskTrace.traceDir(tmp);
        List<Path> asks = Files.list(dir)
                .filter(p -> p.getFileName().toString().startsWith("ask-"))
                .collect(Collectors.toList());
        assert asks.size() == 1 : asks;
        Path file = asks.get(0);
        assert Files.isRegularFile(file);
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assert lines.size() == 3 : lines;
        String[] want = {"send.user", "tool.call", "tool.result"};
        for (int i = 0; i < 3; i++) {
            JsonObject o = JsonParser.parseString(lines.get(i)).getAsJsonObject();
            assert o.has("event") : lines.get(i);
            assert o.has("ts") : lines.get(i);
            assert want[i].equals(o.get("event").getAsString()) : o;
        }
        JsonObject call = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        assert "jei_lookup".equals(call.get("name").getAsString());
        assert call.has("args") && call.has("round");
        Path index = dir.resolve("index.jsonl");
        assert Files.isRegularFile(index);
        JsonObject idx = JsonParser.parseString(
                Files.readAllLines(index, StandardCharsets.UTF_8).get(0)).getAsJsonObject();
        assert idx.has("ts") && idx.has("question") && idx.has("focusId");
        assert idx.has("file") && idx.has("rounds") && idx.has("cardsOut") && idx.has("status");
        assert "ok".equals(idx.get("status").getAsString());
        assert "minecraft:dirt".equals(idx.get("focusId").getAsString());
        assert idx.get("file").getAsString().startsWith("ask-");
        assert idx.get("file").getAsString().endsWith(".jsonl");
        assert Files.isRegularFile(dir.resolve(idx.get("file").getAsString()));
        assert idx.get("rounds").getAsInt() == 1;
        assert idx.get("cardsOut").getAsInt() == 2;
    }

    private static void rotateKeepsIndex() throws Exception {
        AskTrace.resetForTest();
        Path tmp = Files.createTempDirectory("packai-ask-trace-rotate");
        for (int i = 0; i < 3; i++) {
            AskTrace.begin(tmp, "q" + i, "item" + i, true, 2);
            AskTrace.event("send.user", o -> o.addProperty("content", "q"));
            AskTrace.close("ok", 0, 0);
            Thread.sleep(30);
        }
        Path dir = AskTrace.traceDir(tmp);
        List<String> asks = Files.list(dir)
                .map(p -> p.getFileName().toString())
                .filter(n -> n.startsWith("ask-") && n.endsWith(".jsonl"))
                .sorted()
                .collect(Collectors.toList());
        assert asks.size() == 2 : asks;
        assert Files.isRegularFile(dir.resolve("index.jsonl")) : "index.jsonl must remain";
        List<String> indexLines = Files.readAllLines(dir.resolve("index.jsonl"), StandardCharsets.UTF_8);
        assert indexLines.size() == 3 : indexLines;
    }

    private static void unwritableWarnsNoThrow() throws Exception {
        AskTrace.resetForTest();
        Path blocker = Files.createTempFile("packai-ask-trace-blocked", ".file");
        Files.writeString(blocker, "not-a-dir", StandardCharsets.UTF_8);
        AskTrace.begin(blocker, "q", "minecraft:dirt", true, 50);
        AskTrace.event("send.user", o -> o.addProperty("content", "hello"));
        AskTrace.toolResult("x", "y", 1);
        AskTrace.close("error");
        assert AskTrace.warnCount() >= 1 : "unwritable must warn";
        assert !AskTrace.active();
    }

    private static void focusIdColonFilename() {
        AskTrace.resetForTest();
        String name = AskTrace.askFileName("20260914-051400", "minecraft:dirt");
        assert name.equals("ask-20260914-051400-minecraft_dirt.jsonl") : name;
        assert !name.contains(":") : name;
        String none = AskTrace.askFileName("20260914-051400", "");
        assert none.equals("ask-20260914-051400-none.jsonl") : none;
    }

    private static void maskApiKeys() {
        String masked = AskTrace.maskSecrets("Authorization: Bearer sk-abcdefghijklmnopqrstuvwxyz key=sk-or-v1-abcdefgh player=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assert !masked.contains("sk-abcdefghijklmnopqrstuvwxyz") : masked;
        assert !masked.contains("sk-or-v1-abcdefgh") : masked;
        assert masked.contains("***") : masked;
        assert !masked.contains("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee") : masked;
        String fname = "ask-20260914-063019-minecraft_dirt.jsonl";
        assert fname.equals(AskTrace.maskSecrets(fname)) : AskTrace.maskSecrets(fname);
        assert !AskTrace.maskSecrets(fname).contains("***") : AskTrace.maskSecrets(fname);
        String keyed = AskTrace.maskSecrets("key=sk-or-v1-abcdefghijklmnop");
        assert !keyed.contains("sk-or-v1-abcdefghijklmnop") : keyed;
    }
}

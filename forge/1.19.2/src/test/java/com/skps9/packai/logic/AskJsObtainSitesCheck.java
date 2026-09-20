package com.skps9.packai.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Plan v6.14 單 1 harness: JsObtainSites + PLAYER_VISIBLE_FORBIDDEN + S16/S17 cores.
 * Prefers NFWC kubejs tree when present; else embedded A_hard snippets.
 */
public final class AskJsObtainSitesCheck {
    private static final Pattern FORBIDDEN = Pattern.compile(
            JsObtainSites.PLAYER_VISIBLE_FORBIDDEN);

    private AskJsObtainSitesCheck() {}

    public static void main(String[] args) throws Exception {
        assert "(src:)|(\\.js\\b)|(\\.json\\b)|(kubejs[/\\\\])"
                .equals(JsObtainSites.PLAYER_VISIBLE_FORBIDDEN)
                : "PLAYER_VISIBLE_FORBIDDEN literal drift";

        forbiddenNegControls();
        triggerLabelRules();
        parseAHardFromPackOrSnippets();
        System.out.println("AskJsObtainSitesCheck OK");
    }

    static void forbiddenNegControls() {
        assert FORBIDDEN.matcher("src:b_a_d_rclick.js:87").find();
        assert FORBIDDEN.matcher("x.js:12").find();
        assert FORBIDDEN.matcher("kubejs/server_scripts/a.js").find();
        assert FORBIDDEN.matcher("note.json").find();
        assert !FORBIDDEN.matcher("【腳本產出】伏特加（b_a_d:vodka）｜觸發：右鍵").find();
        System.out.println("forbiddenNegControls OK");
    }

    static void triggerLabelRules() {
        JsObtainSites.resetLabelFns();
        JsObtainSites.entityLabelFn = id -> "minecraft:wolf".equals(id) ? "狼" : id;
        JsObtainSites.blockLabelFn = id -> switch (id) {
            case "golden_age:idol" -> "邪异泥塑";
            case "ino_dlc_build:pandora_box" -> "潘多拉魔盒";
            case "minecraft:campfire" -> "营火";
            default -> id;
        };

        assert "右鍵".equals(JsObtainSites.triggerLabel(new JsObtainSites.Trigger(
                JsObtainSites.TriggerKind.RIGHT_CLICK, "e", "r", 1, null, null)));
        assert "玩家攻擊命中".equals(JsObtainSites.triggerLabel(new JsObtainSites.Trigger(
                JsObtainSites.TriggerKind.HURT_BY_PLAYER, "e", "r", 1, null, null)));
        assert "狼死亡".equals(JsObtainSites.triggerLabel(new JsObtainSites.Trigger(
                JsObtainSites.TriggerKind.ENTITY_DEATH, "e", "r", 1, null, "minecraft:wolf")));
        assert "邪异泥塑右鍵".equals(JsObtainSites.triggerLabel(new JsObtainSites.Trigger(
                JsObtainSites.TriggerKind.BLOCK_RIGHT_CLICK, "e", "r", 1, null, "golden_age:idol")));
        assert "潘多拉魔盒右鍵".equals(JsObtainSites.triggerLabel(new JsObtainSites.Trigger(
                JsObtainSites.TriggerKind.BLOCK_RIGHT_CLICK, "e", "r", 1, null,
                "ino_dlc_build:pandora_box")));
        assert "营火右鍵".equals(JsObtainSites.triggerLabel(new JsObtainSites.Trigger(
                JsObtainSites.TriggerKind.BLOCK_RIGHT_CLICK, "e", "r", 1, null,
                "minecraft:campfire")));

        // ENTITY_DEATH must not need OfficialDisplay
        OfficialDisplay.lookup = id -> {
            throw new AssertionError("ENTITY_DEATH must not call OfficialDisplay");
        };
        assert "狼死亡".equals(JsObtainSites.triggerLabel(new JsObtainSites.Trigger(
                JsObtainSites.TriggerKind.ENTITY_DEATH, "e", "r", 1, null, "minecraft:wolf")));
        OfficialDisplay.resetLookup();

        String[] neg = {
                "发酵桶右鍵", "發酵桶右鍵", "器官右鍵", "觸發：發酵桶",
                "觸發：右鍵（手持", "觸發：右鍵（器官", "神像右鍵", "潘多拉嵌板右鍵"
        };
        for (String n : neg) {
            assert !"狼死亡".equals(n);
            assert !JsObtainSites.triggerLabel(new JsObtainSites.Trigger(
                    JsObtainSites.TriggerKind.RIGHT_CLICK, "e", "r", 1, null, null)).contains("發酵桶");
        }
        JsObtainSites.resetLabelFns();
        // headless fallback form
        assert "minecraft:wolf死亡".equals(JsObtainSites.triggerLabel(new JsObtainSites.Trigger(
                JsObtainSites.TriggerKind.ENTITY_DEATH, "e", "r", 1, null, "minecraft:wolf")));
        System.out.println("triggerLabelRules OK");
    }

    static void parseAHardFromPackOrSnippets() throws Exception {
        Path fixture = Path.of("docs/plans/fixtures/2026-09-16_nfwc_js_obtain_inventory.json");
        if (!Files.isRegularFile(fixture)) {
            fixture = Path.of("../../docs/plans/fixtures/2026-09-16_nfwc_js_obtain_inventory.json");
        }
        if (!Files.isRegularFile(fixture)) {
            // gradle cwd = forge/1.19.2
            fixture = Path.of("../..").resolve("docs/plans/fixtures/2026-09-16_nfwc_js_obtain_inventory.json")
                    .normalize();
        }
        JsonObject fix = JsonParser.parseString(Files.readString(fixture, StandardCharsets.UTF_8))
                .getAsJsonObject();
        JsonArray s17 = fix.getAsJsonArray("s17_sites");
        assert s17.size() == 11 : "s17_sites size " + s17.size();

        Path kubeRoot = Path.of(
                System.getProperty(
                        "packai.prism",
                        "C:/Users/skps9/Documents/PrismLauncher-Windows-MinGW-w64-Portable-11.1.0"),
                "instances",
                "AI_test_NFWC_DIM",
                "minecraft");
        List<JsObtainSites.Site> all = new ArrayList<>();
        if (Files.isDirectory(kubeRoot.resolve("kubejs"))) {
            PackIndex idx = new PackIndex();
            idx.build(kubeRoot, List.of("kubejs"));
            Set<String> aHard = new LinkedHashSet<>();
            for (JsonElement el : s17) {
                aHard.add(el.getAsJsonObject().get("outId").getAsString());
            }
            for (String id : aHard) {
                all.addAll(idx.jsObtainSitesFor(id));
            }
            // S2 edges via acquire
            for (String id : aHard) {
                PackIndex.AcquireFacts facts = idx.acquireFactsDetailed(id, "zh_cn", List.of());
                boolean edge = facts.rankedSkipEdges().stream()
                        .anyMatch(e -> e.contains("-[js_produce]->"));
                assert edge : "missing js_produce edge for " + id + " " + facts.rankedSkipEdges();
                for (String line : facts.lines()) {
                    assert !FORBIDDEN.matcher(line).find() : "forbidden in fact: " + line;
                }
                // organ/held presence for table-key vodka
                if ("b_a_d:vodka".equals(id)) {
                    String joined = String.join("\n", facts.lines());
                    assert joined.contains("b_a_d:keg") || joined.contains("发酵桶")
                            || joined.contains("發酵桶")
                            : "vodka missing organ: " + joined;
                    assert joined.contains("玻璃瓶") || joined.contains("glass_bottle")
                            || joined.contains("minecraft:glass_bottle")
                            : "vodka missing held: " + joined;
                }
            }
            System.out.println("packIndexAcquire OK sites=" + all.size());
        } else {
            System.out.println("NFWC kubejs missing — snippet-only mode");
            all.addAll(parseSnippets());
        }

        Set<String> got = new HashSet<>();
        for (JsObtainSites.Site s : all) {
            if (s.kind() != JsObtainSites.Kind.PRODUCE) {
                continue;
            }
            got.add(s.outId() + "|" + s.rel() + "|" + s.line());
        }
        for (JsonElement el : s17) {
            JsonObject o = el.getAsJsonObject();
            String key = o.get("outId").getAsString() + "|" + o.get("rel").getAsString()
                    + "|" + o.get("line").getAsInt();
            assert got.contains(key) : "missing site " + key + " got=" + got;
        }

        // S17 diag
        Path tmp = Files.createTempDirectory("packai-js-obtain");
        try {
            JsObtainSites.diagLogOverride = Boolean.TRUE;
            Path diag = JsObtainSites.diagFile(tmp);
            if (Files.exists(diag)) {
                Files.delete(diag);
            }
            int n = 0;
            for (JsonElement el : s17) {
                JsonObject o = el.getAsJsonObject();
                for (JsObtainSites.Site s : all) {
                    if (s.kind() != JsObtainSites.Kind.PRODUCE) {
                        continue;
                    }
                    if (s.outId().equals(o.get("outId").getAsString())
                            && s.rel().equals(o.get("rel").getAsString())
                            && s.line() == o.get("line").getAsInt()) {
                        JsObtainSites.appendDiag(tmp, s);
                        n++;
                        break;
                    }
                }
            }
            assert Files.isRegularFile(diag) : "diag missing";
            List<String> rows = Files.readAllLines(diag, StandardCharsets.UTF_8);
            assert rows.size() == 11 : "diag rows " + rows.size() + " want 11 (not outId count)";
            assert n == 11 : "appended " + n;
            JsObtainSites.diagLogOverride = Boolean.FALSE;
            Path diagOff = Files.createTempDirectory("packai-js-obtain-off");
            JsObtainSites.appendDiag(diagOff, all.get(0));
            assert !Files.exists(JsObtainSites.diagFile(diagOff));
        } finally {
            JsObtainSites.diagLogOverride = null;
        }
        System.out.println("parseAHard OK");
    }

    /** Minimal embedded snippets covering vodka + believe shapes when pack absent. */
    static List<JsObtainSites.Site> parseSnippets() {
        List<JsObtainSites.Site> out = new ArrayList<>();
        out.addAll(JsObtainSites.parse(
                "kubejs/server_scripts/b_a_d/b_a_d_rclick.js",
                """
                const BADOrganRightClickedOnlyStrategies = {
                \t'b_a_d:keg': function (event, organ) {
                \t\tlet item = event.item
                \t\tif (item == 'minecraft:glass_bottle') {
                \t\tlet random = Math.random()
                \t\t\tif (random < 0.4) {
                \t\t\t    player.give(Item.of('b_a_d:vodka'))
                \t\t\t}
                \t\t}
                \t},
                }
                """));
        return out;
    }
}

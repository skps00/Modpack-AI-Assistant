package com.skps9.packai.logic;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Official display-name rule: label from lookup, never invent from id tokens.
 * Run with -ea.
 */
public final class AskDisplayNameCheck {
    private AskDisplayNameCheck() {}

    private static final String FOCUS = "stray_expansion:chestopener_command";
    private static final String PEER = "stray_expansion:chestopener_dsteellightning";
    /** Bare {@code js:<n>} token; lookbehind avoids matching inside {@code kubejs:}. */
    private static final Pattern JS_BARE = Pattern.compile("(?<![A-Za-z0-9_])js:\\d+");
    private static final Pattern FULLWIDTH_PAREN = Pattern.compile("（([^）]+)）");
    private static final Pattern COUNT_DIGITS = Pattern.compile("count:\\d+");
    /** Every namespace refused by {@code OfficialDisplay.annotatableNamespace} / NS_DENY. */
    private static final Pattern DENY_NS_IN_PAREN = Pattern.compile(
            "(?i)(?:item|source|tier|note|file|mechanic|count|via|held|gets|entity|table"
                    + "|gateway|structure|dimension|from|src|rel):");
    private static final List<String> NS_DENY_LIST = List.of(
            "item",
            "source",
            "tier",
            "note",
            "file",
            "mechanic",
            "count",
            "via",
            "held",
            "gets",
            "entity",
            "table",
            "gateway",
            "structure",
            "dimension",
            "from",
            "src",
            "rel");

    public static void main(String[] args) {
        OfficialDisplay.lookup = id -> {
            String k = id == null ? "" : id.toLowerCase(Locale.ROOT);
            if (PEER.equals(k)) {
                return "龙霆钢开胸器";
            }
            if ("minecraft:iron_pickaxe".equals(k)) {
                return "鐵鎬";
            }
            if (FOCUS.equals(k)) {
                return "命令开胸器";
            }
            return "";
        };
        try {
            positiveLabeled();
            peerSectionAndNoInvented();
            mechanicFactItemPrefixNoMangle();
            itemMarkerUntouched();
            proseItemColonUntouched();
            unresolvedKeepsId();
            promptRulePresent();
            peerAndBodyJunkRejected();
            translationKeyNotOfficial();
            System.out.println("AskDisplayNameCheck OK");
        } finally {
            OfficialDisplay.resetLookup();
        }
    }

    static void positiveLabeled() {
        String lab = OfficialDisplay.labeled(PEER);
        assert lab.contains("龙霆钢开胸器") : lab;
        assert lab.contains(PEER) : lab;
        assert !lab.contains("暗鋼閃電") && !lab.contains("暗钢闪电") : lab;
        String iron = OfficialDisplay.labeled("minecraft:iron_pickaxe");
        assert iron.contains("鐵鎬") : iron;
        assert iron.contains("minecraft:iron_pickaxe") : iron;
        System.out.println("positiveLabeled OK");
    }

    static void peerSectionAndNoInvented() {
        String fact = "item:" + FOCUS
                + " -[quest_text]-> 系列含 " + PEER
                + " 与 " + FOCUS
                + " (source:ftbquests/quests/demo.snbt tier:C)";
        List<String> out = OfficialDisplay.enrichFacts(List.of(fact), FOCUS);
        assert !out.isEmpty() : out;
        String joined = String.join("\n", out);
        assert joined.contains("龙霆钢开胸器") : joined;
        assert joined.contains(PEER) : joined;
        assert joined.contains(OfficialDisplay.PEER_HEADER) : joined;
        assert !joined.contains("暗鋼閃電") && !joined.contains("暗钢闪电") : joined;
        assert !joined.contains("Dark Steel") : joined;
        // Focus id must stay whole — not mangled as item:stray_expansion（…）:path
        assert joined.contains(FOCUS) : joined;
        assert !joined.contains("item:stray_expansion（") : joined;
        assert !joined.contains("item:stray_expansion" + OfficialDisplay.NO_OFFICIAL) : joined;
        // source path must stay (not annotated as an item)
        assert joined.contains("source:ftbquests/") : joined;
        System.out.println("peerSectionAndNoInvented OK peers=" + out.size());
    }

    /** Fact line {@code item:ns:path} keeps prefix + annotates real id (no mangle). */
    static void mechanicFactItemPrefixNoMangle() {
        OfficialDisplay.lookup = id -> {
            String k = id == null ? "" : id.toLowerCase(Locale.ROOT);
            if (FOCUS.equals(k)) {
                return "命令开胸器";
            }
            if (PEER.equals(k)) {
                return "龙霆钢开胸器";
            }
            if ("golden_age:wu".equals(k)) {
                return "悟";
            }
            return "";
        };
        String line = "item:" + FOCUS + " -[quest_text]-> peer " + PEER;
        String out = OfficialDisplay.annotate(line);
        // Broken version printed: item:stray_expansion（無官方名）:chestopener_command …
        assert !out.contains("item:stray_expansion（") : out;
        assert !out.contains("item:stray_expansion" + OfficialDisplay.NO_OFFICIAL) : out;
        assert out.startsWith("item:") : out;
        assert out.contains(FOCUS) : out;
        assert out.contains("命令开胸器") : out;
        // id + official name form (labeled = name（id） after the item: prefix)
        assert out.contains("命令开胸器（" + FOCUS + "）") : out;
        System.out.println("mechanicFactItemPrefixNoMangle OK → " + out);
    }

    /** Genuine UI marker {@code {{item:ns:path}}} must still resolve (left intact). */
    static void itemMarkerUntouched() {
        OfficialDisplay.lookup = id -> {
            if ("golden_age:wu".equals(id == null ? "" : id.toLowerCase(Locale.ROOT))) {
                return "悟";
            }
            return "";
        };
        String marker = "{{item:golden_age:wu}}";
        String out = OfficialDisplay.annotate(marker);
        assert out.equals(marker) : "marker mangled: " + out;
        System.out.println("itemMarkerUntouched OK");
    }

    /** Prose containing the literal word {@code item:} is left untouched. */
    static void proseItemColonUntouched() {
        String prose = "If the line says item: without a registry id, leave it.";
        assert OfficialDisplay.annotate(prose).equals(prose) : OfficialDisplay.annotate(prose);
        String withFake = "Note item:foo is a fact label, not mod:bar alone here.";
        String annotated = OfficialDisplay.annotate(withFake);
        // item:foo must not become item:foo（無官方名） / labeled
        assert !annotated.contains("item:foo（") : annotated;
        assert annotated.contains("item:foo") : annotated;
        System.out.println("proseItemColonUntouched OK");
    }

    static void unresolvedKeepsId() {
        String lab = OfficialDisplay.labeled("mod:totally_unknown_item_xyz");
        assert lab.startsWith("mod:totally_unknown_item_xyz") : lab;
        assert lab.contains(OfficialDisplay.NO_OFFICIAL) : lab;
        assert !lab.contains("暗鋼") && !lab.contains("暗钢") : lab;
        System.out.println("unresolvedKeepsId OK");
    }

    static void promptRulePresent() {
        for (String code : List.of("zh_cn", "zh_tw", "en_us")) {
            String rule = ReplyLang.officialNameRule(code);
            assert rule != null && !rule.isBlank() : code;
            assert !rule.equals("packai.reply.official_name_rule") : "missing bundle " + code;
            assert rule.contains("chestopener_dsteellightning") : code + " " + rule;
            String low = rule.toLowerCase(Locale.ROOT);
            assert low.contains("official") || rule.contains("官方") : code;
            String fc = ReplyLang.factCheck(code, true);
            assert fc.contains(rule.trim()) || fc.contains("chestopener_dsteellightning") : code;
        }
        System.out.println("promptRulePresent OK");
    }

    /** Translation-key shaped hover strings must not count as official names. */
    static void translationKeyNotOfficial() {
        OfficialDisplay.lookup = id -> "item.chestcavity.cud";
        assert OfficialDisplay.officialName("chestcavity:cud").isEmpty()
                : OfficialDisplay.officialName("chestcavity:cud");
        assert OfficialDisplay.isTranslationKeyShaped("item.chestcavity.cud");
        assert !OfficialDisplay.isTranslationKeyShaped("鐵鎬");
        assert !OfficialDisplay.isTranslationKeyShaped("Iron Pickaxe");
        System.out.println("translationKeyNotOfficial OK");
    }

    /**
     * Plan α fixtures: junk script-path / ns tokens rejected; real items still annotate.
     * Scope = packai-injected fact body + peer line only (not model prose).
     */
    static void peerAndBodyJunkRejected() {
        OfficialDisplay.lookup = id -> {
            String k = id == null ? "" : id.toLowerCase(Locale.ROOT);
            if ("kubejs:colorful_candy".equals(k)) {
                return "彩虹糖果";
            }
            if ("eccentrictome:tome".equals(k)) {
                return "怪奇宝典";
            }
            if ("golden_age:infinity_sword".equals(k)) {
                return "寰宇支配之剑";
            }
            if ("ino_dlc_build:music.build_henshin".equals(k)) {
                return "变身音乐";
            }
            if ("mrqx_extra_pack:item/mystery_item".equals(k)) {
                return "神秘物品";
            }
            if ("ino_dlc_build:item/item/build_phone".equals(k)) {
                return "建造手机";
            }
            return "";
        };

        // A: script path must not become a peer; tome peer must remain.
        String factA = "item:怪奇宝典（eccentrictome:tome） -[use]-> 觸發:ItemEvents.tooltip"
                + " (source:kubejs/client_scripts/item_tooltips.js:2 tier:A)";
        List<String> outA = OfficialDisplay.enrichFacts(List.of(factA), FOCUS);
        assertPeerAndBodyClean(outA);
        String peerA = peerLineOf(outA);
        assert peerA != null : outA;
        assert peerA.contains("怪奇宝典（eccentrictome:tome）") : peerA;
        assert !peerA.contains("kubejs/client_scripts/item_tooltips.js:2") : peerA;

        // B: bare js:1 must not be annotated (raw token legitimately stays untouched); no NO_OFFICIAL.
        String factB = "消耗法力：14 active_charm.1 (js:1 A)";
        List<String> outB = OfficialDisplay.enrichFacts(List.of(factB), FOCUS);
        assertPeerAndBodyClean(outB);
        String joinedB = String.join("\n", outB);
        assert !joinedB.contains("js:1（") : joinedB;
        assert !joinedB.contains("js:1" + OfficialDisplay.NO_OFFICIAL) : joinedB;
        assert !joinedB.contains(OfficialDisplay.NO_OFFICIAL) : joinedB;
        String peerB = peerLineOf(outB);
        assert peerB == null || !peerB.contains("js:1") : peerB;

        // C: mechanic:none must not annotate.
        String factC = "item:彩虹糖果（kubejs:colorful_candy） note:mechanic:none";
        List<String> outC = OfficialDisplay.enrichFacts(List.of(factC), FOCUS);
        assertPeerAndBodyClean(outC);
        String joinedC = String.join("\n", outC);
        assert !joinedC.contains("mechanic:none（") : joinedC;
        assert !joinedC.contains("mechanic:none" + OfficialDisplay.NO_OFFICIAL) : joinedC;
        for (String s : outC) {
            Matcher pm = FULLWIDTH_PAREN.matcher(s);
            while (pm.find()) {
                assert !pm.group(1).contains("mechanic:") : s;
            }
        }

        // D: positive control — real items stay annotated.
        String factD = "效果:give:怪奇宝典（eccentrictome:tome）,give:彩虹糖果（kubejs:colorful_candy）";
        List<String> outD = OfficialDisplay.enrichFacts(List.of(factD), FOCUS);
        assertPeerAndBodyClean(outD);
        String joinedD = String.join("\n", outD);
        assert joinedD.contains("怪奇宝典（eccentrictome:tome）") : joinedD;
        assert joinedD.contains("彩虹糖果（kubejs:colorful_candy）") : joinedD;

        // E: unit-level count denylist (defence-in-depth; marker guard already skips {Count:1b).
        String factE = "x count:1b x";
        assert OfficialDisplay.annotate(factE).equals(factE) : OfficialDisplay.annotate(factE);

        // B5: every denied namespace left untouched by annotate.
        for (String ns : NS_DENY_LIST) {
            String raw = "x " + ns + ":foo_bar x";
            assert OfficialDisplay.annotate(raw).equals(raw) : ns + " → " + OfficialDisplay.annotate(raw);
        }

        // Multi-segment positives still annotate.
        for (String id : List.of(
                "ino_dlc_build:music.build_henshin",
                "mrqx_extra_pack:item/mystery_item",
                "ino_dlc_build:item/item/build_phone")) {
            String ann = OfficialDisplay.annotate("see " + id + " here");
            assert ann.contains("（" + id + "）") : ann;
            assert !ann.contains(OfficialDisplay.NO_OFFICIAL) : ann;
        }

        System.out.println("peerAndBodyJunkRejected OK");
    }

    private static String peerLineOf(List<String> out) {
        for (String s : out) {
            if (s == null) {
                continue;
            }
            int hi = s.indexOf(OfficialDisplay.PEER_HEADER);
            if (hi >= 0) {
                return s.substring(hi);
            }
        }
        return null;
    }

    /** Semantic peer/body junk asserts (never raw substring {@code js:}). */
    private static void assertPeerAndBodyClean(List<String> out) {
        String peerLine = peerLineOf(out);
        if (peerLine != null) {
            String rest = peerLine.substring(OfficialDisplay.PEER_HEADER.length());
            String[] parts = rest.split(";", -1);
            int entries = 0;
            for (String part : parts) {
                String entry = part.trim();
                if (entry.isEmpty()) {
                    continue;
                }
                entries++;
                assert !entry.contains(OfficialDisplay.NO_OFFICIAL) : peerLine;
                assert !entry.contains(".js") && !entry.contains(".json") && !entry.contains(".snbt")
                        : peerLine;
                Matcher pm = FULLWIDTH_PAREN.matcher(entry);
                while (pm.find()) {
                    String id = pm.group(1);
                    assertParenIdClean(id, peerLine);
                }
            }
            assert entries <= OfficialDisplay.PEER_CAP : peerLine + " entries=" + entries;
        }
        for (String fact : out) {
            if (fact == null) {
                continue;
            }
            int hi = fact.indexOf(OfficialDisplay.PEER_HEADER);
            String body = hi < 0 ? fact : fact.substring(0, hi);
            if (body.isBlank()) {
                continue;
            }
            assert !body.contains(OfficialDisplay.NO_OFFICIAL) : body;
            Matcher pm = FULLWIDTH_PAREN.matcher(body);
            while (pm.find()) {
                String id = pm.group(1);
                assertParenIdClean(id, body);
            }
        }
    }

    private static void assertParenIdClean(String id, String ctx) {
        assert !JS_BARE.matcher(id).find() : ctx;
        assert !id.contains(".js") && !id.contains(".json") && !id.contains(".snbt") : ctx;
        assert !id.equals("無官方名") && !id.contains(OfficialDisplay.NO_OFFICIAL) : ctx;
        assert !id.contains("mechanic:") : ctx;
        assert !COUNT_DIGITS.matcher(id).find() : ctx;
        assert !DENY_NS_IN_PAREN.matcher(id).find() : ctx;
        assert !OfficialDisplay.isTranslationKeyShaped(id) : ctx;
    }
}

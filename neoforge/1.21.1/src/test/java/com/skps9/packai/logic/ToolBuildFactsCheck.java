package com.skps9.packai.logic;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonParser;

/** Headless NBT → {@code [TOOL_BUILD]} (no Minecraft). Run with -ea. */
public final class ToolBuildFactsCheck {
    private ToolBuildFactsCheck() {}

    public static void main(String[] args) {
        hammer();
        wuSword();
        axe();
        scrollEmpty();
        unparsed();
        skipNoise();
        tetraUseReverse();
        System.out.println("ToolBuildFactsCheck OK");
    }

    private static void hammer() {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("double/basic_hammer_left_material", "basic_hammer/copper");
        s.put("double/basic_hammer_right_material", "basic_hammer/copper");
        s.put("double/basic_handle_material", "basic_handle/spruce");
        s.put("double/handle", "double/basic_handle");
        s.put("double/head_left", "double/basic_hammer_left");
        s.put("double/head_right", "double/basic_hammer_right");
        s.put("id", "d5bf3a60-0a52-4bbd-a10c-fc4ef0a3f98b");
        Map<String, Integer> n = new LinkedHashMap<>();
        n.put("Damage", 0);
        n.put("HideFlags", 1);
        n.put("double/head_left:workable", 1);
        n.put("double/head_right:workable", 1);
        n.put("honing_progress", 203);
        String out = ToolBuildFacts.format(ToolBuildFacts.parse(s, n));
        assert out.startsWith(ToolBuildFacts.HEADER) : out;
        assert out.contains("part double/head_left: double/basic_hammer_left material basic_hammer/copper")
                : out;
        assert out.contains("part double/handle: double/basic_handle material basic_handle/spruce") : out;
        assert out.contains("improvement double/head_left:workable 1") : out;
        assert !out.contains("d5bf3a60") : out;
        assert !out.contains("honing_progress") : out;
    }

    private static void wuSword() {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("sword/blade", "sword/wu");
        s.put("sword/wu_material", "wu");
        s.put("sword/hilt", "sword/wu_hilt");
        s.put("sword/wu_hilt_material", "wu_hilt");
        s.put("sword/fuller", "sword/reinforced_fuller");
        s.put("sword/reinforced_fuller_material", "reinforced_fuller/archotech_arcane_steel");
        s.put("sword/guard", "sword/sword_socket");
        s.put("sword/sword_socket_material", "sword_socket/thunder_gem1_socket");
        s.put("sword/pommel", "sword/forefinger_ring");
        s.put("sword/forefinger_ring_material", "forefinger_ring/archotech_arcane_steel");
        ToolBuildFacts.Scan parsed = ToolBuildFacts.parse(s, Map.of());
        java.util.ArrayList<ToolBuildFacts.Part> named = new java.util.ArrayList<>();
        for (ToolBuildFacts.Part p : parsed.parts()) {
            String item = "";
            if (p.materialId().contains("thunder_gem1_socket")) {
                item = "golden_age:thunder_gem1";
            } else if (p.materialId().contains("archotech_arcane_steel")) {
                item = "golden_age:archotech_arcane_steel";
            } else if ("wu".equals(p.materialId())) {
                item = "golden_age:wu";
            }
            named.add(p.withMeta("", item));
        }
        String out = ToolBuildFacts.format(new ToolBuildFacts.Scan(named, parsed.mods(), parsed.rawSource()));
        assert out.contains("socket sword/guard: sword/sword_socket material sword_socket/thunder_gem1_socket")
                : out;
        assert out.contains("item golden_age:thunder_gem1") : out;
        assert out.contains("item golden_age:archotech_arcane_steel") : out;
        assert out.contains("item golden_age:wu") : out;
        assert out.contains("part sword/blade: sword/wu material wu") : out;
        assert !out.contains("minecraft:dirt") : out;
        String gemJson = "{\"key\":\"thunder_gem1_socket\",\"material\":{\"items\":[\"golden_age:thunder_gem1\"]}}";
        assert "golden_age:thunder_gem1".equals(TetraMaterialItems.firstItemId(gemJson)) : gemJson;
        assert "thunder_gem1_socket".equals(TetraMaterialItems.jsonKey(gemJson));
        assert TetraMaterialItems.firstItemId("{\"key\":\"wu\"}").isEmpty();
        assert TetraMaterialItems.firstItemId(
                        "{\"key\":\"x\",\"material\":{\"tag\":\"forge:gems/diamond\"}}")
                .isEmpty();
        java.util.Map<String, String> schem = new java.util.LinkedHashMap<>();
        TetraMaterialItems.indexSchematicJson(
                "{\"outcomes\":[{\"material\":{\"items\":[\"golden_age:wu\"]},\"moduleKey\":\"sword/wu\",\"moduleVariant\":\"wu\"}]}",
                schem);
        assert "golden_age:wu".equals(schem.get("wu")) : schem;
        assert "golden_age:wu".equals(schem.get("sword/wu")) : schem;
        TetraMaterialItems.indexSchematicJson(
                "{\"outcomes\":[{\"moduleKey\":\"sword/wu_hilt\",\"moduleVariant\":\"wu_hilt\"}]}",
                schem);
        assert !schem.containsKey("wu_hilt") : schem;
    }

    private static void axe() {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("double/head_left", "double/basic_axe_left");
        s.put("double/basic_axe_left_material", "basic_axe/oak");
        s.put("double/head_right", "double/butt_right");
        s.put("double/butt_right_material", "butt/oak");
        s.put("double/handle", "double/basic_handle");
        s.put("double/basic_handle_material", "basic_handle/stick");
        Map<String, Integer> n = Map.of("double/head_left:hone/efficiency", 1);
        String out = ToolBuildFacts.format(ToolBuildFacts.parse(s, n));
        assert out.contains("material basic_axe/oak") : out;
        assert out.contains("material butt/oak") : out;
        assert out.contains("improvement double/head_left:hone/efficiency 1") : out;
    }

    private static void scrollEmpty() {
        Map<String, String> s = Map.of("id", "tetra:mirror");
        assert ToolBuildFacts.parse(s, Map.of()).isEmpty();
        assert !ToolBuildFacts.looksLikeTetraModularItem("tetra:scroll_rolled");
        assert ToolBuildFacts.looksLikeTetraModularItem("tetra:modular_double");
        assert !ToolBuildFacts.looksLikeTetraModularItem("minecraft:iron_ingot");
    }

    private static void unparsed() {
        assert ToolBuildFacts.unparsedBlock().contains(ToolBuildFacts.UNPARSED);
        assert ToolBuildFacts.format(new ToolBuildFacts.Scan(null, null, "")).isEmpty();
    }

    private static void skipNoise() {
        Map<String, Integer> n = new LinkedHashMap<>();
        n.put("double/head_left/settle_progress", 40);
        n.put("double/handle_tweak:foo", 2);
        Map<String, String> s = Map.of("double/head_left", "double/basic_hammer_left");
        String out = ToolBuildFacts.format(ToolBuildFacts.parse(s, n));
        assert out.contains("part double/head_left") : out;
        assert !out.contains("settle_progress") : out;
        assert !out.contains("_tweak") : out;
    }

    private static void tetraUseReverse() {
        java.util.Map<String, String> fwd = new LinkedHashMap<>();
        java.util.Map<String, java.util.List<TetraMaterialItems.Use>> rev = new LinkedHashMap<>();
        TetraMaterialItems.indexJson(
                "{\"key\":\"archotech_arcane_steel\",\"category\":\"metal\",\"material\":{\"items\":[\"golden_age:archotech_arcane_steel\"]}}",
                fwd,
                rev);
        TetraMaterialItems.indexJson(
                "{\"key\":\"thunder_gem1_socket\",\"category\":\"socket\",\"material\":{\"items\":[\"golden_age:thunder_gem1\"]}}",
                fwd,
                rev);
        TetraMaterialItems.indexJson("{\"key\":\"wu\"}", fwd, rev);
        TetraMaterialItems.indexSchematicJson(
                "{\"slots\":[\"sword/blade\"],\"outcomes\":[{\"material\":{\"items\":[\"golden_age:wu\"]},\"moduleKey\":\"sword/wu\",\"moduleVariant\":\"wu\"}]}",
                fwd,
                rev);
        TetraMaterialItems.indexSchematicJson(
                "{\"slots\":[\"sword/blade\"],\"outcomes\":[{\"moduleKey\":\"sword/wu_hilt\",\"moduleVariant\":\"wu_hilt\"}]}",
                fwd,
                rev);
        TetraMaterialItems.indexSchematicJson(
                "{\"slots\":[\"sword/blade\",\"sword/hilt\"],\"outcomes\":[{\"material\":{\"items\":[\"minecraft:gold_nugget\"]},\"improvements\":{\"hone_gild\":1}}]}",
                fwd,
                rev);
        String steel = TetraMaterialItems.formatUses(rev.get("golden_age:archotech_arcane_steel"));
        assert steel.startsWith(TetraMaterialItems.USE_HEADER) : steel;
        assert steel.contains("material key=archotech_arcane_steel category=metal") : steel;
        assert !steel.contains("sword/") : steel;
        String gem = TetraMaterialItems.formatUses(rev.get("golden_age:thunder_gem1"));
        assert gem.contains("socket key=thunder_gem1_socket") : gem;
        String wu = TetraMaterialItems.formatUses(rev.get("golden_age:wu"));
        assert wu.contains("module key=wu slots=sword/blade module=sword/wu") : wu;
        assert rev.get("wu_hilt") == null || rev.get("wu_hilt").isEmpty();
        String nugget = TetraMaterialItems.formatUses(rev.get("minecraft:gold_nugget"));
        assert nugget.contains("modifier") : nugget;
        assert nugget.contains("improvement=hone_gild") : nugget;
        assert nugget.contains("slots=sword/blade,sword/hilt") : nugget;
        assert TetraMaterialItems.formatUses(rev.get("minecraft:dirt")).isEmpty();
        String overflow = TetraMaterialItems.joinSlots(
                JsonParser.parseString(
                                "{\"slots\":[\"s/1\",\"s/2\",\"s/3\",\"s/4\",\"s/5\",\"s/6\",\"s/7\",\"s/8\",\"s/9\",\"s/10\",\"s/11\",\"s/12\",\"s/13\"]}")
                        .getAsJsonObject());
        assert overflow.equals("s/1,s/2,s/3,s/4,s/5,s/6,s/7,s/8,+5") : overflow;
    }
}

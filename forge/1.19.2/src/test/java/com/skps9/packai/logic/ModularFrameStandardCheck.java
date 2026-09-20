package com.skps9.packai.logic;

import java.util.List;
import java.util.Map;

/**
 * Plan F S4/S8/S10 + fix1 — ModularFrameStandard classifier harness ({@code java -ea}).
 * Fixtures mirror plan section 3 S4 member table (trace + tetra modular craft recipes).
 */
public final class ModularFrameStandardCheck {
    private ModularFrameStandardCheck() {}

    public static void main(String[] args) {
        List<Map<String, String>> expected = ModularFrameStandard.tetraBlankFramePartMaps();
        List<ModularFrameStandard.FrameRecipe> recipes = ModularFrameStandard.tetraBlankFrameRecipes();
        assert expected.size() == 4 : "tetra blank-frame unique maps";
        assert recipes.size() == 4 : "tetra blank-frame recipes";
        // Runtime installs defaults in static init — classifyInstalled must match classify(expected).
        String stone = ""
                + "[TOOL_BUILD]\n"
                + "part sword/hilt: sword/basic_hilt material basic_hilt/stick name x\n"
                + "part sword/blade: sword/stonecutter material stonecutter/stonecutter name y\n";
        assert ModularFrameStandard.classifyInstalled(stone) == ModularFrameStandard.Kind.STANDARD
                : "defaults installed";
        standardS4(expected, recipes);
        modifiedS4(expected);
        unknownS4(expected);
        stubDiscriminates(expected);
        s8Cost(expected);
        s10NoOverrideAcquire(expected);
        ensureVisibleHelper();
        fix1StandardRecipeInsert(recipes);
        fix3LateInsertSurvivesHowToGet(recipes);
        System.out.println("ModularFrameStandardCheck OK");
    }

    /** STANDARD 2: stonecutter traces (+ recipe index for fix1). */
    static void standardS4(
            List<Map<String, String>> expected,
            List<ModularFrameStandard.FrameRecipe> recipes
    ) {
        String stoneA = ""
                + "[TOOL_BUILD]\n"
                + "part sword/hilt: sword/basic_hilt material basic_hilt/stick name fragile\n"
                + "part sword/blade: sword/stonecutter material stonecutter/stonecutter name blade\n";
        String stoneB = ""
                + "[TOOL_BUILD]\n"
                + "part sword/blade: sword/stonecutter material stonecutter/stonecutter name blade\n"
                + "part sword/hilt: sword/basic_hilt material basic_hilt/stick name fragile\n";
        assert ModularFrameStandard.classify(stoneA, expected) == ModularFrameStandard.Kind.STANDARD
                : "132113/122439 STANDARD";
        assert ModularFrameStandard.classify(stoneB, expected) == ModularFrameStandard.Kind.STANDARD
                : "order-insensitive STANDARD";
        // fix1 / 19:24 held stonecutter sword → STANDARD + stonecutter recipe index 0
        ModularFrameStandard.Match m = ModularFrameStandard.classifyDetailed(stoneA, recipes);
        assert m.kind() == ModularFrameStandard.Kind.STANDARD : "detailed STANDARD";
        assert m.recipeIndex() != null && m.recipeIndex() == 0 : "stonecutter recipeIndex=" + m.recipeIndex();
        ModularFrameStandard.FrameRecipe r = recipes.get(m.recipeIndex());
        assert r.hasCraftLine() : "stonecutter craft line";
        assert "tetra:modular_sword".equals(r.resultItemId()) : r.resultItemId();
        assert r.ingredientItemIds().contains("tetra:stonecutter") : r.ingredientItemIds();
        assert r.ingredientItemIds().contains("minecraft:stick") : r.ingredientItemIds();
        System.out.println("standardS4 OK");
    }

    /** MODIFIED 11 samples (representative + void-scythe pack tool). */
    static void modifiedS4(List<Map<String, String>> expected) {
        String[] samples = {
                // ask-20260917-122100 archotech void scythe
                "[TOOL_BUILD]\n"
                        + "part single/handle: single/archotech_void_scythe_handle material archotech_void_scythe_handle name handle\n"
                        + "part single/head: single/archotech_void_scythe material archotech_void_scythe item golden_age:archotech_void_scythe\n"
                        + "improvement single/head:ultimate_stability 1\n",
                // flesh blade pack tool
                "[TOOL_BUILD]\n"
                        + "part sword/fuller: sword/reinforced_fuller material reinforced_fuller/archotech_arcane_steel\n"
                        + "part sword/pommel: sword/counterweight\n"
                        + "part sword/guard: sword/socket\n"
                        + "part sword/hilt: sword/wu_hilt\n"
                        + "part sword/blade: sword/flesh_blade\n",
                "[TOOL_BUILD]\n"
                        + "part sword/pommel: sword/infested_gem\n"
                        + "part sword/fuller: sword/wash\n"
                        + "part sword/guard: sword/infested_heart\n"
                        + "part sword/blade: sword/infested_blade\n"
                        + "part sword/hilt: sword/infested_hilt\n",
                "[TOOL_BUILD]\n"
                        + "part sword/hilt: sword/wu_hilt\n"
                        + "part sword/blade: sword/wu\n",
                "[TOOL_BUILD]\n"
                        + "part single/binding: single/soul_ruby\n"
                        + "part single/handle: single/archotech_void_scythe_handle\n"
                        + "part single/head: single/archotech_void_scythe\n",
                "[TOOL_BUILD]\n"
                        + "socket single/binding: single/socket material single_socket/socket_nether_star\n"
                        + "part single/handle: single/basic_handle\n"
                        + "part single/head: single/philosophers_mace\n",
                // 5 more flesh / wu variants to fill 11
                "[TOOL_BUILD]\npart sword/hilt: sword/wu_hilt\npart sword/blade: sword/flesh_blade\npart sword/guard: sword/socket\n",
                "[TOOL_BUILD]\npart sword/hilt: sword/basic_hilt\npart sword/blade: sword/flesh_blade\n",
                "[TOOL_BUILD]\npart single/handle: single/basic_handle\npart single/head: single/archotech_void_scythe\n",
                "[TOOL_BUILD]\npart sword/hilt: sword/infested_hilt\npart sword/blade: sword/stonecutter\n",
                "[TOOL_BUILD]\npart toolbelt/belt: toolbelt/belt\npart toolbelt/slot1: toolbelt/pouch_slot1\n",
        };
        assert samples.length == 11 : "want 11 modified fixtures";
        for (int i = 0; i < samples.length; i++) {
            ModularFrameStandard.Kind k = ModularFrameStandard.classify(samples[i], expected);
            assert k == ModularFrameStandard.Kind.MODIFIED : "modified i=" + i + " got " + k;
        }
        System.out.println("modifiedS4 OK n=" + samples.length);
    }

    /** UNKNOWN: unparsed + empty expected + blank. */
    static void unknownS4(List<Map<String, String>> expected) {
        String unparsed = ToolBuildFacts.unparsedBlock();
        assert ModularFrameStandard.classify(unparsed, expected) == ModularFrameStandard.Kind.UNKNOWN
                : "131542 unparsed";
        assert ModularFrameStandard.classify("", expected) == ModularFrameStandard.Kind.UNKNOWN : "blank";
        assert ModularFrameStandard.classify(null, expected) == ModularFrameStandard.Kind.UNKNOWN : "null";
        // no tool_build → cannot be STANDARD
        assert ModularFrameStandard.classify("just text", expected) == ModularFrameStandard.Kind.UNKNOWN
                : "no parts";
        // empty expected → UNKNOWN (fail-open)
        assert ModularFrameStandard.classify(
                "[TOOL_BUILD]\npart sword/hilt: sword/basic_hilt\npart sword/blade: sword/stonecutter\n",
                List.of()) == ModularFrameStandard.Kind.UNKNOWN
                : "empty expected";
        System.out.println("unknownS4 OK");
    }

    /** Red→green: always-STANDARD stub must fail modified assert (discriminating harness). */
    static void stubDiscriminates(List<Map<String, String>> expected) {
        String modified = ""
                + "[TOOL_BUILD]\n"
                + "part single/handle: single/archotech_void_scythe_handle\n"
                + "part single/head: single/archotech_void_scythe\n";
        ModularFrameStandard.Kind stub = ModularFrameStandard.Kind.STANDARD; // deliberate wrong
        assert stub != ModularFrameStandard.classify(modified, expected)
                : "stub would false-green if harness only checked STANDARD";
        assert ModularFrameStandard.classify(modified, expected) == ModularFrameStandard.Kind.MODIFIED;
        System.out.println("stubDiscriminates OK");
    }

    /** S8: 1000 classify calls ≤ 50ms. */
    static void s8Cost(List<Map<String, String>> expected) {
        String text = ""
                + "[TOOL_BUILD]\n"
                + "part sword/hilt: sword/basic_hilt\n"
                + "part sword/blade: sword/stonecutter\n";
        // warmup
        for (int i = 0; i < 100; i++) {
            ModularFrameStandard.classify(text, expected);
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            ModularFrameStandard.classify(text, expected);
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assert ms <= 50 : "S8 cost ms=" + ms;
        System.out.println("s8Cost OK ms=" + ms);
    }

    /**
     * S10: MODIFIED + non-empty acquire → do not force miss line
     * (AskEngine gates force=false when acquire non-empty; helper itself only inserts when force).
     */
    static void s10NoOverrideAcquire(List<Map<String, String>> expected) {
        String modified = ""
                + "[TOOL_BUILD]\n"
                + "part single/handle: single/archotech_void_scythe_handle\n"
                + "part single/head: single/archotech_void_scythe\n";
        assert ModularFrameStandard.classify(modified, expected) == ModularFrameStandard.Kind.MODIFIED;
        String body = "obtain: script drop demo.";
        String acquireFact = "KubeJS give: demo";
        boolean force = ModularFrameStandard.classify(modified, expected) == ModularFrameStandard.Kind.MODIFIED
                && acquireFact.isBlank(); // non-empty → false
        String out = HonestMiss.ensureAskMissAcquirePlayerVisible(body, "zh_cn", force);
        assert out.equals(body) : "must not inject miss over acquire facts";
        assert !out.contains(ReplyLang.askMissAcquirePlayer("zh_cn")) || body.contains(ReplyLang.askMissAcquirePlayer("zh_cn"));
        System.out.println("s10NoOverrideAcquire OK");
    }

    static void ensureVisibleHelper() {
        String miss = ReplyLang.askMissAcquirePlayer("zh_cn");
        assert miss != null && !miss.isBlank() : "lang miss line";
        String src = ReplyLang.sourceHeader("zh_cn");
        String body = "customized tool.\n\n" + src + "local";
        String out = HonestMiss.ensureAskMissAcquirePlayerVisible(body, "zh_cn", true);
        assert out.contains(miss) : out;
        assert out.contains(src) : out;
        // idempotent
        assert HonestMiss.ensureAskMissAcquirePlayerVisible(out, "zh_cn", true).equals(out);
        assert HonestMiss.ensureAskMissAcquirePlayerVisible(body, "zh_cn", false).equals(body);
        System.out.println("ensureVisibleHelper OK");
    }

    /**
     * Fix1: 19:24 STANDARD stonecutter sword — stub body without craft line must gain
     * {@code packai.reply.frame_standard_recipe} after ensureFrameStandardRecipeVisible.
     */
    static void fix1StandardRecipeInsert(List<ModularFrameStandard.FrameRecipe> recipes) {
        String stone = ""
                + "[TOOL_BUILD]\n"
                + "part sword/hilt: sword/basic_hilt material basic_hilt/stick name fragile\n"
                + "part sword/blade: sword/stonecutter material stonecutter/stonecutter name blade\n";
        ModularFrameStandard.Match m = ModularFrameStandard.classifyDetailed(stone, recipes);
        assert m.kind() == ModularFrameStandard.Kind.STANDARD;
        assert m.recipeIndex() != null && m.recipeIndex() == 0;
        ModularFrameStandard.FrameRecipe recipe = recipes.get(m.recipeIndex());
        String src = ReplyLang.sourceHeader("en_us");
        // Model wrongly called it customized / not-indexed — craft names absent.
        String stub = "This customized version is not indexed.\n\n" + src + "local";
        String out = HonestMiss.ensureFrameStandardRecipeVisible(stub, "en_us", recipe);
        String line = ReplyLang.tr(
                "en_us",
                "packai.reply.frame_standard_recipe",
                HonestMiss.joinItemLabels(recipe.ingredientItemIds()),
                HonestMiss.itemLabel(recipe.resultItemId()));
        assert line != null && !line.isBlank() && !line.equals("packai.reply.frame_standard_recipe")
                : "lang frame_standard_recipe";
        assert out.contains(line) : out;
        assert out.contains(src) : out;
        // idempotent when core names already present
        assert HonestMiss.ensureFrameStandardRecipeVisible(out, "en_us", recipe).equals(out);
        // MODIFIED must not use this helper path (AskEngine gates); helper with null recipe no-op
        assert HonestMiss.ensureFrameStandardRecipeVisible(stub, "en_us", null).equals(stub);
        System.out.println("fix1StandardRecipeInsert OK");
    }

    /**
     * Fix3: craft line must survive after ensureHowToGetBody (obtain-unknown rewrite).
     * Simulates post-LLM order: early insert → howToGet may wipe → late insert restores.
     * ASCII-only literals (S11 harness gate).
     */
    static void fix3LateInsertSurvivesHowToGet(List<ModularFrameStandard.FrameRecipe> recipes) {
        ModularFrameStandard.FrameRecipe recipe = recipes.get(0);
        String unknown = ReplyLang.obtainUnknown("en_us");
        String src = ReplyLang.sourceHeader("en_us");
        // Body already has How-to-get filled with obtain-unknown (model / early howToGet path).
        String body = "How to get:\n" + unknown + "\n\n" + src + "local";
        String early = HonestMiss.ensureFrameStandardRecipeVisible(body, "en_us", recipe);
        String line = ReplyLang.tr(
                "en_us",
                "packai.reply.frame_standard_recipe",
                HonestMiss.joinItemLabels(recipe.ingredientItemIds()),
                HonestMiss.itemLabel(recipe.resultItemId()));
        assert line != null && !line.isBlank() && !line.equals("packai.reply.frame_standard_recipe");
        assert early.contains(line) : early;
        // Same call AskEngine used to run AFTER early insert — can drop craft names from how-to-get block.
        String afterHow = AskReplyScrub.ensureHowToGetBody(early, "", false, unknown);
        // Late insert (fix3 order): must leave craft line present.
        String late = HonestMiss.ensureFrameStandardRecipeVisible(afterHow, "en_us", recipe);
        assert late.contains(line) : late;
        assert HonestMiss.bodyContainsAllLabels(
                late, recipe.ingredientItemIds(), recipe.resultItemId())
                : late;
        // Idempotent final check
        assert HonestMiss.ensureFrameStandardRecipeVisible(late, "en_us", recipe).equals(late);
        System.out.println("fix3LateInsertSurvivesHowToGet OK");
    }
}

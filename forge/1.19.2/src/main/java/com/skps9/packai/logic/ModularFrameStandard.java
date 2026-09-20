package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plan F stage-1: classify a held Tetra modular {@code [TOOL_BUILD]} as a blank-frame
 * craft recipe (STANDARD) vs a customized pack tool (MODIFIED).
 *
 * <p>Pure memory — no jar / index / network I/O. Expected standard part-maps must be
 * injected by the caller ({@link #installExpectedPartMaps} / {@link #installExpectedRecipes}
 * or per-call argument). Empty expected → {@link Kind#UNKNOWN} (fail-open = treat like
 * today's behavior).
 */
public final class ModularFrameStandard {
    public enum Kind {
        STANDARD,
        MODIFIED,
        UNKNOWN
    }

    /**
     * One blank-frame craft recipe: part set + static ingredient / result item ids
     * (from tetra jar {@code data/tetra/recipes/*.json}; I/O-free at runtime).
     */
    public record FrameRecipe(
            Map<String, String> parts,
            List<String> ingredientItemIds,
            String resultItemId,
            boolean shapeless,
            int variantCount
    ) {
        public FrameRecipe {
            parts = parts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(parts));
            ingredientItemIds = ingredientItemIds == null
                    ? List.of()
                    : List.copyOf(ingredientItemIds);
            resultItemId = resultItemId == null ? "" : resultItemId.trim();
            if (variantCount < 1) {
                variantCount = 1;
            }
        }

        /** Convenience: shaped, single variant (part-map-only / legacy callers). */
        public FrameRecipe(
                Map<String, String> parts,
                List<String> ingredientItemIds,
                String resultItemId
        ) {
            this(parts, ingredientItemIds, resultItemId, false, 1);
        }

        /** True when a player-facing craft line can be built. */
        public boolean hasCraftLine() {
            return !resultItemId.isBlank() && !ingredientItemIds.isEmpty();
        }
    }

    /**
     * Classifier result. {@code recipeIndex} is non-null only for {@link Kind#STANDARD}
     * (index into the expected recipe list used for that call).
     */
    public record Match(Kind kind, Integer recipeIndex) {
        public static Match of(Kind kind) {
            return new Match(kind, null);
        }

        public static Match standard(int recipeIndex) {
            return new Match(Kind.STANDARD, recipeIndex);
        }
    }

    private static final Pattern PART_LINE = Pattern.compile(
            "^(?:part|socket)\\s+(\\S+):\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

    /** Caller-installed expected blank-frame recipes (immutable snapshot). */
    private static final AtomicReference<List<FrameRecipe>> INSTALLED =
            new AtomicReference<>(List.of());

    /**
     * Tetra jar blank-frame craft recipes (unique part sets; materials excluded).
     * 13 modular result recipes → 4 unique part maps (10 hammers share one).
     * Ingredient lists = representative jar recipe (oak hammer; rods tag → stick).
     * ponytail: hardcoded — classifier stays I/O-free; packs without Tetra never match.
     */
    private static final List<FrameRecipe> TETRA_BLANK_FRAMES = List.of(
            new FrameRecipe(
                    Map.of(
                            "sword/blade", "sword/stonecutter",
                            "sword/hilt", "sword/basic_hilt"),
                    List.of("tetra:stonecutter", "minecraft:stick"),
                    "tetra:modular_sword",
                    true,
                    1),
            new FrameRecipe(
                    Map.of(
                            "single/head", "single/earthpiercer",
                            "single/handle", "single/basic_handle"),
                    List.of("tetra:earthpiercer", "minecraft:stick"),
                    "tetra:modular_single",
                    false,
                    1),
            new FrameRecipe(
                    Map.of(
                            "double/handle", "double/basic_handle",
                            "double/head_left", "double/basic_hammer_left",
                            "double/head_right", "double/basic_hammer_right"),
                    List.of("minecraft:oak_planks", "minecraft:stick"),
                    "tetra:modular_double",
                    false,
                    10),
            new FrameRecipe(
                    Map.of(
                            "toolbelt/belt", "toolbelt/belt",
                            "toolbelt/slot1", "toolbelt/strap_slot1"),
                    List.of("minecraft:string"),
                    "tetra:modular_toolbelt",
                    false,
                    1));

    static {
        installExpectedRecipes(TETRA_BLANK_FRAMES);
    }

    private ModularFrameStandard() {}

    /** Immutable snapshot of built-in Tetra blank-frame part maps (for harness). */
    public static List<Map<String, String>> tetraBlankFramePartMaps() {
        List<Map<String, String>> out = new ArrayList<>(TETRA_BLANK_FRAMES.size());
        for (FrameRecipe r : TETRA_BLANK_FRAMES) {
            out.add(r.parts());
        }
        return List.copyOf(out);
    }

    /** Immutable snapshot of built-in Tetra blank-frame recipes (for harness). */
    public static List<FrameRecipe> tetraBlankFrameRecipes() {
        return TETRA_BLANK_FRAMES;
    }

    /**
     * Replace the process-wide expected standard-frame recipes.
     * Null / empty → clears (classify returns {@link Kind#UNKNOWN} until reinstalled).
     */
    public static void installExpectedRecipes(Collection<FrameRecipe> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            INSTALLED.set(List.of());
            return;
        }
        ArrayList<FrameRecipe> copy = new ArrayList<>();
        for (FrameRecipe r : recipes) {
            if (r == null) {
                continue;
            }
            Map<String, String> norm = normalizePartMap(r.parts());
            if (norm.isEmpty()) {
                continue;
            }
            // Must pass-through shapeless / variantCount — runtime reads INSTALLED via recipeAt.
            copy.add(new FrameRecipe(
                    norm, r.ingredientItemIds(), r.resultItemId(), r.shapeless(), r.variantCount()));
        }
        INSTALLED.set(List.copyOf(copy));
    }

    /**
     * Replace expected part-maps only (no craft-line metadata). Prefer
     * {@link #installExpectedRecipes} when ingredient / result ids are known.
     * Part-map-only path: no craft metadata → keep 3-arg ctor ({@code hasCraftLine()}=false).
     */
    public static void installExpectedPartMaps(Collection<? extends Map<String, String>> maps) {
        if (maps == null || maps.isEmpty()) {
            INSTALLED.set(List.of());
            return;
        }
        ArrayList<FrameRecipe> copy = new ArrayList<>();
        for (Map<String, String> m : maps) {
            Map<String, String> norm = normalizePartMap(m);
            if (!norm.isEmpty()) {
                // part-map-only: no craft metadata, hasCraftLine()=false
                copy.add(new FrameRecipe(norm, List.of(), ""));
            }
        }
        INSTALLED.set(List.copyOf(copy));
    }

    /** Snapshot of installed expected recipes (never null). */
    public static List<FrameRecipe> installedExpectedRecipes() {
        return INSTALLED.get();
    }

    /** Snapshot of installed expected part maps (never null). */
    public static List<Map<String, String>> installedExpectedPartMaps() {
        List<FrameRecipe> recipes = INSTALLED.get();
        List<Map<String, String>> out = new ArrayList<>(recipes.size());
        for (FrameRecipe r : recipes) {
            out.add(r.parts());
        }
        return List.copyOf(out);
    }

    /** Installed recipe at index, or null. */
    public static FrameRecipe recipeAt(int index) {
        List<FrameRecipe> recipes = INSTALLED.get();
        if (index < 0 || index >= recipes.size()) {
            return null;
        }
        return recipes.get(index);
    }

    /** Classify using {@link #installedExpectedRecipes()}. */
    public static Kind classifyInstalled(String toolBuildText) {
        return classifyDetailedInstalled(toolBuildText).kind();
    }

    /** Classify + matched recipe index using installed recipes. */
    public static Match classifyDetailedInstalled(String toolBuildText) {
        return classifyDetailed(toolBuildText, installedExpectedRecipes());
    }

    /**
     * @param toolBuildText {@link ToolBuildFacts#format} output (or unparsed block)
     * @param expectedStandardPartMaps each map = {@code slot → module} for one blank-frame recipe
     */
    public static Kind classify(
            String toolBuildText,
            Collection<? extends Map<String, String>> expectedStandardPartMaps
    ) {
        if (expectedStandardPartMaps == null || expectedStandardPartMaps.isEmpty()) {
            return Kind.UNKNOWN;
        }
        ArrayList<FrameRecipe> recipes = new ArrayList<>();
        for (Map<String, String> m : expectedStandardPartMaps) {
            Map<String, String> norm = normalizePartMap(m);
            if (!norm.isEmpty()) {
                // part-map-only path: no craft metadata, hasCraftLine()=false
                recipes.add(new FrameRecipe(norm, List.of(), ""));
            }
        }
        return classifyDetailed(toolBuildText, recipes).kind();
    }

    /**
     * Classify with full recipe metadata. {@link Match#recipeIndex()} indexes
     * {@code expectedRecipes} when kind is {@link Kind#STANDARD}.
     */
    public static Match classifyDetailed(
            String toolBuildText,
            List<FrameRecipe> expectedRecipes
    ) {
        try {
            if (toolBuildText == null || toolBuildText.isBlank()) {
                return Match.of(Kind.UNKNOWN);
            }
            String text = toolBuildText.trim();
            if (text.contains(ToolBuildFacts.UNPARSED)) {
                return Match.of(Kind.UNKNOWN);
            }
            Map<String, String> held = partsFromToolBuildText(text);
            if (held.isEmpty()) {
                return Match.of(Kind.UNKNOWN);
            }
            if (expectedRecipes == null || expectedRecipes.isEmpty()) {
                return Match.of(Kind.UNKNOWN);
            }
            for (int i = 0; i < expectedRecipes.size(); i++) {
                FrameRecipe recipe = expectedRecipes.get(i);
                if (recipe == null) {
                    continue;
                }
                Map<String, String> norm = normalizePartMap(recipe.parts());
                if (!norm.isEmpty() && norm.equals(held)) {
                    return Match.standard(i);
                }
            }
            return Match.of(Kind.MODIFIED);
        } catch (RuntimeException e) {
            return Match.of(Kind.UNKNOWN);
        }
    }

    /**
     * Parse {@code part}/{@code socket} lines → {@code slot → module}.
     * Skips materials / names / items / improvements (not part of the equality set).
     */
    public static Map<String, String> partsFromToolBuildText(String toolBuildText) {
        Map<String, String> out = new LinkedHashMap<>();
        if (toolBuildText == null || toolBuildText.isBlank()) {
            return out;
        }
        for (String raw : toolBuildText.split("\n", -1)) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty() || line.equalsIgnoreCase(ToolBuildFacts.HEADER)) {
                continue;
            }
            Matcher m = PART_LINE.matcher(line);
            if (!m.find()) {
                continue;
            }
            String slot = m.group(1).trim();
            String module = m.group(2).trim();
            if (slot.isEmpty() || module.isEmpty()) {
                continue;
            }
            if ("id".equalsIgnoreCase(slot) || ToolBuildFacts.isMaterialKey(slot)) {
                continue;
            }
            out.put(slot, module);
        }
        return out;
    }

    /**
     * Flat NBT string map → part set (same rules as plan §1: drop {@code id} uuid + {@code *_material}).
     */
    public static Map<String, String> partsFromFlatStrings(Map<String, String> strings) {
        Map<String, String> out = new LinkedHashMap<>();
        if (strings == null || strings.isEmpty()) {
            return out;
        }
        for (Map.Entry<String, String> e : strings.entrySet()) {
            String key = e.getKey();
            String val = e.getValue();
            if (key == null || val == null || val.isBlank()) {
                continue;
            }
            String k = key.trim();
            String v = val.trim();
            if (ToolBuildFacts.skipKey(k) || ToolBuildFacts.looksLikeUuid(v)) {
                continue;
            }
            if (ToolBuildFacts.isMaterialKey(k)) {
                continue;
            }
            if (ToolBuildFacts.isSlotKey(k) && ToolBuildFacts.looksLikeModuleId(v)) {
                out.put(k, v);
            }
        }
        return out;
    }

    static Map<String, String> normalizePartMap(Map<String, String> raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (Map.Entry<String, String> e : raw.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String k = e.getKey().trim();
            String v = e.getValue().trim();
            if (k.isEmpty() || v.isEmpty()) {
                continue;
            }
            if ("id".equalsIgnoreCase(k) || k.toLowerCase(Locale.ROOT).endsWith("_material")) {
                continue;
            }
            out.put(k, v);
        }
        return out;
    }
}
